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

        URI uri = b.build(true).toUri(); // 전체를 '이미 인코딩됨'으로 취급(키가 Encoding)
        
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
        SeatRealtimeResponse dto = getSeatRealtimeRaw(pageNo, numOfRows);

        SeatRealtimeResponse.Body body = (dto != null) ? dto.body() : null;
        List<SeatRealtimeResponse.Item> items =
                (body != null && body.items() != null) ? body.items() : Collections.emptyList();

        final String kName = safeLower(libName);
        final String kRegion = safeLower(region);

        // 클라이언트 필터(부분일치 + 대소문자 무시)
        List<SeatRealtimeView> views = new ArrayList<>(items.size());
        for (SeatRealtimeResponse.Item it : items) {
            String nm = safeLower(it.pblibNm());
            String rg = safeLower(it.lclgvNm());

            boolean nameMatch = kName.isEmpty() || nm.contains(kName);
            boolean regionMatch = kRegion.isEmpty() || rg.contains(kRegion);

            if (!nameMatch || !regionMatch) {
                continue;
            }

            // view 매핑
            String humanReadable;
            try {
                LocalDateTime dt = LocalDateTime.parse(nvl(it.totDt()), RAW_FMT);
                humanReadable = dt.format(HUMAN_FMT);
            } catch (Exception e) {
                humanReadable = nvl(it.totDt());
            }

            views.add(new SeatRealtimeView(
                    nvl(it.pblibNm()),
                    nvl(it.lclgvNm()),
                    nvl(it.rdrmNm()),
                    toInt(it.tseatCnt()),
                    toInt(it.useSeatCnt()),
                    toInt(it.rsvtSeatCnt()),
                    toInt(it.rmndSeatCnt()),
                    humanReadable,
                    toInt(it.nowVstrCnt())
            ));

            if (limit > 0 && views.size() >= limit) {
                break;
            }
        }
        
        int total = 0;      // 필터된 결과 수
        return new SeatRealtimePage(views, pageNo, numOfRows, total);
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

    private static int toInt(String s) {
        if (s == null) return 0;
        try {
            return Integer.parseInt(s.trim());
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
}
