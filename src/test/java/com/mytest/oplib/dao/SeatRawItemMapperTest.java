package com.mytest.oplib.dao;

import com.mytest.oplib.seat.dto.SeatRawUpsertCmd;
import com.mytest.oplib.seat.repository.SeatRawItemMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * seat_raw_item 업서트 스모크 테스트
 * - 외부 API 없이, 더미 JSON으로 DB 적재 경로만 검증
 * - 실행 전: seat_raw_item 테이블과 UNIQUE(pblib_id,rdrm_id,tot_dt) 준비
 */
@SpringBootTest
class SeatRawItemMapperTest {

    @Autowired
    private SeatRawItemMapper mapper;

    @Test
    void upsertRaw_shouldInsertOrUpdate() {
        // 1) 더미 키/JSON (totDt는 14자리로)
        String source = "rlt_rdrm_info";
        String stdgCd = "LIB001";
        String pblibId = "LIB001";
        String rdrmId = "ROOM01";
        String totDt = "20250901103000";

        String payloadJson = """
                {
                    "header": {
                        "resultCode": "00",
                        "resultMsg": "OK" },
                    "body": {
                        "item": [{
                        "stdgCd": "LIB001",
                        "pblibId": "LIB001",
                        "rdrmId": "ROOM01",
                        "nowVstrCnt": "12",
                        "tseatCnt": "100",
                        "totDt": "20250901103000"
                        }]
                    }
                }
                """;

        // 2) 커맨드 생성 후 업서트 호출
        SeatRawUpsertCmd cmd = new SeatRawUpsertCmd(
                source, stdgCd, pblibId, rdrmId, totDt, payloadJson
        );

        int affected = mapper.upsertRaw(cmd);

        // 3) 삽입(1) 또는 갱신(2 이상)이어야 한다.
        assertThat(affected).isGreaterThan(0);
    }
}