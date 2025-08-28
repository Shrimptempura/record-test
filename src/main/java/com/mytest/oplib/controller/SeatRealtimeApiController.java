package com.mytest.oplib.controller;

import com.mytest.oplib.dto.SeatRealtimePage;
import com.mytest.oplib.dto.SeatRealtimeResponse;
import com.mytest.oplib.service.SeatRealtimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/lib/seat")
public class SeatRealtimeApiController {

    private final SeatRealtimeService seatService;

    @GetMapping("/raw")
    public SeatRealtimeResponse getRaw(
            @RequestParam(defaultValue = "1")  int pageNo,
            @RequestParam(defaultValue = "10") int numOfRows,
            @RequestParam(required = false, name = "pblibId") String libraryId,
            @RequestParam(required = false, name = "rdrmId")  String readingRoomId
    ) {
        return seatService.getSeatRealtimeRaw(pageNo, numOfRows, libraryId, readingRoomId);
    }

    @GetMapping("/page")
    public SeatRealtimePage getPage(
            @RequestParam(defaultValue = "1")  int pageNo,
            @RequestParam(defaultValue = "20") int numOfRows,
            @RequestParam(required = false, name = "pblibId") String libraryId,
            @RequestParam(required = false, name = "rdrmId")  String readingRoomId
    ) {
        return seatService.getSeatRealtimePage(pageNo, numOfRows, libraryId, readingRoomId);
    }
}
