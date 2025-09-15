package com.mytest.oplib.service;

import com.mytest.oplib.dao.BookBestMapper;
import com.mytest.oplib.dto.BookBestRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class BusanBestQueryService {

    private final BookBestMapper mapper;

    public List<BookBestRow> page(int pageNo, int size, BookSort order, String title, String author) {
        int limit = Math.max(1, Math.min(size, 500));
        int page = Math.max(1, pageNo);
        int offset = (page - 1) * limit;

        String orderKey = (order == null) ? BookSort.RANK_ASC.name() : order.name();
        String t = norm(title);
        String a = norm(author);

        return mapper.selectPage(orderKey, limit, offset, t, a);
    }

    public int count(String title, String author) {
        return mapper.countAll(norm(title), norm(author));
    }

    // 안전 변환(enum)
    public BookSort parseOrderOrDefault(String sort) {
        if (sort == null || sort.isBlank()) {
            return BookSort.RANK_ASC;
        }

        try {
            return BookSort.valueOf(sort.trim().toUpperCase());
        } catch (Exception e) {
            return BookSort.RANK_ASC;
        }
    }

    private String norm(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }

        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
