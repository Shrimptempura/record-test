package com.mytest.oplib.dto;

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

