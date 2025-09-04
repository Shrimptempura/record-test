package com.mytest.oplib.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mytest.oplib.dao.SeatCurrentRoomMapper;
import com.mytest.oplib.dao.SeatRawItemMapper;
import com.mytest.oplib.dto.CurrentRoomKey;
import com.mytest.oplib.dto.SeatRawUpsertCmd;
import com.mytest.oplib.dto.SeatRealtimeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// 최신버전0904
/**
 * 외부 open api 호풀로 raw JSON 저장(seat_raw_item)
 * item 별 최신내용 materialize(seat_current_room)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatIngestService {

    private final SeatOpenApiClient client;
    private final SeatRawItemMapper rawMapper;
    private final SeatCurrentRoomMapper currentMapper;

    // Json <-> Java 객체 변환
    private final ObjectMapper objectMapper;

    @Transactional
    public int ingestAndMaterialize(String pblibId, String rdrmId, Integer pageNo, Integer numOfRows) {
        // 1) 외부 호출 (raw JSON)
        String rawJson = client.fetch(pblibId, rdrmId, pageNo, numOfRows);
        log.info("INGEST fetch ok, rawLen={}", (rawJson == null ? 0 : rawJson.length()));

        // 2) 파싱 (SeatRealtimeResponse)
        SeatRealtimeResponse resp = parseOrThrow(rawJson);
        if (resp.body() == null || resp.body().items() == null) {
            log.info("SeatIngestService - 수집 항목 없음 - pblibId: {}, rdrmId: {}", pblibId, rdrmId);
            return 0;
        }

        // 3) item 마다 업서트 + materialize
        int totalAffected = 0;
        for (SeatRealtimeResponse.Item item : resp.body().items()) {
            // 요청에 rdrmId가 들어오면 그 item만 대상으로(안전필터)
            if (StringUtils.hasText(rdrmId) && !rdrmId.equals(item.rdrmId())) {
                continue;
            }

            String keyPblibId = StringUtils.hasText(item.pblibId())
                    ? item.pblibId()
                    : synthKey(item.pblibNm(), item.lclgvNm());
            String keyRdrmId = StringUtils.hasText(item.rdrmId())
                    ? item.rdrmId()
                    : synthKey(item.rdrmNm());

            String stdgCd = item.stdgCd();

            if (!StringUtils.hasText(keyPblibId) || !StringUtils.hasText(keyRdrmId)) {
                log.warn("SeatIngestService - Skipped item with missing key - pblibId: {}, rdrmId: {}", keyPblibId, keyRdrmId);
                continue;
            }

            String totDt14 = normalizeTotDt(item.totDt());

            String itemJson;
            try {
                itemJson = objectMapper.writeValueAsString(item);
            } catch (Exception e) {
                log.warn("SeatIngestService - Failed to serialize item to JSON - pblibId: {}, rdrmId: {}", keyPblibId, keyRdrmId, e);
                continue;
            }

            // 원본 raw 업서트
            SeatRawUpsertCmd cmd = new SeatRawUpsertCmd(
                    "rlt_rdrm_info",
                    stdgCd,
                    keyPblibId,
                    keyRdrmId,
                    totDt14,
                    itemJson
            );
            rawMapper.upsertRaw(cmd);

            // 최신 스냅샷 materialize
            totalAffected += currentMapper.materializeLatestByKey(new CurrentRoomKey(stdgCd, keyPblibId, keyRdrmId));
        }

        log.info("SeatIngestService - 성공 - totalAffected: {}", totalAffected);
        return totalAffected;
    }

    /**
     * 업스트림에 존재하는 "모든" 아이템을 자동으로 수집.
     * - totalCount 기반 최종 페이지 계산 + 빈 페이지 조기 종료
     * - pageSize는 업스트림 허용 최대치 권장(예: 100)
     * - maxPages는 안전 가드
     */
    @Transactional
    public int ingestAllAuto(Integer pageSize, Integer maxPages) {
        final int size = (pageSize == null || pageSize <= 0) ? 100 : pageSize;
        final int guard = (maxPages == null || maxPages <= 0) ? 5000 : maxPages;

        // 1페이지
        String raw1 = client.fetch(null, null, 1, size);
        SeatRealtimeResponse first = parseOrThrow(raw1);
        if (first.body() == null || first.body().items() == null || first.body().items().isEmpty()) {
            log.info("[INGEST] upstream empty on page 1");
            return 0;
        }

        int totalCount = safeInt(first.body().totalCount());
        int lastPage = (totalCount > 0) ? Math.max(1, (totalCount + size - 1) / size) : guard;
        lastPage = Math.min(lastPage, guard);

        int affected = ingestPageObject(first);

        for (int p = 2; p <= lastPage; p++) {
            String raw = client.fetch(null, null, p, size);
            SeatRealtimeResponse dto = parseOrThrow(raw);
            int a = ingestPageObject(dto);
            affected += a;
            if (dto.body() == null || dto.body().items() == null || dto.body().items().isEmpty()) {
                log.info("[INGEST] page {} empty -> stop", p);
                break;
            }
        }
        log.info("[INGEST] ingestAllAuto done, totalAffected={}", affected);
        return affected;
    }

    // SeatIngestService.java
    public String debugFetchRaw(String pblibId, String rdrmId, Integer pageNo, Integer numOfRows) {
        String raw = client.fetch(pblibId, rdrmId, pageNo, numOfRows);
        log.info("[DEBUG] fetch raw: pblibId={}, rdrmId={}, pageNo={}, numOfRows={}, len={}",
                pblibId, rdrmId, pageNo, numOfRows, (raw == null ? 0 : raw.length()));
        return raw;
    }




    // --- helper ---
    private int ingestPageObject(SeatRealtimeResponse resp) {
        if (resp == null || resp.body() == null || resp.body().items() == null) return 0;

        int total=0, savedRaw=0, matz=0, skippedNoMinimal=0, skippedSerialize=0;

        for (SeatRealtimeResponse.Item item : resp.body().items()) {
            total++;

            // raw 저장을 위한 키 보정 (A안 그대로)
            String keyPblibId = StringUtils.hasText(item.pblibId())
                    ? item.pblibId()
                    : synthKey(item.pblibNm(), item.lclgvNm());   // 이름+지역 기반 대체키
            String keyRdrmId  = StringUtils.hasText(item.rdrmId())
                    ? item.rdrmId()
                    : synthKey(item.rdrmNm());                    // 열람실명 기반 대체키

            String stdgCd = item.stdgCd();

            // 최소한 대체키조차 못 만들 정도로 정보가 없다면만 스킵
            if (!StringUtils.hasText(keyPblibId) && !StringUtils.hasText(keyRdrmId)) {
                skippedNoMinimal++;
                continue;
            }

            String totDt14 = normalizeTotDt(item.totDt());

            String itemJson;
            try {
                itemJson = objectMapper.writeValueAsString(item);
            } catch (Exception e) {
                skippedSerialize++;
                continue;
            }

            // RAW는 무조건 보존 (대체키 포함)
            rawMapper.upsertRaw(new SeatRawUpsertCmd("rlt_rdrm_info", stdgCd, keyPblibId, keyRdrmId, totDt14, itemJson));
            savedRaw++;

            // 스냅샷은 “진짜 키”가 있을 때만 반영
            if (StringUtils.hasText(item.pblibId()) && StringUtils.hasText(item.rdrmId())) {
                matz += currentMapper.materializeLatestByKey(new CurrentRoomKey(item.stdgCd(), item.pblibId(), item.rdrmId()));
            }
        }

        log.info("[INGEST] page stats: total={}, savedRaw={}, matz={}, skippedNoMinimal={}, skippedSerialize={}",
                total, savedRaw, matz, skippedNoMinimal, skippedSerialize);

        return matz; // 반환값은 그대로 두되, 통계 로그로 실제 저장 수 확인
    }


    private SeatRealtimeResponse parseOrThrow(String rawJson) {
        try {
            return objectMapper.readValue(rawJson, SeatRealtimeResponse.class);
        } catch (Exception e) {
            log.error("SeatIngestService - Failed to parse JSON response", e);
            throw new IllegalStateException("외부 응답 파싱 실패", e);
        }
    }

    private static String nz(String v, String fallback) {
        return (v == null || v.isBlank()) ? fallback : v;
    }

    private String normalizeTotDt(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        }

        String s = raw.trim();
        if (s.length() == 8) {
            return s + "000000";
        }

        if (s.length() == 12) {
            return s + "00";
        }

        if (s.length() >= 14) {
            return s.substring(0, 14);
        }

        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    private int safeInt(String s) {
        if (s == null) return 0;
        try { return Integer.parseInt(s.trim()); } catch (Exception ignore) { return 0; }
    }

    private String synthKey(String... parts) {
        // null/빈문자 제거하고 조합
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (StringUtils.hasText(p)) {
                if (sb.length() > 0) sb.append('|');
                sb.append(p.trim());
            }
        }
        if (sb.length() == 0) return "UNK_" + System.nanoTime();

        // 간단 해시로 대체키 생성 (충돌 가능성 아주 낮음)
        int h = sb.toString().hashCode();
        return "UNK_" + Integer.toHexString(h);
    }
}
