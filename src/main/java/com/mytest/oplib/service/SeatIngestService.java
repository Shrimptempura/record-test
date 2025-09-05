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
     * - pblibId/stdgCd/rdrmId/numOfRows를 받아 페이지 끝까지 수집
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
            PageResult r = processPageItems(resp, stdgCd, pblibId, rdrmId, rawJson);
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

    // 페이지 내 아이템 처리
    private PageResult processPageItems(SeatRealtimeResponse resp,
                                        String stdgCd, String pblibId, String rdrmId,
                                        String rawJsonForRaw) {
        int rawInserted = 0;
        int curUpserted = 0;

        for (SeatRealtimeResponse.Item item : resp.body().items()) {

            // 요청 rdrmId가 지정되면 해당 아이템만 처리 (안전 필터)
            if (StringUtils.hasText(rdrmId) && !rdrmId.equals(item.rdrmId())) {
                continue;
            }

            String keyPblibId = StringUtils.hasText(item.pblibId()) ? item.pblibId()
                    : synthKey(item.pblibNm(), item.lclgvNm());
            String keyRdrmId  = StringUtils.hasText(item.rdrmId())  ? item.rdrmId()
                    : synthKey(item.rdrmNm());

            if (!StringUtils.hasText(keyPblibId) || !StringUtils.hasText(keyRdrmId)) {
                log.warn("[INGEST] 키 부족 → 스킵 (pblibId={}, rdrmId={})", keyPblibId, keyRdrmId);
                continue;
            }

            String totDt14 = normalizeTotDt(item.totDt());
            // RAW: 아이템 단위 JSON 저장 (원하면 item만 직렬화해서 저장)
            String itemJson = toItemJsonOr(rawJsonForRaw, item);

            rawInserted += upsertRaw(stdgCd, keyPblibId, keyRdrmId, totDt14, itemJson);

            // CURRENT: 진짜 키(pblibId+rdrmId)가 있을 때만 최신 반영 (머터리얼라이즈 방식)
            if (StringUtils.hasText(item.pblibId()) && StringUtils.hasText(item.rdrmId())) {
                curUpserted += currentMapper.materializeLatestByKey(
                        new CurrentRoomKey(item.stdgCd(), item.pblibId(), item.rdrmId()));
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

    private String toItemJsonOr(String fallbackRaw, SeatRealtimeResponse.Item item) {
        try {
            return objectMapper.writeValueAsString(item);
        } catch (Exception e) {
            // 아이템만 직렬화 실패 시, 원본 raw를 그대로 보관(증거 용도)
            return fallbackRaw;
        }
    }

    private static String nz(String v, String fb) {
        return v == null || v.isBlank() ? fb : v;
    }

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
}
