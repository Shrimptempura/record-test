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
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.lang.Integer.parseInt;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatRealtimeService {

    private final RestClient restClient;
    private final LibSeatProps props;

    private static final DateTimeFormatter RAW_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter HUMAN_FMT = DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH시 mm분 ss초");
    
    // URI 생성 + encoding키 + 서버ID 필터 아님(required 파라매터에 없는 필드사용 예정)
    private URI buildSeatUri(int pageNo, int numOfRows) {
        UriComponentsBuilder b = UriComponentsBuilder
                .fromHttpUrl(props.getBaseUrl()) // ex) https://apis.data.go.kr/B551982/plr/rlt_rdrm_info
                .queryParam("serviceKey", props.getServiceKey().trim())
                .queryParam("resultType", "json")
                .queryParam("pageNo", pageNo)
                .queryParam("numOfRows", numOfRows);

        URI uri = b.build(true).toUri(); // 전체를 이미 인코딩됨으로 취급(키가 Encoding)
        
        return uri;
    }

    // HTTP 호출 + 역직렬화이후 레코드(dto)로 받기
    public SeatRealtimeResponse getSeatRealtimeRaw(int pageNo, int numOfRows) {
        URI uri = buildSeatUri(pageNo, numOfRows);

        SeatRealtimeResponse responseDto = restClient.get()
                .uri(uri)
                .retrieve()
                .body(SeatRealtimeResponse.class);

        validateSeatApiResponse(responseDto);
        return responseDto;
    }

    // 프런트가 쓰기 쉬운 뷰
    // body.items를 가져와 필터/검증/뷰 dto로 매핑
    public SeatRealtimePage getSeatRealtimePage(int pageNo, int numOfRows, String libName, String region, int limit) {

        int safeViewPageNo = Math.max(1, pageNo);
        int safeApiNumOfRows = Math.max(1, numOfRows);
        int safeLimit = (limit > 0) ? limit : 20;

        final String kName = normalizeKeyword(libName);
        final String kRegion = normalizeKeyword(region);
        log.info("[SEAT] param viewPageNo={}, apiNumOfRows={}, limit={}, libName='{}', region='{}'",
                pageNo, numOfRows, limit, libName, region);

        // 업스트림 1페이지 먼저 호출(총 건수 확보)
        SeatRealtimeResponse first = getSeatRealtimeRaw(1, safeViewPageNo);
        SeatRealtimeResponse.Body firstBody = (first != null) ? first.body() : null;
        List<SeatRealtimeResponse.Item> firstItems =
                (firstBody != null && firstBody.items() != null) ? firstBody.items() : Collections.emptyList();

        // 업스트림 totalCount 피싱(전체 데이터 건수 - 필터 전)
        int apiTotal = toInt((firstBody != null) ? firstBody.totalCount() : "0");
        int lastApiPage = Math.max(1, (apiTotal + safeApiNumOfRows - 1) / safeApiNumOfRows);

        // 필터된 전체 건수/현재 페이지 구간 수집
        int filteredTotal = 0;
        int viewStart = (safeViewPageNo - 1) * safeLimit;   // 포함
        int viewEndExclusive = viewStart + safeLimit;       // 미포함
        log.info("[SEAT] upstream totalCount={}, lastApiPage={}, firstItems={}",
                apiTotal, lastApiPage, firstItems.size());

        // 클라이언트 필터(부분일치 + 대소문자 무시)
        List<SeatRealtimeView> pageViews = new ArrayList<>(safeLimit);

        // 업스트림 페이지를 순회하며 필터 적용 + 현재 뷰 구간만 수집 + 전체 카운트 집계(fetch)
        for (int apiPage = 1; apiPage <= lastApiPage; apiPage++) {
            List<SeatRealtimeResponse.Item> items;

            if (apiPage == 1) {
                items = firstItems;
            } else {
                SeatRealtimeResponse dto = getSeatRealtimeRaw(apiPage, safeApiNumOfRows);
                SeatRealtimeResponse.Body body = (dto != null) ? dto.body() : null;
                items = (body != null && body.items() != null) ? body.items() : Collections.emptyList();
            }

            for (SeatRealtimeResponse.Item item : items) {
                String nm = safeLower(item.pblibNm());
                String rg = safeLower(item.lclgvNm());

                boolean nameMatch = kName.isEmpty() || (nm != null && nm.contains(kName));
                boolean regionMatch = kRegion.isEmpty() || (rg != null && rg.contains(kRegion));
                if (!nameMatch || !regionMatch) {
                    continue;
                }

                // 필터 일치: 전체 카운트 증가
                int idx = filteredTotal++;

                // 현재 뷰 페이지 구간에 속하면 수집
                if (idx >= viewStart && idx < viewEndExclusive) {
                    pageViews.add(toView(item));
                }
            }
        }
        log.info("[SEAT] filteredTotal={}, pageViews.size={}", filteredTotal, pageViews.size());

        return new SeatRealtimePage(pageViews, safeViewPageNo, safeApiNumOfRows, filteredTotal);
    }

    /**
     * 공통 응답 검증 (성공코드 화이트리스트 허용)
     */
    private void validateSeatApiResponse(SeatRealtimeResponse responseDto) {
        if (responseDto == null || responseDto.header() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Seat API 응답 없음");
        }
        String code = responseDto.header().resultCode();
        String msg = responseDto.header().resultMsg();
        OpenApiResultValidator.validate(code, msg);
    }

    // --- 유틸 ---
    private SeatRealtimeView toView(SeatRealtimeResponse.Item item) {
        String humanReadable;
        try {
            LocalDateTime dt = LocalDateTime.parse(nvl(item.totDt()), RAW_FMT);
            humanReadable = dt.format(HUMAN_FMT);
        } catch (Exception e) {
            humanReadable = nvl(item.totDt());
        }

        return new SeatRealtimeView(
                nvl(item.pblibNm()),
                nvl(item.lclgvNm()),
                nvl(item.rdrmNm()),
                toInt(item.tseatCnt()),
                toInt(item.useSeatCnt()),
                toInt(item.rsvtSeatCnt()),
                toInt(item.rmndSeatCnt()),
                humanReadable,
                toInt(item.nowVstrCnt())
        );
    }

    private static int toInt(String s) {
        if (s == null) return 0;
        try {
            return parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static String nvl(String s) {
        return (s == null) ? "" : s;
    }

    private static String nvl(String s, String def) {
        return (s == null || s.isBlank()) ? def : s;
    }

    private static String safeLower(String s) {
        return (s == null) ? "" : s.toLowerCase();
    }

    private static String normalizeKeyword(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.isEmpty()) return "";
        if ("null".equalsIgnoreCase(t)) return "";

        return t.toLowerCase();
    }
}
