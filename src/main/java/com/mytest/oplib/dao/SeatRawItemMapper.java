package com.mytest.oplib.dao;

import com.mytest.oplib.dto.SeatRawUpsertCmd;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SeatRawItemMapper {

    int upsertRaw(SeatRawUpsertCmd cmd);
}
