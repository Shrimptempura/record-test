package com.mytest.oplib.seat.controller;

import com.mytest.oplib.seat.dto.SeatSnapshotView;
import com.mytest.oplib.seat.service.SeatQueryService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Hidden
@Controller
@RequestMapping("/seats")
@RequiredArgsConstructor
public class SeatPageController {

    private final SeatQueryService query;

    @GetMapping
    public String page(@RequestParam(required = false) String name,
                       @RequestParam(required = false) String region,
                       @RequestParam(required = false) String stdgCd,
                       @RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "20") Integer size,
                       Model model) {
        int safeSize   = Math.max(1, Math.min(size, 500));
        int total      = query.count(stdgCd, name, region);
        int totalPages = Math.max(1, (total + safeSize - 1) / safeSize);
        int safePage   = Math.min(Math.max(1, page), totalPages);

        List<SeatSnapshotView> items = query.search(stdgCd, name, region, safePage, safeSize);

        boolean hasPrev = safePage > 1;
        boolean hasNext = safePage < totalPages;

        model.addAttribute("items", items);
        model.addAttribute("page", safePage);
        model.addAttribute("size", safeSize);
        model.addAttribute("total", total);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("hasPrev", hasPrev);
        model.addAttribute("hasNext", hasNext);
        model.addAttribute("prevPage", hasPrev ? safePage - 1 : 1);
        model.addAttribute("nextPage", hasNext ? safePage + 1 : safePage);

        model.addAttribute("name", name);
        model.addAttribute("region", region);
        model.addAttribute("stdgCd", stdgCd);

        model.addAttribute("activeTab", "seats");

        return "seats"; // templates/seats.html 그대로 사용
    }
}
