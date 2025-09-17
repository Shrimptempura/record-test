package com.mytest.oplib.seat.dto;

// 원본 테이블 저장
public record SeatRawUpsertCmd(
        String source,
        String stdgCd,
        String pblibId,
        String rdrmId,
        String totDt,
        String payloadJson
) { }
