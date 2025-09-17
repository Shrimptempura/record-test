package com.mytest.oplib.seat.dto;

import java.util.List;

public record SeatRealtimePage(
        List<SeatRealtimeView> items,
        int pageNo,
        int numOfRows,
        int totalCount
) {}