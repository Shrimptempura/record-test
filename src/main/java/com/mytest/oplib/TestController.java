package com.mytest.oplib;

import com.mytest.oplib.config.BusanBestProps;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@RequiredArgsConstructor
public class TestController {

    private final RestClient restClient;
    private final BusanBestProps props;

    @GetMapping("/home")
    public ResponseEntity<String> raw() {
        // 문서의 메서드명: getBookLoanBest
        URI uri = UriComponentsBuilder.fromHttpUrl(props.getBaseUrl())
                .queryParam("serviceKey", props.getServiceKey()) // Decoding 키 사용
                .queryParam("numOfRows", 10)
                .queryParam("pageNo", 1)
                .queryParam("resultType", "json")                // JSON으로 받기
                // .queryParam("title", "")                      // 옵션
                // .queryParam("author", "")                     // 옵션
                .build(true)                                     // 자동 인코딩
                .toUri();

        String body = restClient.get()
                .uri(uri)
                .retrieve()
                .body(String.class);

        return ResponseEntity.ok(body); // 원문 그대로 반환(맛보기)
    }
}
