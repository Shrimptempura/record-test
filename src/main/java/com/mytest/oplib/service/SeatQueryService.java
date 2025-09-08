package com.mytest.oplib.service;

import com.mytest.oplib.dao.SeatCurrentRoomMapper;
import com.mytest.oplib.dto.CurrentRoomKey;
import com.mytest.oplib.dto.CurrentRoomSearchCond;
import com.mytest.oplib.dto.SeatSnapshotView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 조회 전용 서비스
 * - SeatCurrentRoomMapper를 감싸서 DB에 저장된 서브 테이블(current_room) 기준으로 검색/조회 기능 제공
 * - 매번 직접 조회하지 않고, DB에 저장된 최신 스냅샷(current_room)을 대상으로 조회
 */
@Service
@RequiredArgsConstructor
public class SeatQueryService {

    private final SeatCurrentRoomMapper mapper;

    /**
     * PK 조회
     * 복합키(stdgCd, pblibId, rdrmId)로 특정 열람실 좌석 정보 가져옴
     *      (API에서 내려온 원본과 DB를 1:1 매핑하기 위한 키 구조)
     */
    public SeatSnapshotView getByKey(String stdgCd, String pblibId, String rdrmId) {
        return mapper.findByKey(new CurrentRoomKey(stdgCd, pblibId, rdrmId));
    }

    /**
     * 리스트 검색
     * 이름/지역 조건을 이용한 키워드 검색 기능
     * 페이징 처리(limit/offset)도 여기서 계산해서 mapper에 전달
     */
    public List<SeatSnapshotView> search(String stdgCd, String name, String region, Integer page, Integer size) {
        int safeMax = 500;
        int limit = (size == null || size <= 0) ? 20 : Math.min(size, safeMax);
        int offset = (page == null || page <= 1) ? 0 : (page - 1) * limit;

        CurrentRoomSearchCond cond = new CurrentRoomSearchCond(stdgCd, name, region, limit, offset);
        return mapper.search(cond);
    }

    /** 전체 건수(검색 반영)
     * 검색 조건 반영된 전체 건수: 프론트 총 개수 표시용
     * */
    public int count(String stdgCd, String name, String region) {
        CurrentRoomSearchCond cond = new CurrentRoomSearchCond(stdgCd, name, region, null, null);
        return mapper.count(cond);
    }

    /** 전체 건수(검색 조건 없음) */
    public int countAll() {
        return mapper.count(new CurrentRoomSearchCond( null,null, null, null, null));
    }
}
