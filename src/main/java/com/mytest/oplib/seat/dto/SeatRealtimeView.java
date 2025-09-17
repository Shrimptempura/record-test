package com.mytest.oplib.seat.dto;

public record SeatRealtimeView(
        String libraryName,
        String regionName,
        String roomName,
        int totalSeats,
        int usedSeats,
        int reservedSeats,
        int remainSeats,
        String updatedAt,
        int nowVisitorCount
) {}

