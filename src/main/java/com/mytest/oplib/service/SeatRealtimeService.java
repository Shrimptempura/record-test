package com.mytest.oplib.service;

import com.mytest.oplib.config.LibSeatProps;
import com.mytest.oplib.dto.SeatRealtimePage;
import com.mytest.oplib.dto.SeatRealtimeResponse;
import com.mytest.oplib.dto.SeatRealtimeView;
import com.mytest.oplib.util.OpenApiResultValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatRealtimeService {

    private final RestClient restClient;
    private final LibSeatProps props;

    private static final DateTimeFormatter RAW_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter HUMAN_FMT = DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH시 mm분 ss초");

    /** 외부 API 최종 URI 구성 (Encoding 키 사용) */
    private URI buildSeatUri(int pageNo, int numOfRows, String libraryId, String readingRoomId) {
        UriComponentsBuilder b = UriComponentsBuilder
                .fromHttpUrl(props.getBaseUrl()) // ex) https://apis.data.go.kr/B551982/plr/rlt_rdrm_info
                .queryParam("serviceKey", props.getServiceKey().trim())
                .queryParam("resultType", "json")
                .queryParam("pageNo", pageNo)
                .queryParam("numOfRows", numOfRows);

        // 한글/특수문자 파라미터는 개별 인코딩
        if (StringUtils.hasText(libraryId)) {
            b.queryParam("pblibId", UriUtils.encodeQueryParam(libraryId.trim(), StandardCharsets.UTF_8));
        }
        if (StringUtils.hasText(readingRoomId)) {
            b.queryParam("rdrmId", UriUtils.encodeQueryParam(readingRoomId.trim(), StandardCharsets.UTF_8));
        }

        URI uri = b.build(true).toUri(); // 전체를 '이미 인코딩됨'으로 취급(키가 Encoding)
        if (log.isDebugEnabled()) {
            String masked = uri.toASCIIString().replace(props.getServiceKey(), "***");
            log.debug("Seat API URI = {}", masked);
        }
        return uri;
    }

    /** 원문 JSON 구조 그대로 반환(맛보기/검증 용) */
    public SeatRealtimeResponse getSeatRealtimeRaw(int pageNo, int numOfRows, String libraryId, String readingRoomId) {
        URI uri = buildSeatUri(pageNo, numOfRows, libraryId, readingRoomId);

        SeatRealtimeResponse responseDto = restClient.get()
                .uri(uri)
                .retrieve()
                .body(SeatRealtimeResponse.class);

        validateSeatApiResponse(responseDto);
        return responseDto;
    }

    /** 프런트가 쓰기 좋은 요약 페이지 */
    public SeatRealtimePage getSeatRealtimePage(int pageNo, int numOfRows, String libraryId, String readingRoomId) {
        SeatRealtimeResponse dto = getSeatRealtimeRaw(pageNo, numOfRows, libraryId, readingRoomId);

        var body = dto.body();
        List<SeatRealtimeResponse.Item> items =
                (body != null && body.items() != null) ? body.items() : Collections.emptyList();


        List<SeatRealtimeView> views = new ArrayList<>(items.size());
        for (SeatRealtimeResponse.Item it : items) {
            String humanReadable;
            try {
                LocalDateTime dt = LocalDateTime.parse(nvl(it.totDt()), RAW_FMT);
                humanReadable = dt.format(HUMAN_FMT);
            } catch (Exception e) {
                humanReadable = nvl(it.totDt());
            }

            views.add(new SeatRealtimeView(
                    nvl(it.pblibNm()),
                    nvl(it.rdrmNm()),
                    toInt(it.tseatCnt()),
                    toInt(it.useSeatCnt()),
                    toInt(it.rsvtSeatCnt()),
                    toInt(it.rmndSeatCnt()),
                    humanReadable,
                    toInt(it.nowVstrCnt())
            ));
        }

        int total = 0;
        try {
            total = (body != null) ? Integer.parseInt(nvl(body.totalCount(), "0")) : 0;
        } catch (Exception ignore) {}

        return new SeatRealtimePage(views, pageNo, numOfRows, total);
    }

    /** 공통 응답 검증 (성공코드 화이트리스트 허용) */
    private void validateSeatApiResponse(SeatRealtimeResponse responseDto) {
        if (responseDto == null || responseDto.header() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Seat API 응답 없음");
        }
        String code = responseDto.header().resultCode();
        String msg  = responseDto.header().resultMsg();
        OpenApiResultValidator.validate(code, msg);
    }

    // --- 유틸 ---

    private static int toInt(String s) {
        if (s == null) return 0;
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    private static String nvl(String s) { return (s == null) ? "" : s; }

    private static String nvl(String s, String def) {
        return (s == null || s.isBlank()) ? def : s;
    }
}
