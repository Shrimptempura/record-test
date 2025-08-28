package com.mytest.oplib.controller;

import com.mytest.oplib.dto.SeatRealtimePage;
import com.mytest.oplib.service.SeatRealtimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/seats")  // 페이지 전용 prefix
public class SeatRealtimePageController {

    private final SeatRealtimeService seatService;

    @GetMapping
    public String view(
            @RequestParam(defaultValue = "1")  int pageNo,
            @RequestParam(defaultValue = "20") int numOfRows,
            @RequestParam(required = false, name = "pblibId") String libraryId,
            @RequestParam(required = false, name = "rdrmId")  String readingRoomId,
            Model model
    ) {
        SeatRealtimePage page = seatService.getSeatRealtimePage(pageNo, numOfRows, libraryId, readingRoomId);

        boolean hasPrev = page.pageNo() > 1;
        boolean hasNext = (long)page.pageNo() * page.numOfRows() < page.totalCount();

        model.addAttribute("items", page.items());
        model.addAttribute("pageNo", page.pageNo());
        model.addAttribute("numOfRows", page.numOfRows());
        model.addAttribute("totalCount", page.totalCount());
        model.addAttribute("hasPrev", hasPrev);
        model.addAttribute("hasNext", hasNext);
        model.addAttribute("prevPage", page.pageNo() - 1);
        model.addAttribute("nextPage", page.pageNo() + 1);
        model.addAttribute("pblibId", libraryId);
        model.addAttribute("rdrmId", readingRoomId);

        return "seats"; // templates/seats.html
    }
}
