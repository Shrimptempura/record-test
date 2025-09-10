package com.mytest.oplib.service;

public interface SeatIngestService {

    // 전량 수집 엔트리 포인트
    void ingestAll(String pblibId, String stdgCd, String rdrmId, int numOfRows);

    // 디버그 조회
    String debugFetchRaw(String pblibId, String rdrmId, Integer pageNo, Integer numOfRows);
}
