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
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "20") int numOfRows,
            @RequestParam(required = false) String libName,
            @RequestParam(required = false) String region,
            Model model
    ) {
        // 현재 서비스는 한페이지 호출 -> 클라이언트 필터 -> 상위 limit 반환
        SeatRealtimePage page = seatService.getSeatRealtimePage(pageNo, numOfRows, libName, region, limit);

        boolean hasPrev = page.pageNo() > 1;
        boolean hasNext = false;

        model.addAttribute("items", page.items());
        model.addAttribute("pageNo", page.pageNo());
        model.addAttribute("numOfRows", page.numOfRows());
        model.addAttribute("totalCount", page.totalCount());        // 현재는 필터 후 개수
        model.addAttribute("hasPrev", hasPrev);
        model.addAttribute("hasNext", hasNext);
        model.addAttribute("prevPage", page.pageNo() - 1);
        model.addAttribute("nextPage", page.pageNo() + 1);

        // 검색 파라매터
        model.addAttribute("libName", libName);
        model.addAttribute("region", region);
        model.addAttribute("limit", limit);

        return "seats"; // templates/seats.html
    }
}
