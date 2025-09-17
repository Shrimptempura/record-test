package com.mytest.oplib.seat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "좌석 스냅샷 뷰(열람실 단위 현황)")
public record SeatSnapshotView (

        @Schema(description = "공공도서관 ID", example = "2345000000")
        String pblibId,

        @Schema(description = "열람실 ID", example = "RDRM1122")
        String rdrmId,

        @Schema(description = "도서관 이름", example = "서울중앙도서관")
        String pblibNm,

        @Schema(description = "지역명(시/군/구)", example = "서울특별시")
        String lclgvNm,

        @Schema(description = "열람실 이름", example = "제1열람실")
        String rdrmNm,

        @Schema(description = "현재 방문자 수", example = "57")
        Integer nowVstrCnt,

        @Schema(description = "총 좌석 수", example = "120")
        Integer tseatCnt,

        @Schema(description = "사용 중인 좌석 수", example = "63")
        Integer useSeatCnt,

        @Schema(description = "예약된 좌석 수", example = "5")
        Integer rsvtSeatCnt,

        @Schema(description = "잔여 좌석 수", example = "52")
        Integer rmndSeatCnt,

        @Schema(description = "최종 갱신 시각", example = "2025-09-18T12:30:00")
        LocalDateTime updatedAt
) { }
