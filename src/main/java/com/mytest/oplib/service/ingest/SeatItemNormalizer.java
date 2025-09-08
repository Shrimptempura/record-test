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
    public Optional<NormalizedItem> normalize(SeatRealtimeResponse.Item i,
                                              String stdgCdFromTarget,
                                              String rdrmIdFilter) {
        // 1) 필터: 요청 rdrmId가 지정된 경우 그 외는 스킵
        if (StringUtils.hasText(rdrmIdFilter) && !rdrmIdFilter.equals(i.rdrmId())) {
            return Optional.empty();
        }

        // 2) stdgCd: 아이템 값 우선, 없으면 타깃값
        String stdg = StringUtils.hasText(i.stdgCd()) ? i.stdgCd() : stdgCdFromTarget;

        // 3) 키 결정(Lenient): 실키가 없으면 synthKey로 대체키 생성
        // realP, realR: 원본에서 온 진짜 키
        String realP = i.pblibId();
        String realR = i.rdrmId();
        boolean hasReal = StringUtils.hasText(realP) && StringUtils.hasText(realR);

        String keyP = realP;
        String keyR = realR;
        if (!hasReal) {
            keyP = StringUtils.hasText(keyP) ? keyP : synthKey(i.pblibNm(), i.lclgvNm());
            keyR = StringUtils.hasText(keyR) ? keyR : synthKey(i.rdrmNm());
            if (!StringUtils.hasText(keyP) && !StringUtils.hasText(keyR)) {
                return Optional.empty();
            }
        }

        // 4) 시간 정규화
        String tot = normalizeTotDt(i.totDt());

        // 5) JSON 직렬화(아이템 단위 RAW payload)
        String json;
        try {
            json = objectMapper.writeValueAsString(i);
        } catch (Exception e) {
            return Optional.empty();
        }
        return Optional.of(new NormalizedItem(stdg, keyP, keyR, tot, json, hasReal, realP, realR));

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
                    sb.append('|');
                }
                sb.append(p.trim());
            }
        }
        if (sb.length() == 0) {
            return "UNK_" + System.nanoTime();
        }
        int h = sb.toString().hashCode();
        return "UNK_" + Integer.toHexString(h);
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
