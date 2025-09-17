package com.mytest.oplib.seat.service;

import com.mytest.oplib.seat.config.LibSeatProps;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * 관심사 분리 = Clinet, 이제 service로 대부분 넘김
 * 호출: SeatIngestScheduler(스케쥴러), SeatIngestService(페이지 루프를 돌면서 Client.featch() 호출),
 *      SeatQueryService(화면/REST용 조회 서비스)
 * 요약: 외부 Open API를 호출해서 JSON 원본 데이터를 가져오는 역할
 */

/**
 * 외부 Open API 호출 어댑터
 * - 전수 모드에서 null/빈 파라미터는 쿼리에 "붙이지 않음"  ← (중요)
 * - 상태코드/소요시간/바디 길이 로깅
 * - 4xx/5xx 등 예외는 잡아 null 반환 → 상위 루프가 종료/스킵 판단
 */
@Slf4j
@Component      // 외부 api 어댑터 성격이라 component, (service도 가능)
@RequiredArgsConstructor
public class SeatOpenApiClient {

    private final RestClient restClient;
    private final LibSeatProps props;

    // pageNo, numOfRows는 null 허용 -> Integer
    public String fetch(String pblibId, String rdrmId, Integer pageNo, Integer numOfRows) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(props.getBaseUrl())
                .queryParam("serviceKey", props.getServiceKey().trim())
                .queryParam("type", "json");

        // 부분
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

        log.info("[FETCH] pblibId={}, rdrmId={}, pageNo={}, numOfRows={}", pblibId, rdrmId, pageNo, numOfRows);

        URI uri = builder.build(true).toUri();

        log.info("[CLIENT] uri={}", uri);
        return restClient.get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);    // 원본 JSON 문자열로 반환
    }
}
