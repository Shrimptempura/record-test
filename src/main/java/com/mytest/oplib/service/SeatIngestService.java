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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
        log.info("INGEST fetch ok, rawLen={}", (rawJson==null?0:rawJson.length()));

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

            String keyPblibId = nz(item.pblibId(), pblibId);
            String keyRdrmId = nz(item.rdrmId(), rdrmId);
            String totDt14 = normalizeTotDt(item.totDt());

            // 원본 raw 업서트
            SeatRawUpsertCmd cmd = new SeatRawUpsertCmd(
                    "rlt_rdrm_info",
                    keyPblibId,
                    keyRdrmId,
                    totDt14,
                    rawJson
            );
            rawMapper.upsertRaw(cmd);

            // 최신 스냅샷 materialize
            totalAffected += currentMapper.materializeLatestByKey(new CurrentRoomKey(keyPblibId, keyRdrmId));
        }

        log.info("SeatIngestService - 성공 - totalAffected: {}", totalAffected);
        return totalAffected;
    }

    // 예: /seats/ingest-all?pageFrom=1&pageTo=30&size=100
    @GetMapping("/ingest-all")
    public String ingestAll(@RequestParam(defaultValue="1") Integer pageFrom,
                            @RequestParam(defaultValue="1") Integer pageTo,
                            @RequestParam(defaultValue="100") Integer size) {
        int total = 0;
        for (int p = pageFrom; p <= pageTo; p++) {
            total += ingestAndMaterialize(null, null, p, size);
        }
        // 옵션: totalCount를 파싱해서 pageTo를 자동 계산하도록 개선 가능
        return "redirect:/seats";
    }

    public String debugFetchRaw(String pblibId, String rdrmId, Integer pageNo, Integer numOfRows) {
        String raw = client.fetch(pblibId, rdrmId, pageNo, numOfRows);
        log.info("DEBUG fetch uri with pblibId={}, rdrmId={}, rawLen={}", pblibId, rdrmId, (raw == null ? 0 : raw.length()));
        return raw;
    }


    // --- helper ---
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
}
