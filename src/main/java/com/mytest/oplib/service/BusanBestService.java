package com.mytest.oplib.service;

import com.mytest.oplib.config.BusanBestProps;
import com.mytest.oplib.dto.BookBestResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

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

    public BookBestResponse fetch(int pageNo, int numOfRows) {
        URI uri = buildUri(pageNo, numOfRows);
        log.debug("Busan API GET: {}", uri);        // 키가 URL에 노출됨으로 운영로그에 남기지 않기
        
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
}
