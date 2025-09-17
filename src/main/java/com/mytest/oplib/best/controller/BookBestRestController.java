package com.mytest.oplib.best.controller;

import com.mytest.oplib.best.dto.BookBestPageResponse;
import com.mytest.oplib.best.dto.BookBestRow;
import com.mytest.oplib.seat.service.BookSort;
import com.mytest.oplib.best.service.BusanBestQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/books")
public class BookBestRestController {

    private final BusanBestQueryService queryService;

    @GetMapping
    public BookBestPageResponse getBooks(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(name="numOfRows", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String author
    ) {
        BookSort order = queryService.parseOrderOrDefault(sort);
        List<BookBestRow> rows = queryService.page(pageNo, pageSize, order, title, author);
        int total = queryService.count(title, author);

        return BookBestPageResponse.of(
                rows, pageNo, pageSize, total,
                order.name(), title, author
        );
    }
}
