package com.mytest.oplib.dto;

import java.time.LocalDateTime;

public record SeatSnapshotView (
        String pblibId,
        String rdrmId,
        String pblibNm,
        String lclgvNm,
        String rdrmNm,
        Integer nowVstrCnt,
        Integer tseatCnt,
        Integer useSeatCnt,
        Integer rsvtSeatCnt,
        Integer rmndSeatCnt,
        LocalDateTime updatedAt
) { }
