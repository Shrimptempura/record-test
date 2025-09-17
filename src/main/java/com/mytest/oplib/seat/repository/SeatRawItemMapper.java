package com.mytest.oplib.seat.repository;

import com.mytest.oplib.seat.dto.SeatRawUpsertCmd;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SeatRawItemMapper {

    int upsertRaw(SeatRawUpsertCmd cmd);
}
