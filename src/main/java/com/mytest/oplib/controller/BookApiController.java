package com.mytest.oplib.controller;

import com.mytest.oplib.service.BusanBestService;
import com.mytest.oplib.dto.BookBestView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/books")
@RequiredArgsConstructor
public class BookApiController {

    private final BusanBestService service;

    @GetMapping
    public PageResponse<BookBestView> list(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int numOfRows,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String author
    ) {
        // service는 그대로 사용
        var page = service.fetchSimple(pageNo, numOfRows, title, author);

        // 안전 보정(옵션): pageNo/numOfRows가 과도하면 잘라주고 싶으면 여기서 처리
        int safeRows = Math.max(1, Math.min(page.numOfRows(), 200));
        int totalPages = Math.max(1, (page.totalCount() + safeRows - 1) / safeRows);
        int safePage = Math.min(Math.max(1, page.pageNo()), totalPages);

        // 페이지 보정 반영해서 다시 조회하고 싶으면 service 재호출도 가능
        // 여기서는 단순히 계산만 반영해 응답 구성
        return new PageResponse<>(
                page.items(),
                safePage,
                safeRows,
                page.totalCount(),
                totalPages
        );
    }

    public record PageResponse<T>(List<T> items, int page, int size, int total, int totalPages) {}
}
