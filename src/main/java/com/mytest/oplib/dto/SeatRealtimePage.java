package com.mytest.oplib.dto;

import java.util.List;

public record SeatRealtimePage(
        List<SeatRealtimeView> items,
        int pageNo,
        int numOfRows,
        int totalCount
) {}