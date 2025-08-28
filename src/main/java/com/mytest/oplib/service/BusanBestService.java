package com.mytest.oplib.service;

import com.mytest.oplib.config.BusanBestProps;
import com.mytest.oplib.dto.BookBestResponse;
import com.mytest.oplib.dto.BookBestView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class BusanBestService {

    private final RestClient restClient;
    private final BusanBestProps props;

    /**
     * 외부 API 최종 URI 조립
     * - 서비스키가 "Encoding 키"면 .build(true)
     * - decoding 키를 쓸 거면, .build().encode()로 바꿔야 함
     */
    private URI buildUri(int pageNo, int numOfRows) {
        return UriComponentsBuilder.fromHttpUrl(props.getBaseUrl())
                .queryParam("serviceKey", props.getServiceKey().trim())
                .queryParam("pageNo", pageNo)
                .queryParam("numOfRows", numOfRows)
                .queryParam("resultType", "json")
                .build(true)
                .toUri();
    }

    /**
     * 1) 원문 구조로 받기 (디버그/검증용 또는 내부 가공용)
     *    - resultCode 검사 포함
     */
    public BookBestResponse fetch(int pageNo, int numOfRows) {
        URI uri = buildUri(pageNo, numOfRows);
        log.debug("Busan API GET: {}", uri);        // 키가 URL에 노출됨으로 운영로그에 남기지 않기

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
    public List<BookBestView> fetchSimple(int pageNo, int numOfRows) {
        BookBestResponse res = fetch(pageNo, numOfRows);
        List<BookBestResponse.Item> items = res.response().body().items().item();
        return items.stream()
                .map(it -> new BookBestView(
                        it.rank(),
                        it.title(),
                        it.author(),
                        it.lib_name(),
                        it.image()
                ))
                .toList();
    }

    /**
     * (선택) 원문 JSON 문자열 그대로 반환하고 싶을 때
     *  - 초기 점검용 엔드포인트에서 편함
     */
    public String fetchRawJson(int pageNo, int numOfRows) {
        URI uri = buildUri(pageNo, numOfRows);
        return restClient.get().uri(uri).retrieve().body(String.class);
    }
}
