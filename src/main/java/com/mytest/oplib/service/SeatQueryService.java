package com.mytest.oplib.service;

import com.mytest.oplib.dao.SeatCurrentRoomMapper;
import com.mytest.oplib.dto.CurrentRoomKey;
import com.mytest.oplib.dto.CurrentRoomSearchCond;
import com.mytest.oplib.dto.SeatSnapshotView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SeatQueryService {

    private final SeatCurrentRoomMapper mapper;

    public SeatSnapshotView getByKey(String pblibId, String rdrmId) {
        return mapper.findByKey(new CurrentRoomKey(pblibId, rdrmId));
    }

    public List<SeatSnapshotView> search(String name, String region, Integer page, Integer size) {
        int limit = (size == null || size <= 0) ? 20 : Math.min(size, 100);
        int offset = (page == null || page <= 1) ? 0 : (page - 1) * limit;

        CurrentRoomSearchCond cond = new CurrentRoomSearchCond(name, region, limit, offset);
        return mapper.search(cond);
    }
}
