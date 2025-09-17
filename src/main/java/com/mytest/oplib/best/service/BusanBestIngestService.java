package com.mytest.oplib.best.service;

import com.mytest.oplib.best.repository.BookBestMapper;
import com.mytest.oplib.best.dto.BookBestResponse;
import com.mytest.oplib.best.dto.BookBestRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class BusanBestIngestService {

    private final BusanBestService apiService;
    private final BookBestMapper mapper;

    // api에서 주간 인기 대출 도서 100건 받아 db에 저장
    @Transactional
    public void refreshWeekly(String title, String author) {
        // 1. 외부 api에서 100건 요청
        BookBestResponse res = apiService.fetch(1, 100, title, author);

        if (res.response().body().items() == null) {
            throw new IllegalStateException("BusanIngestService - api 응답에 도서 데이터가 없습니다");
        }

        List<BookBestResponse.Item> items = res.response().body().items().item();
        if (items == null || items.isEmpty()) {
            log.warn("BusanIngestService - api가 비었습니다");
            return;
        }
        
        // 2. JSON -> DB Row 변환
        List<BookBestRow> rows = new ArrayList<>(items.size());
        LocalDateTime now = LocalDateTime.now();
        for (BookBestResponse.Item it : items) {
            Integer year = parseYear(it.publish_year());
            Integer rank = parseInt(it.rank());

            rows.add(new BookBestRow(
                    null,
                    rank,
                    it.title(),
                    it.author(),
                    it.lib_name(),
                    it.image(),
                    year,
                    now
            ));
        }

        // 3. 기존 데이터 삭제 후 새 데이터 삽입
        mapper.deleteAll();
        mapper.batchInsert(rows);

        log.info("BusanIngestService - 주간 인기 대출 도서 {}건 갱신 완료", rows.size());
    }

    private static Integer parseYear(String year) {
        if (year == null) {
            return null;
        }

        try {
            return Integer.parseInt(year);
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer parseInt(String str) {
        if (str == null) {
            return null;
        }

        try {
            return Integer.parseInt(str);
        } catch (Exception e) {
            return null;
        }
    }

}
