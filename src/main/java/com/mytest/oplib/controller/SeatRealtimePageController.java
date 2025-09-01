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
            @RequestParam(defaultValue = "500") int numOfRows,
            @RequestParam(required = false) String libName,
            @RequestParam(required = false) String region,
            Model model
    ) {
        int safePageNo = Math.max(1, pageNo);

        // 현재 서비스는 한페이지 호출 -> 클라이언트 필터 -> 상위 limit 반환
        SeatRealtimePage page = seatService.getSeatRealtimePage(safePageNo, numOfRows, libName, region, limit);

        // 페이지 계산
        boolean hasPrev = page.pageNo() > 1;
        boolean hasNext = (long) page.pageNo() * page.numOfRows() < page.totalCount();
        int prevPage = hasPrev ? page.pageNo() - 1 : page.pageNo();
        int nextPage = hasNext ? page.pageNo() + 1 : page.pageNo();

        model.addAttribute("items", page.items());
        model.addAttribute("pageNo", page.pageNo());
        model.addAttribute("numOfRows", page.numOfRows());          // = limit
        model.addAttribute("totalCount", page.totalCount());        // 필터된 전체 건수
        model.addAttribute("hasPrev", hasPrev);
        model.addAttribute("hasNext", hasNext);
        model.addAttribute("prevPage", prevPage);
        model.addAttribute("nextPage", nextPage);

        // 검색 파라매터
        model.addAttribute("libName", libName);
        model.addAttribute("region", region);
        model.addAttribute("limit", limit);

        model.addAttribute("rawNumOfRows", numOfRows);

        return "seats"; // templates/seats.html
    }
}
