package com.mytest.oplib.service;

import com.mytest.oplib.config.LibSeatProps;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Component      // 외부 api 어댑터 성격이라 component, (service도 가능)
@RequiredArgsConstructor
public class SeatOpenApiClient {

    private final RestClient restClient;
    private final LibSeatProps props;

    public String fetch(String pblibId, String rdrmId, Integer pageNo, Integer numOfRows) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(props.getBaseUrl())
                .queryParam("serviceKey", props.getServiceKey().trim())
                .queryParam("Type", "json");

        if (StringUtils.hasText(pblibId)) {
            builder.queryParam("pblibId", pblibId.trim());
        }

        if (StringUtils.hasText(rdrmId)) {
            builder.queryParam("rdrmId", rdrmId.trim());
        }

        if (pageNo != null && pageNo > 0) {
            builder.queryParam("pageNo", pageNo);
        }
        if (numOfRows != null && numOfRows > 0) {
            builder.queryParam("numOfRows", numOfRows);
        }

        URI uri = builder.build(true).toUri();

        return restClient.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);    // 원본 JSON 문자열로 반환
    }
}
