package com.mytest.oplib.dao;

import com.mytest.oplib.dto.CurrentRoomKey;
import com.mytest.oplib.dto.CurrentRoomSearchCond;
import com.mytest.oplib.dto.SeatSnapshotView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * seat_raw_item의 playload(json 원본)에서 필요한 칼럼만 추출해
 * seat_snapshot 테이블에 업서트하고 조회를 제공
 */
@Mapper
public interface SeatCurrentRoomMapper {

    int materializeLatestByKey(@Param("key") CurrentRoomKey key);

    SeatSnapshotView findByKey(@Param("key") CurrentRoomKey key);

    List<SeatSnapshotView> search(@Param("cond") CurrentRoomSearchCond cond);

}
