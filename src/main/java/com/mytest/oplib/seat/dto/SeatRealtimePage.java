package com.mytest.oplib.seat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "실시간 좌석 현황 페이지 응답")
public record SeatRealtimePage(

        @Schema(description = "좌석 현황 아이템 목록")
        List<SeatRealtimeView> items,

        @Schema(description = "현재 페이지 번호(1-base)", example = "1")
        int pageNo,

        @Schema(description = "페이지 크기", example = "20")
        int numOfRows,

        @Schema(description = "전체 건수", example = "221")
        int totalCount
) {}