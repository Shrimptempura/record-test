package com.mytest.oplib.dto;

// 원본 테이블 저장
public record SeatRawUpsertCmd(
        String source,
        String pblibId,
        String rdrmId,
        String totDt,
        String payloadJson
) { }
