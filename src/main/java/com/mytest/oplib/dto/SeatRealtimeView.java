package com.mytest.oplib.dto;

import java.util.List;

public record SeatRealtimeView(
        String libraryName,
        String roomName,
        int totalSeats,
        int usedSeats,
        int reservedSeats,
        int remainSeats,
        String updatedAt,
        int nowVisitorCount
) {}

