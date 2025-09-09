package com.mytest.oplib.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mytest.oplib.dao.SeatCurrentRoomMapper;
import com.mytest.oplib.dao.SeatRawItemMapper;
import com.mytest.oplib.dto.CurrentRoomKey;
import com.mytest.oplib.dto.SeatRawUpsertCmd;
import com.mytest.oplib.dto.SeatRealtimeResponse;
import com.mytest.oplib.service.ingest.SeatItemNormalizer;
import com.mytest.oplib.util.OpenApiResultValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

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

    // 1단계 분리: 정규화 전담 컴포넌트
    private final SeatItemNormalizer normalizer;

    // 최대 페이지 상한 (무한루프 방지)
    private static final int MAX_PAGES = 1000;

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
     *
     *  *    ==== 수정됨 ====
     *  *    타깃제외
     *  *    전량 수집 - 세 값 모두 null로 호출
     *  *    스케줄러의 진입점(호출당함)
     */
    @Transactional
    public void ingestOneTarget(String pblibId, String stdgCd, String rdrmId, int numOfRows) {
        int pageNo = 1;
        int totalRaw = 0;
        int totalCur = 0;

        // nomOfRows 방어
        if (numOfRows <= 0) {
            log.warn("SeatIngestService - numOfRows <= 0 방어, 1로 보정");
            numOfRows = 1;
        }

        while (true) {
            if (pageNo > MAX_PAGES) {
                log.warn("SeatIngestService - 페이지 상한 도달 → 종료 - pblibId:{}, rdrmId:{}", MAX_PAGES, pblibId, rdrmId);
                break;
            }

            String rawJson = client.fetch(pblibId, rdrmId, Integer.valueOf(pageNo), Integer.valueOf(numOfRows));
            if (rawJson == null || rawJson.isBlank()) {
                log.info("SeatIngestService - 빈 응답 → 종료 (pblibId={}, stdgCd={}, rdrmId={}, pageNo={})", pblibId, stdgCd, rdrmId, pageNo);
                break;
            }

            SeatRealtimeResponse resp = parseOrThrow(rawJson);

            // 헤더 검증 (K03 '데이터 없음'은 예외 던지지 않고 빈 처리)
            String code = resp.header() != null ? nz(resp.header().resultCode(), "") : "";
            String msg  = resp.header() != null ? nz(resp.header().resultMsg(), "")  : "";
            try {
                OpenApiResultValidator.validate(code, msg);
            } catch (Exception ex) {
                log.warn("SeatIngestService - 헤더 검증 실패 → 종료 (code={}, msg={}, pageNo={})", code, msg, pageNo);
                break;
            }

            // 아이템 없으면 종료
            SeatRealtimeResponse.Body body = resp.body();
            if (body == null || body.items() == null || body.items().isEmpty()) {
                log.info("SeatIngestService - 아이템 없음 → 종료 (pageNo={})", pageNo);
                break;
            }

            // 페이지 처리
            PageResult r = processPageItems(resp);
            totalRaw += r.rawInserted();
            totalCur += r.currentUpserted();

            // 페이지 요약 로그
            log.info("SeatIngestService - page={}, items{}, raw+={}, cur+={}", pageNo, body.items().size(), r.rawInserted(), r.currentUpserted());

            // 마지막 페이지 판단
            if (isLastPage(resp, pageNo, numOfRows)) {
                break;
            }
            pageNo++;
        }

        log.info("SeatIngestService - done target: pblibId={}, stdgCd={}, rdrmId={}, rawInserted={}, currentUpserted={}",
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
    private PageResult processPageItems(SeatRealtimeResponse resp) {
        int rawInserted = 0;
        int curUpserted = 0;

        SeatRealtimeResponse.Body body = resp.body();

        for (SeatRealtimeResponse.Item item : body.items()) {
//            // 타깃 vs 아이템 불일치 탐지 (디버깅용)
//            if (pblibIdFromTarget != null && item.pblibId() != null
//                    && !pblibIdFromTarget.equals(item.pblibId())) {
//                log.warn("[MISMATCH] pblibId target={}, item={}", pblibIdFromTarget, item.pblibId());
//            }
//            if (stdgCdFromTarget != null && item.stdgCd() != null
//                    && !stdgCdFromTarget.equals(item.stdgCd())) {
//                log.warn("[MISMATCH] stdgCd target={}, item={}", stdgCdFromTarget, item.stdgCd());
//            }

            // 정규화 로직을 외부 클래스로 이관 (Lenient 고정)
            Optional<SeatItemNormalizer.NormalizedItem> normOpt = normalizer.normalize(item);
            if (normOpt.isEmpty()) {
                continue;
            }

            // return된 record dto
            SeatItemNormalizer.NormalizedItem itemX = normOpt.get();

            // RAW 업서트
            rawInserted += upsertRaw(itemX.stdgCd(), itemX.keyPblibId(), itemX.keyRdrmId(), itemX.totDt14(), itemX.payloadJson());

            // CHANGED: CURRENT 머터리얼라이즈는 "진짜 키"가 있을 때만 + stdgCd도 item/보강 값으로
            if (itemX.hasRealKey()) {
                // 반드시 원본 실키로 머터리어라이즈
                curUpserted += currentMapper.materializeLatestByKey(
                        new CurrentRoomKey(itemX.stdgCd(), itemX.realPblibId(), itemX.realRdrmId())
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
        // 경계 보정
        if (pageNo < 1) pageNo = 1;
        if (numOfRows < 1) numOfRows = 1;

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


    private int safeInt(String s) {
        if (s == null) return 0;
        try { return Integer.parseInt(s.trim()); } catch (Exception ignore) { return 0; }
    }

    // page 처리 결과 묶음 (record)
    private record PageResult(int rawInserted, int currentUpserted) {}

}
