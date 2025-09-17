package com.mytest.oplib.seat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mytest.oplib.seat.config.SeatIngestProps;
import com.mytest.oplib.seat.repository.SeatCurrentRoomMapper;
import com.mytest.oplib.seat.repository.SeatRawItemMapper;
import com.mytest.oplib.seat.dto.CurrentRoomKey;
import com.mytest.oplib.seat.dto.SeatRawUpsertCmd;
import com.mytest.oplib.seat.dto.SeatRealtimeResponse;
import com.mytest.oplib.seat.service.SeatItemNormalizer.NormalizedItem;
import com.mytest.oplib.util.OpenApiResultValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatIngestServiceImpl implements SeatIngestService {

    private final SeatOpenApiClient client;          // fetch(String,String,Integer,Integer) → String
    private final SeatRawItemMapper rawMapper;       // RAW 업서트
    private final SeatCurrentRoomMapper currentMapper; // CURRENT 업서트/머터리얼라이즈
    private final ObjectMapper objectMapper;
    private final SeatIngestProps props;

    // 1단계 분리: 정규화 전담 컴포넌트
    private final SeatItemNormalizer normalizer;
    
    // 페이지 단위 트랜잭션 실행용
    private final TransactionTemplate transactionTemplate;

    @Override
    public void ingestAll(String pblibId, String stdgCd, String rdrmId, int numOfRows) {
        int pageNo = 1;
        int totalRaw = 0;
        int totalCur = 0;

        while (pageNo <= props.maxPages()) {
            String rawJson = client.fetch(pblibId, rdrmId, pageNo, numOfRows);
            if (isBlank(rawJson)) {
                log.warn("SeatIngestImpl - 빈 응답으로 종료 - pblibId:{}, stdgCd:{}, rdrmId:{}, pageNo:{}",
                        pblibId, stdgCd, rdrmId, pageNo);
                break;
            }

            // 파싱
            SeatRealtimeResponse resp = parseOrThrow(rawJson);
            if (!isHeaderOk(resp)) {
                log.warn("SeatIngestImpl - 헤더 검증 실패로 종료");
                break;
            }

            if (isEmptyItems(resp)) {
                log.warn("SeatIngestImpl - 아이템 없음으로 종료");
                break;
            }

            // 페이지 단위 트랜잭션
            PageResult result = transactionTemplate.execute(status -> processOnePage(resp));
            if (result != null) {
                totalRaw += result.rawInserted;
                totalCur += result.currentUpserted;
                log.info("SeatIngestImpl - pageNo:{}, totalRaw:{}, totalCur:{}", pageNo, totalRaw, totalCur);
            } else {
                log.warn("SeatIngestImpl - pageNo:{}, totalRaw:{}, totalCur:{}", pageNo, totalRaw, totalCur);
            }

            if (isLastPage(resp, pageNo, numOfRows)) {
                break;
            }
            pageNo++;
        }
        log.info("SeatIngestImpl - 전량수집 완료 - rawInserted:{}, currentUpserted:{}", totalRaw, totalCur);
    }

    // 트랜잭션 내에서 실행되는 페이지 단위 처리
    private PageResult processOnePage(SeatRealtimeResponse resp) {
        int rawInserted = 0;
        int curUpserted = 0;

        SeatRealtimeResponse.Body body = resp.body();
        if (body == null || body.items() == null) {
            return new PageResult(0, 0);
        }

        for (SeatRealtimeResponse.Item item : body.items()) {
            Optional<NormalizedItem> normOpt = normalizer.normalize(item);
            if (normOpt.isEmpty()) {
                continue;
            }

            NormalizedItem norm = normOpt.get();
            
            // RAW 업서트(seat_raw_item table은 합성키라도 저장가능)
            rawInserted += upsertRawSafe(norm);
            curUpserted += materializeIfRealKey(norm);
        }
        return new PageResult(rawInserted, curUpserted);
    }

    // 디버깅용 원문조회
    @Override
    public String debugFetchRaw(String pblibId, String rdrmId, Integer pageNo, Integer numOfRows) {
        int pn = (pageNo == null || pageNo <= 0) ? 1 : pageNo;
        int nr = (numOfRows == null || numOfRows <= 0) ? 100 : numOfRows;
        return client.fetch(pblibId, rdrmId, pn, nr);
    }

    // dao 호출 래퍼들(부분 실패 멱등 있음)
    private int upsertRawSafe(NormalizedItem norm) {
        try {
            return rawMapper.upsertRaw(new SeatRawUpsertCmd(
                    "rlt_rdrm_info", norm.stdgCd(), norm.keyPblibId(), norm.keyRdrmId(), norm.totDt14(), norm.payloadJson()
            ));
        } catch (DataAccessException dae) {
            log.warn("SeatIngestImpl - raw upsert 실패 - {}, {}, {}, {}", norm.stdgCd(), norm.keyPblibId(), norm.keyRdrmId(), norm.totDt14());
            return 0;
        }
    }

    private int materializeIfRealKey(NormalizedItem norm) {
        if (!norm.hasRealKey()) {
            return 0;
        }

        try {
            return currentMapper.materializeLatestByKey(new CurrentRoomKey(
                    norm.stdgCd(), norm.realPblibId(), norm.realRdrmId()
            ));
        } catch (DataAccessException dae) {
            log.warn("SeatIngestImpl - materialize 실패(current_room) - {}, {}, {}", norm.stdgCd(), norm.realPblibId(), norm.realRdrmId());
            return 0;
        }
    }
    
    // === helper ==========
    private boolean isLastPage(SeatRealtimeResponse resp, int pageNo, int numOfRows) {
        int safePageNo = pageNo < 1 ? 1 : pageNo;
        int safeNumOfRows = numOfRows < 1 ? 1 : numOfRows;

        int totalCount = safeInt(resp.body() != null ? resp.body().totalCount() : null);
        if (totalCount > 0) {
            int last = (totalCount + safeNumOfRows - 1) / safeNumOfRows;
            return safePageNo >= last;
        }
        int size = (resp.body() != null && resp.body().items() != null) ? resp.body().items().size() : 0;
        return size < safeNumOfRows;
    }

    private SeatRealtimeResponse parseOrThrow(String rawJson) {
        try {
            return objectMapper.readValue(rawJson, SeatRealtimeResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("외부 응답 파싱 실패", e);
        }
    }

    private boolean isHeaderOk(SeatRealtimeResponse r) {
        String code = r.header() != null ? nz(r.header().resultCode(), "") : "";
        String msg  = r.header() != null ? nz(r.header().resultMsg(), "")  : "";
        try {
            OpenApiResultValidator.validate(code, msg);
            return true;
        } catch (Exception e) {
            log.warn("Header invalid: code={}, msg={}", code, msg);
            return false;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean isEmptyItems(SeatRealtimeResponse r) {
        return r.body() == null || r.body().items() == null || r.body().items().isEmpty();
    }

    private static String nz(String v, String fb) {
        return v == null || v.isBlank() ? fb : v;
    }

    private static int safeInt(String s) {
        if (s == null) return 0;
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception ignore) {
            return 0;
        }
    }

    // 페이지 처리 결과(내부 전용)
    private record PageResult(int rawInserted, int currentUpserted) {}
}