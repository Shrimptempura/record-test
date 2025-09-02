package com.mytest.oplib.service;

import com.mytest.oplib.config.LibSeatProps;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

// @Service
@Component
@RequiredArgsConstructor
public class SeatOpenApiClient {

    private final RestClient restClient;
    private final LibSeatProps props;

    public String fetch(String pblibId, String rdrmId) {
        URI uri = UriComponentsBuilder.fromHttpUrl(props.getBaseUrl())
                .queryParam("serviceKey", props.getServiceKey().trim())
                .queryParam("Type", "json")
                .queryParam("pblibId", pblibId)
                .queryParam("rdrmId", rdrmId)
                .build(true).toUri();

        return restClient.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);
    }
}
