package com.mytest.oplib.controller;

import com.mytest.oplib.dto.SeatSnapshotView;
import com.mytest.oplib.service.SeatQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 스케줄링 버전 0905
@RestController
@RequestMapping("/seats")
@RequiredArgsConstructor
public class SeatPageController {

    private final SeatQueryService query;

    @GetMapping
    public PageResponse<SeatSnapshotView> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String stdgCd,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safeSize = Math.max(1, Math.min(size, 500));
        int total = query.count(stdgCd, name, region);
        int totalPages = Math.max(1, (total + safeSize - 1) / safeSize);
        int safePage = Math.min(Math.max(1, page), totalPages);

        List<SeatSnapshotView> items = query.search(stdgCd, name, region, safePage, safeSize);
        return new PageResponse<>(items, safePage, safeSize, total, totalPages);
    }

    public record PageResponse<T>(
            List<T> items, int page, int size, int total, int totalPages
    ) {}


}


