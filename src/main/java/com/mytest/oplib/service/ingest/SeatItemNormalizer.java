package com.mytest.oplib.service.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mytest.oplib.dto.SeatRealtimeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 아이템 1건을 "정규화"하는 책임만 가진 컴포넌트 (Lenient 고정)
 * - 필터(rdrmId)
 * - stdgCd 보강(아이템 우선, 없으면 타깃값)
 * - 키 결정: 실키가 없으면 synthKey로 대체(단, CURRENT는 여전히 실키만 반영해야 함)
 * - totDt 14자리 정규화
 * - 아이템 JSON 직렬화(payload)
 */
@Component
@RequiredArgsConstructor
public class SeatItemNormalizer {

    private final ObjectMapper objectMapper;

    // 아이템 1건 정규화(Lenient 고정)
    /**
     * 아이템 1건 정규화 (Lenient 고정)
     * @param item             OpenAPI 응답 아이템
     * @param stdgCdFromTarget 스케줄러/타깃에서 전달된 stdgCd (보강용)
     * @param rdrmIdFilter     처리할 rdrmId가 지정된 경우(없으면 전체)
     */
    public Optional<NormalizedItem> normalize(SeatRealtimeResponse.Item item,
                                              String stdgCdFromTarget,
                                              String rdrmIdFilter) {
        // 1) 필터: 요청 rdrmId가 지정된 경우 그 외는 스킵
        if (StringUtils.hasText(rdrmIdFilter) && !rdrmIdFilter.equals(item.rdrmId())) {
            return Optional.empty();
        }

        // 2) stdgCd: 아이템 값 우선, 없으면 타깃값
        String stdg = StringUtils.hasText(item.stdgCd()) ? item.stdgCd() : stdgCdFromTarget;
        
        // raw에는 key(실키면 실키, 없으면 syntheky(합성키))
        // current material(cuurent_room)은 real 키 사용

        // 3) 키 결정(Lenient): 실키가 없으면 synthKey로 대체키 생성
        // realP, realR: 원본에서 온 진짜 키
        String realPblibId = item.pblibId();
        String realRdrmId = item.rdrmId();
        boolean hasRealKey = StringUtils.hasText(realPblibId) && StringUtils.hasText(realRdrmId);

        // 저장에 실제 쓸값(실키가 있으면 그대로, 없으면 합성키)
        String savePblibId  = realPblibId;
        String saveRdrmId = realRdrmId;
        if (!hasRealKey) {
            //  pblib 합성키 재료: [도서관명, 지자체명]
            savePblibId = StringUtils.hasText(savePblibId)
                    ? savePblibId
                    : synthKey(item.pblibNm(), item.lclgvNm());

            // rdrm 합성키 재료: [도서관명, 열람실명, (선택)지자체명]
            saveRdrmId = StringUtils.hasText(saveRdrmId)
                    ? saveRdrmId
                    : synthKey(item.pblibNm(), item.rdrmNm(), item.lclgvNm());

            // 둘중 하나라도 없으면 저장 불가 -> 스킵
            if (!StringUtils.hasText(savePblibId) && !StringUtils.hasText(saveRdrmId)) {
                return Optional.empty();
            }
        }

        // 4) 시간 정규화
        String tot = normalizeTotDt(item.totDt());

        // 5) JSON 직렬화(아이템 단위 RAW payload)
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(item);
        } catch (Exception e) {
            return Optional.empty();
        }
        return Optional.of(new NormalizedItem(stdg, savePblibId, saveRdrmId, tot, payloadJson, hasRealKey, realPblibId, realRdrmId));

    }

    // 정규화 결과 DTO
    public record NormalizedItem(String stdgCd,
                                 String keyPblibId,     // 저장/RAW에 사용할 키(실키 or 합성키)
                                 String keyRdrmId,      // 저장/RAW에 사용할 키(실키 or 합성키)
                                 String totDt14,
                                 String payloadJson,
                                 boolean hasRealKey,    // 원본이 준 실키(pblibId & rdrmId) 존재 여부
                                 String realPblibId,    // 원본 실키(없으면 null)
                                 String realRdrmId      // 원본 실키(없으면 null)
    ) {}



    // --- helper methods -----------------------------------
    // 간단 합성키(Lenient 전용)
    private String synthKey(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (StringUtils.hasText(p)) {
                if (sb.length() > 0) {
                    sb.append('|');     // 중간 구분자
                }
                sb.append(p.trim());
            }
        }
        if (sb.length() == 0) {
            // 재료 0개 비재현 임시 키
            return "UNK_" + System.nanoTime();  
        }

        // 재현 가능한 단순 해시
        int hash = sb.toString().hashCode();
        return "UNK_" + Integer.toHexString(hash);
    }

    // null/빈/길이 변형을 14자리로 보정
    private String normalizeTotDt(String raw) {
        if (!StringUtils.hasText(raw)) return "00000000000000";
        String s = raw.trim();
        if (s.length() == 14) return s.substring(0, 14);
        if (s.length() == 12) return s + "00";
        if (s.length() == 8) return s + "000000";
        return "00000000000000";
    }

}
