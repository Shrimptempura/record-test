package com.mytest.oplib.service.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mytest.oplib.dto.SeatRealtimeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * (전량 수집 기준) 아이템 1건 "정규화" 전담 컴포넌트
 * - 타깃/필터 개념 제거  // REMOVED: rdrmId 필터, 타깃 기반 stdgCd 보강
 * - stdgCd: 아이템 값 사용, 없으면 "UNKNOWN"
 * - 키 결정( Lenient ): 실키(pblibId, rdrmId) 둘 다 있으면 그대로 사용
 *                      하나라도 비면 synthKey로 대체키 생성( RAW 저장용 )
 *   ※ CURRENT(조회용) 반영은 항상 "실키가 있는 경우에만" 진행 (서비스에서 결정)
 * - totDt 14자리 정규화
 * - 아이템 JSON 직렬화(payload)
 */
@Component
@RequiredArgsConstructor
public class SeatItemNormalizer {

    private final ObjectMapper objectMapper;

    public Optional<NormalizedItem> normalize(SeatRealtimeResponse.Item item) {
        // 1) stdgCd: 아이템 값 사용, 없으면 "UNKNOWN"
        String stdg = StringUtils.hasText(item.stdgCd()) ? item.stdgCd() : "UNKNOWN";
        
        // 2) 키 결정, 실키(real) 존재 여부
        // realP, realR: 원본에서 온 진짜 키
        String realPblibId = item.pblibId();
        String realRdrmId = item.rdrmId();
        boolean hasRealKey = StringUtils.hasText(realPblibId) && StringUtils.hasText(realRdrmId);

        // 저장용 키(RAW 멱등키 구성요소): 실키가 있으면 실키 그대로, 없으면 합성키
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
            if (!StringUtils.hasText(savePblibId) || !StringUtils.hasText(saveRdrmId)) {
                return Optional.empty();
            }
        }

        // 3) 시간 정규화
        String tot = normalizeTotDt(item.totDt());

        // 4) JSON 직렬화(아이템 단위 RAW payload)
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
    // 여기서의 StringUtils.hasText()는 id가 아니라 nm검사임
    /**
     * synthKey(합성키)
     * - 원본에서 pblibId/rdrmId가 비어온 경우, 임시로 만들어 쓰는 키 ex) UNK_a1b2c3d4
     * - RAW를 최대한 보존하기 위해, 원본이 불완전해도 "무슨 데이터가 들어왔는지" 기록으로
     *      추후 매핑 테이블이나 수작업으로 복구/분석용
     * = 임시키는 충돌 가능/식별 신뢰도 하락, 그래서 CURRENT(조회 기준)에는 사용하지 않음
     *
     * Strict 모드
     * = 실키(진짜 원본 ID)가 없는 아이템은 아에 스킵한다(=synthKey 생성도 하지 않음)
     * = 장점: 코드 단순/데이터 일관성 좋음/CURRENT 품질 보장
     * = 단점: RAW 보존률 낮음(원본 누락이 아에 버려짐)
     */
    /**
     * synthKey(합성키)
     * - 실키가 비는 경우 RAW 보존을 위해 임시 식별자 생성(예: UNK_a1b2c3)
     * - CURRENT(조회 기준)에는 사용하지 않음
     */
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
