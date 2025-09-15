package com.mytest.oplib.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mytest.oplib.config.BusanBestProps;
import com.mytest.oplib.dto.BookBestResponse;
import com.mytest.oplib.dto.BookBestView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class BusanBestService {

    private final RestClient restClient;
    private final BusanBestProps props;

    public record BookBestPage(List<BookBestView> items, int pageNo, int numOfRows, int totalCount) {}

    private final ObjectMapper objectMapper;

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

    public String fetchRawJson(int pageNo, int numOfRows, String title, String author) {
        URI uri = buildUri(pageNo, numOfRows, title, author);
        return restClient.get().uri(uri).retrieve().body(String.class);
    }
}
