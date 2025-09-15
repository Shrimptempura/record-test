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

    public List<BookBestRow> page(int pageNo, int size, BookSort order) {
        int limit = Math.max(1, Math.min(size, 500));
        int page = Math.max(1, pageNo);
        int offset = (page - 1) * limit;

        String orderKey = (order == null) ? BookSort.RANK_ASC.name() : order.name();
        return mapper.selectPage(orderKey, limit, offset);
    }

    public int count() {
        return mapper.countAll();
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
}
