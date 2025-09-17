package com.mytest.oplib.seat.service;

import com.mytest.oplib.seat.repository.SeatCurrentRoomMapper;
import com.mytest.oplib.seat.dto.CurrentRoomSearchCond;
import com.mytest.oplib.seat.dto.SeatSnapshotView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 조회 전용 서비스
 * - SeatCurrentRoomMapper를 감싸서 DB에 저장된 서브 테이블(current_room) 기준으로 검색/조회 기능 제공
 * - 매번 직접 조회하지 않고, DB에 저장된 최신 스냅샷(current_room)을 대상으로 조회
 */
@Service
@RequiredArgsConstructor
public class SeatQueryServiceImpl implements SeatQueryService{

    private final SeatCurrentRoomMapper mapper;

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 500;

    /**
     * 리스트 검색
     * 이름/지역 조건을 이용한 키워드 검색 기능
     * 페이징 처리(limit/offset)도 여기서 계산해서 mapper에 전달
     */

    @Override
    @Transactional(readOnly = true)
    public List<SeatSnapshotView> search(String stdgCd, String name, String region, Integer page, Integer size) {
        int limit = normalizeSize(size);
        int pageno = normalizePage(page);
        int offset = (pageno - 1) * limit;

        CurrentRoomSearchCond cond = new CurrentRoomSearchCond(stdgCd, name, region, limit, offset);

        return mapper.search(cond);
    }

    /** 전체 건수(검색 반영)
     * 검색 조건 반영된 전체 건수: 프론트 총 개수 표시용
     * */

    @Override
    @Transactional(readOnly = true)
    public int count(String stdgCd, String name, String region) {
        CurrentRoomSearchCond cond = new CurrentRoomSearchCond(stdgCd, name, region, null, null);
        return mapper.count(cond);
    }

    private int normalizeSize(Integer size) {
        int s = (size == null || size <= 0) ? DEFAULT_SIZE : size;
        return Math.min(s, MAX_SIZE);
    }

    private int normalizePage(Integer page) {
        int p = (page == null || page <= 0) ? 1 : page;
        return p;
    }
}

