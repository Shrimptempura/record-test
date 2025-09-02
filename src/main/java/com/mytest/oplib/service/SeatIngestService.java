package com.mytest.oplib.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mytest.oplib.dao.SeatRawItemMapper;
import com.mytest.oplib.dto.SeatRawUpsertCmd;
import com.mytest.oplib.dto.SeatRealtimeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatIngestService {

    private final SeatOpenApiClient client;
    private final SeatRawItemMapper rawMapper;

    // Json <-> Java 객체 변환
    private final ObjectMapper objectMapper;

    @Transactional
    public int ingest(String pblibId, String rdrmId) {
        // 1) 외부 호출 (raw JSON)
        String rawJson = client.fetch(pblibId, rdrmId);

        // 2) 파싱 (SeatRealtimeResponse)
        SeatRealtimeResponse resp;
        try {
            resp = objectMapper.readValue(rawJson, SeatRealtimeResponse.class);
        } catch (Exception e) {
            log.error("SeatIngestService - Failed to parse JSON response", e);
            throw new IllegalStateException("외부 응답 파싱 실패", e);
        }

        // resp.body가 널이면 isEmpty???
        if (resp.body() == null || resp.body().items() == null) {
            log.info("SeatIngestService - 수집 항목 없음 - pblibId: {}, rdrmId: {}", pblibId, rdrmId);
            return 0;
        }
        
        // 3) item 마다 업서트
        int totalAffected = 0;
        for (SeatRealtimeResponse.Item item : resp.body().items()) {
            String keyPblibId = nz(item.pblibId(), pblibId);
            String keyRdrmId = nz(item.rdrmId(), rdrmId);
            String totDt14 = normalizeTotDt(item.totDt());

            SeatRawUpsertCmd cmd = new SeatRawUpsertCmd(
                    "rlt_rdrm_info",
                    keyPblibId,
                    keyRdrmId,
                    totDt14,
                    rawJson
            );
            totalAffected += rawMapper.upsertRaw(cmd);
        }

        return totalAffected;
    }

    // --- helper ---
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
