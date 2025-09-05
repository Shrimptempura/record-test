package com.mytest.oplib.dao;

import com.mytest.oplib.dto.SeatRawUpsertCmd;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SeatRawItemMapper {

    int upsertRaw(SeatRawUpsertCmd cmd);

    int upsertCurrent(@Param("stdgCd") String stdgCd,
                      @Param("rdrmId") String rdrmId,
                      @Param("totDt") String totDt,
                      @Param("totalSeats") int totalSeats,
                      @Param("occupied") int occupied,
                      @Param("available") int available);
}
