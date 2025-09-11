package com.mytest.oplib.service;

import com.mytest.oplib.config.BusanBestProps;
import com.mytest.oplib.dto.BookBestResponse;
import com.mytest.oplib.dto.BookBestView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class BusanBestService {

    private final RestClient restClient;
    private final BusanBestProps props;

    public record BookBestPage(List<BookBestView> items, int pageNo, int numOfRows, int totalCount) {}

    /**
     * 외부 API 최종 URI 조립
     * - 서비스키가 "Encoding 키"면 .build(true)
     * - decoding 키를 쓸 거면, .build().encode()로 바꿔야 함
     */
    private URI buildUri(int pageNo, int numOfRows, String title, String author) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(props.getBaseUrl())
                .path("/getBookLoanBest")
                .queryParam("serviceKey", props.getServiceKey().trim())
                .queryParam("pageNo", pageNo)
                .queryParam("numOfRows", numOfRows)
                .queryParam("resultType", "json");

        // null/빈문자열/공백 문자열을 -> 값 없음 처리
        if (StringUtils.hasText(title)) {
            builder.queryParam("title", UriUtils.encode(title.trim(), StandardCharsets.UTF_8));
        }

        if (StringUtils.hasText(author)) {
            builder.queryParam("author", UriUtils.encode(author.trim(), StandardCharsets.UTF_8));
        }

        return builder.build(true).toUri();
    }

    /**
     * 1) 원문 구조로 받기 (디버그/검증용 또는 내부 가공용)
     *    - resultCode 검사 포함
     */
    public BookBestResponse fetch(int pageNo, int numOfRows, String title, String author) {
        URI uri = buildUri(pageNo, numOfRows, title, author);

        // 원래 try-catch나 전역 예외 처리함
        BookBestResponse res = restClient.get()
                .uri(uri)
                .retrieve()
                .body(BookBestResponse.class);

        // 기본 검증
        if (res == null || res.response() == null || res.response().header() == null) {
            throw new IllegalStateException("Busan API response is null");
        }

        String code = res.response().header().resultCode();
        if (!"00".equals(code)) {
            String msg = res.response().header().resultMsg();
            throw new IllegalStateException("Busan API 실패: " + code + " - " + msg);
        }

        return res;
    }

    /**
     * 2) 프런트가 쓰기 쉬운 요약 리스트
     */
    public BookBestPage fetchSimple(int pageNo, int numOfRows, String title, String author, BookSort sort) {
        // 항상 100건 받기
        BookBestResponse res = fetch(1, 100, title, author);

        BookBestResponse.Body body = res.response().body();
        if (body == null || body.items() == null || body.items().item() == null) {
            return new BookBestPage(List.of(), 1, (numOfRows <= 0) ? 20 : numOfRows, 0);
        }

        List<BookBestResponse.Item> src = res.response().body().items().item();
        List<BookBestView> items = new ArrayList<>(src.size());

        for (BookBestResponse.Item it : src) {
            items.add(new BookBestView(it.rank(), it.title(), it.author(), it.lib_name(), it.image(), it.publish_year()));
        }

        // 정렬(기본: rank ASC)
        BookSort s = (sort == null) ? BookSort.RANK_ASC : sort;
        switch (s) {
            case PUBLISH_YEAR_DESC -> items.sort(
                    Comparator.comparingInt((BookBestView v) -> parseYear(v.publishYear()))
                            .reversed()
                            .thenComparingInt(v -> parseInt(v.rank()))
            );

            case PUBLISH_YEAR_ASC -> items.sort(
                    Comparator.comparingInt((BookBestView v) -> parseYear(v.publishYear()))
                            .thenComparingInt(v -> parseInt(v.rank()))
            );

            case RANK_ASC -> items.sort(
                    Comparator.comparingInt(v -> parseInt(v.rank()))
            );
        }

        int totalCount = items.size();
        int safeSize = (numOfRows <= 0) ? 20 : Math.min(numOfRows, 100);
        int safePage = (pageNo <= 1) ? 1 : pageNo;
        int from = (safePage - 1) * safeSize;
        int to = Math.min(from + safeSize, totalCount);

        List<BookBestView> pageItems = (from >= totalCount) ? List.of() : items.subList(from, to);

        return new BookBestPage(pageItems, safePage, safeSize, totalCount);
    }

    /**
     * (선택) 원문 JSON 문자열 그대로 반환하고 싶을 때
     *  - 초기 점검용 엔드포인트에서 편함
     */
    public String fetchRawJson(int pageNo, int numOfRows, String title, String author) {
        URI uri = buildUri(pageNo, numOfRows, title, author);
        return restClient.get().uri(uri).retrieve().body(String.class);
    }

    private static int parseYear(String year) {
        if (year == null) {
            return 0;
        }

        try {
            return Integer.parseInt(year.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static int parseInt(String str) {
        if (str == null) {
            return 0;
        }

        try {
            return Integer.parseInt(str.trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
