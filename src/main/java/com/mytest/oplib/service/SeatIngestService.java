package com.mytest.oplib.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mytest.oplib.dao.SeatCurrentRoomMapper;
import com.mytest.oplib.dao.SeatRawItemMapper;
import com.mytest.oplib.dto.CurrentRoomKey;
import com.mytest.oplib.dto.SeatRawUpsertCmd;
import com.mytest.oplib.dto.SeatRealtimeResponse;
import com.mytest.oplib.util.OpenApiResultValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 1) 아이템 정규화 한 곳으로 모으기
 *      - processPageItems() 안에 흩어진 널/빈/보강/시간포맷/직렬화를 정규화 함수 1개로 모은다
 *      = 루프가 저장/머터리얼라이즈 2줄로 정리됨
 * 2) 페이지 종료/헤더 검증 가드 정리
 *      - isLastPage 경계값 보정, numOfRows <= 0 방어, MAX_PAGES 상한 등 안전장치 추가
 *      = 무한 르프/이상 응답에 대한 운영 안정성 확보
 * 3) 정책 스위치 도입(Strict(키 엄격)/Lenient(키 관대) 모드)
 *     - Strict: 실키 없으면 스킵, Lenient: RAW만 synthKey 허용, CURRENT는 실키만
 *     = 프로젝트 성격에 맞게 일관된 정책 적용
 * 4) 작은 역할 외부화(클래스 분리)
 *    - SeatItemNormalizer(정규화) -> SeatCurrentMaterializer(머터리얼라이즈) -> OpenApiPageInspector(페이지 종료 판단)
 *    - -> SeatRawWriter(RAW 배치)
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatIngestService {

    private final SeatOpenApiClient client;          // fetch(String,String,Integer,Integer) → String
    private final SeatRawItemMapper rawMapper;       // RAW 업서트
    private final SeatCurrentRoomMapper currentMapper; // CURRENT 업서트/머터리얼라이즈
    private final ObjectMapper objectMapper;

    /**
     * 스케줄러가 부르는 단일 타깃 진입점.
     * - 특정타깃(pblibId/stdgCd/rdrmId)에 대해 1페이지부터 끝 페이지까지 반복 수집
     * = "언제 멈출지/어떻게 페이지를 진전시킬지 등"같은 흐름(오케스트레이션)을 한곳에서 관리
     * = client.fetch(): 원본 JSON 수신
     * = parseOrThrow(): JSON → DTO 역직렬화 파싱
     * = OpenApiResultValidator.validate(): 헤더 코드 검증
     * = 아이템 유무 확인(없으면 그대로 종료)
     * = 페이지 처리: processPageItems()에서 RAW 적재 + CURRENT 머터리얼라이즈 반영
     * = isLastPage(): 마지막 페이지 판단 -> 종료 or 다음 페이지
     */
    @Transactional
    public void ingestOneTarget(String pblibId, String stdgCd, String rdrmId, int numOfRows) {
        int pageNo = 1;
        int totalRaw = 0;
        int totalCur = 0;

        while (true) {
            String rawJson = client.fetch(pblibId, rdrmId, Integer.valueOf(pageNo), Integer.valueOf(numOfRows));
            if (rawJson == null || rawJson.isBlank()) {
                log.info("[INGEST] 빈 응답 → 종료 (pblibId={}, stdgCd={}, rdrmId={}, pageNo={})", pblibId, stdgCd, rdrmId, pageNo);
                break;
            }

            SeatRealtimeResponse resp = parseOrThrow(rawJson);

            // 헤더 검증 (K03 '데이터 없음'은 예외 던지지 않고 빈 처리)
            String code = resp.header() != null ? nz(resp.header().resultCode(), "") : "";
            String msg  = resp.header() != null ? nz(resp.header().resultMsg(), "")  : "";
            try {
                OpenApiResultValidator.validate(code, msg);
            } catch (Exception ex) {
                log.warn("[INGEST] 헤더 검증 실패 → 종료 (code={}, msg={}, pageNo={})", code, msg, pageNo);
                break;
            }

            // 아이템 없으면 종료
            if (resp.body() == null || resp.body().items() == null || resp.body().items().isEmpty()) {
                log.info("[INGEST] 아이템 없음 → 종료 (pageNo={})", pageNo);
                break;
            }

            // 페이지 처리
            PageResult r = processPageItems(resp, stdgCd, pblibId, rdrmId);
            totalRaw += r.rawInserted();
            totalCur += r.currentUpserted();

            // 마지막 페이지 판단
            if (isLastPage(resp, pageNo, numOfRows)) {
                break;
            }
            pageNo++;
        }

        log.info("[INGEST] done target: pblibId={}, stdgCd={}, rdrmId={}, rawInserted={}, currentUpserted={}",
                pblibId, stdgCd, rdrmId, totalRaw, totalCur);
    }

    /**
     * 한 페이지의 아이템들을 돌며, 각 아이템을 정규화하고 저장
     * - 한 페이지 안에서 일관된 규칙(필터/정규화/저장/머터리얼라이즈)을 모아두면, 상위는 페이지 반복만 신경씀
     * = rdrmIdFilter가 지정되면 해당 아이템만 처리
     * = stdgCd는 "아이템 값 우선", 없으면 타깃 값으로 보강
     * = 키 결정: 원본 pblibId/rdrmId가 있으면 사용, 없으면 (옵션) synthKey()로 대체키 생성(=RAW 저장용)
     * = totDt 정규화: 14자리로 맞춤
     * = RAW에 아이템 단위 JSON을 업서트(페이지 JSON 전체가 아니라 개별 아이템 상태 보존)
     * = CURRENT 머터리얼라이즈는 "진짜 키"가 있을 때만 수행 + stdgCd도 item/보강 값으로
     *      - RAW는 감사/재처리 목적으로 최대한 남기고, 조회용 CURRENT는 신뢰 가능한 키만 반영해 품질 보장
     */
    private PageResult processPageItems(SeatRealtimeResponse resp,
                                        String stdgCdFromTarget, String pblibIdFromTarget, String rdrmIdFilter) {
        int rawInserted = 0;
        int curUpserted = 0;

        for (SeatRealtimeResponse.Item item : resp.body().items()) {

            // 요청 rdrmId가 지정되면 해당 아이템만 처리 (안전 필터)
            if (StringUtils.hasText(rdrmIdFilter) && !rdrmIdFilter.equals(item.rdrmId())) {
                continue;
            }

            // CHANGED: stdgCd는 "아이템 값 우선", 없으면 타깃 stdgCd로 보강
            String stdgForSave = StringUtils.hasText(item.stdgCd()) ? item.stdgCd() : stdgCdFromTarget;

            // 키 보정(아이디가 비면 대체키 생성)
            String keyPblibId = StringUtils.hasText(item.pblibId())
                    ? item.pblibId()
                    : synthKey(item.pblibNm(), item.lclgvNm());
            String keyRdrmId  = StringUtils.hasText(item.rdrmId())
                    ? item.rdrmId()
                    : synthKey(item.rdrmNm());

            if (!StringUtils.hasText(keyPblibId) || !StringUtils.hasText(keyRdrmId)) {
                log.warn("[INGEST] 키 부족 → 스킵 (pblibId={}, rdrmId={})", keyPblibId, keyRdrmId);
                continue;
            }

            String totDt14 = normalizeTotDt(item.totDt());

            // CHANGED: RAW 저장은 "아이템 JSON"으로 (페이지 전체 rawJson 아님)
            String itemJson;
            try {
                itemJson = objectMapper.writeValueAsString(item);
            } catch (Exception e) {
                log.warn("[INGEST] item 직렬화 실패 → 스킵 (pblibId={}, rdrmId={}, totDt={})", keyPblibId, keyRdrmId, totDt14, e);
                continue;
            }

            // RAW 업서트
            rawInserted += upsertRaw(stdgForSave, keyPblibId, keyRdrmId, totDt14, itemJson);

            // CHANGED: CURRENT 머터리얼라이즈는 "진짜 키"가 있을 때만 + stdgCd도 item/보강 값으로
            if (StringUtils.hasText(item.pblibId()) && StringUtils.hasText(item.rdrmId())) {
                curUpserted += currentMapper.materializeLatestByKey(
                        new CurrentRoomKey(stdgForSave, item.pblibId(), item.rdrmId())
                );
            }
        }
        return new PageResult(rawInserted, curUpserted);
    }

    // RAW 업서트
    private int upsertRaw(String stdgCd, String pblibId, String rdrmId, String totDt14, String payloadJson) {
        try {
            SeatRawUpsertCmd cmd = new SeatRawUpsertCmd(
                    "rlt_rdrm_info", stdgCd, pblibId, rdrmId, totDt14, payloadJson
            );
            return rawMapper.upsertRaw(cmd);
        } catch (DataAccessException dae) {
            log.warn("[RAW] upsert 실패 (stdgCd={}, pblibId={}, rdrmId={}, totDt={})",
                    stdgCd, pblibId, rdrmId, totDt14, dae);
            return 0;
        }
    }

    // 마지막 페이지 판단 (totalCount 우선, 없으면 휴리스틱)

    /**
     * totalCount가 있으면 공식 계산, 없으면 휴리스틱(이번 페이지 item 수 < 요청한 rows 수)로 종료 판단
     * - 외부 API가 항상 totalCount를 주지 않거나 부정확한 경우 때문에 안전한 종료를 위한 방어 로직 필요
     */
    private boolean isLastPage(SeatRealtimeResponse resp, int pageNo, int numOfRows) {
        int totalCount = safeInt(resp.body() != null ? resp.body().totalCount() : null);
        if (totalCount > 0) {
            int last = (totalCount + numOfRows - 1) / numOfRows;
            return pageNo >= last;
        }
        // totalCount 없으면, 현재 페이지 아이템 수가 페이지 크기 미만이면 마지막으로 간주
        int size = (resp.body() != null && resp.body().items() != null) ? resp.body().items().size() : 0;
        return size < numOfRows;
    }

    // --- utils --------------------------------------------------------

    private SeatRealtimeResponse parseOrThrow(String rawJson) {
        try {
            return objectMapper.readValue(rawJson, SeatRealtimeResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("외부 응답 파싱 실패", e);
        }
    }

    private static String nz(String v, String fb) {
        return v == null || v.isBlank() ? fb : v;
    }

    // 다양한 길이의 시간 문자열을 고정 14자리로 맞춤
    private String normalizeTotDt(String raw) {
        if (raw == null || raw.isBlank()) return "00000000000000";
        String s = raw.trim();
        if (s.length() >= 14) return s.substring(0, 14);
        if (s.length() == 12) return s + "00";
        if (s.length() == 8)  return s + "000000";
        return "00000000000000";
    }

    private int safeInt(String s) {
        if (s == null) return 0;
        try { return Integer.parseInt(s.trim()); } catch (Exception ignore) { return 0; }
    }

    // page 처리 결과 묶음 (record)
    private record PageResult(int rawInserted, int currentUpserted) {}

    // null/빈문자 제거 후 조합해서 간단 해시로 대체키 생성
    // 원본 키가 비어도 RAW 적재는 하겠다는 관대한 정책(Lenient)을 위한 임시 식별자 생성기
    /**
     * synthKey(합성키)
     * - 원본에서 pblibId/rdrmId가 비어온 경우, 임시로 만들어 쓰는 키 ex) UNK_a1b2c3d4
     * - RAW를 최대한 보존하기 위해, 원본이 불완전해도 "무슨 데이터가 들어왔는지" 기록으로
     *      추후 매핑 테이블이나 수작업으로 복구/분석용
     * = 임시키는 충돌 가능/식별 신뢰도 하락, 그래서 CURRENT(조회 기준)에는 사용하지 않음
     *
     * Strict 모드
     * = 실키(진짜 원본 ID)가 없는 아이템은 아에 스킵한다(=synthKey 생성도 하지 않음)
     * = 장점: 코드 단순/데이터 일관성 좋음/CURRENT 품질 보장
     * = 단점: RAW 보존률 낮음(원본 누락이 아에 버려짐)
     */
    private String synthKey(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (StringUtils.hasText(p)) {
                if (sb.length() > 0) sb.append('|');
                sb.append(p.trim());
            }
        }
        if (sb.length() == 0) {
            return "UNK_" + System.nanoTime(); // 최후의 fallback
        }
        int h = sb.toString().hashCode();
        return "UNK_" + Integer.toHexString(h);
    }

}
