package com.mytest.oplib.controller;

import com.mytest.oplib.dto.BookBestView;
import com.mytest.oplib.service.BusanBestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class PageController {

    private final BusanBestService busanBestService;

    @GetMapping("/books")
    public String books(Model model) {
        List<BookBestView> list = busanBestService.fetchSimple(1, 20);
        model.addAttribute("books", list);
        return "books";     // templates/testBooks.html
    }
}
