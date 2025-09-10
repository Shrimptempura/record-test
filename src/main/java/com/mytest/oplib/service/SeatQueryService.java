package com.mytest.oplib.service;

import com.mytest.oplib.dto.SeatSnapshotView;

import java.util.List;

public interface SeatQueryService {

    // 이름/지역 검색 + 페이징(search + count)
    List<SeatSnapshotView> search(String stdgCd, String name, String region, Integer page, Integer size);

    // 검색 조건된 총 건수
    int count(String stdgCd, String name, String region);

}
