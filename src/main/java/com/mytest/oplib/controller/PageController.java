package com.mytest.oplib.controller;

import com.mytest.oplib.service.BusanBestService;
import com.mytest.oplib.service.BusanBestService.BookBestPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class PageController {

    private final BusanBestService busanBestService;

    @GetMapping("/books")
    public String books(@RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int numOfRows,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String author,
            Model model) {
        BookBestPage page = busanBestService.fetchSimple(pageNo, numOfRows, title, author);

        // 페이징 계산
        boolean hasPrev = page.pageNo() > 1;
        boolean hasNext = (page.pageNo() * page.numOfRows()) < page.totalCount();
        int nextPage = hasNext ? page.pageNo() + 1 : page.pageNo();
        int prevPage = hasPrev ? page.pageNo() - 1 : page.pageNo();

        model.addAttribute("books", page.items());
        model.addAttribute("pageNo", page.pageNo());
        model.addAttribute("numOfRows", page.numOfRows());
        model.addAttribute("totalCount", page.totalCount());
        model.addAttribute("title", title);
        model.addAttribute("author", author);
        model.addAttribute("hasPrev", hasPrev);
        model.addAttribute("hasNext", hasNext);
        model.addAttribute("prevPage", prevPage);
        model.addAttribute("nextPage", nextPage);

        return "books";     // template/books.html
    }
}
