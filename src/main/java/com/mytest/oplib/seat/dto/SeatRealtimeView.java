package com.mytest.oplib.seat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "실시간 좌석 현황 단건 뷰")
public record SeatRealtimeView(
        
        @Schema(description = "도서관 이름", example = "서울 중앙 도서관")
        String libraryName,

        @Schema(description = "지역명(시/군/구)", example = "서울특별시")
        String regionName,

        @Schema(description = "열람실 이름", example = "제1열람실")
        String roomName,

        @Schema(description = "총 좌석 수", example = "120")
        int totalSeats,

        @Schema(description = "사용 중인 좌석 수", example = "50")
        int usedSeats,

        @Schema(description = "예약된 좌석 수", example = "5")
        int reservedSeats,

        @Schema(description = "잔여 좌석 수", example = "70")
        int remainSeats,

        @Schema(description = "최종 갱신 시각", example = "2025-09-18T12:30:00")
        String updatedAt,

        @Schema(description = "현재 방문자 수", example = "57")
        int nowVisitorCount
) {}

