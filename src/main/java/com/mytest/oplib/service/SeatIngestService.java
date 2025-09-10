package com.mytest.oplib.service;

/**
 * 실시간 좌석 수집/적재 파이프라인의 파사드(Service 인터페이스)
 * - 구현체에서 트랜잭션 경계 및 예외(DataAccessException 등) 처리
 * - 컨트롤러/스케줄러가 이 인터페이스에만 의존
 *
 * [확장 가능성]
 *  - 필요 시 Overload로 DTO(예: SeatFetchRequest) 하나로 받는 버전 추가 가능
 *  - 배치 대상 범위를 name/region 조건으로 제한하는 메서드는 나중에 논의
 */
public interface SeatIngestService {

    // 원문 페이지를 seat_raw_item에 적재
    int ingestRawPage(String pblibId, String rdrmId, Integer pageNo, Integer numOfRows);

    // 최신 원본을 기준으로 단건 머터리얼라이즈
    // seat_current_room 갱신(업서트)
    // 키는 stdgCd + pblibId + rdrmId
    int materializeLatestByKey(String stdgCd, String pblibId, String rdrmId);

    // 전체/일괄 머터리얼 라이즈
    // 구현체에서 대상 전략 결정
    int materializeAll();

    // 원샷 갱신: fetch -> normalize -> current_room 업서트
    // raw 적재 생략 버전
    int refreshCurrentByKey(String stdgCd, String pblibId, String rdrmId);

    // 디버그 조회
    String debugFetchRaw(String pblibId, String rdrmId, Integer pageNo, Integer numOfRows);
}
