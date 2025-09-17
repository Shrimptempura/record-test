package com.mytest.oplib.best.controller;

import com.mytest.oplib.best.dto.BookBestRow;
import com.mytest.oplib.best.dto.BookSort;
import com.mytest.oplib.best.service.BusanBestQueryService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Hidden
@Controller
@RequiredArgsConstructor
public class PageController {

    private final BusanBestQueryService queryService;

    @GetMapping("/books")
    public String books(@RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int numOfRows,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String author,
            Model model) {

        // sort 문자열 -> enum 변환 (안전 변환, 기본값 RANK_ASC)
        BookSort order = queryService.parseOrderOrDefault(sort);

        List<BookBestRow> rows = queryService.page(pageNo, numOfRows, order, title, author);
        int total = queryService.count(title, author);

        // 페이징 계산
        boolean hasPrev = pageNo > 1;
        boolean hasNext = (pageNo * numOfRows) < total;
        int nextPage = hasNext ? pageNo + 1 : pageNo;
        int prevPage = hasPrev ? pageNo - 1 : pageNo;

        model.addAttribute("books", rows);
        model.addAttribute("pageNo", pageNo);
        model.addAttribute("numOfRows", numOfRows);
        model.addAttribute("totalCount", total);
        model.addAttribute("sort", order.name());
        model.addAttribute("hasPrev", hasPrev);
        model.addAttribute("hasNext", hasNext);
        model.addAttribute("prevPage", prevPage);
        model.addAttribute("nextPage", nextPage);
        model.addAttribute("title", title);
        model.addAttribute("author", author);

        return "books";     // template/books.html
    }
}
