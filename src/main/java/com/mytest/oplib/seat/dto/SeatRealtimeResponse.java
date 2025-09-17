package com.mytest.oplib.seat.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SeatRealtimeResponse(Header header, Body body) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(String resultCode, String resultMsg) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(
            String numOfRows,
            String pageNo,
            String totalCount,
            @JsonProperty("item")
            @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            List<Item> items // item이 1개일 때도 List로 받도록 처리
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String stdgCd,
            String lclgvNm,
            String pblibId,
            String pblibNm,
            String rdrmId,
            String rdrmNo,
            String rdrmNm,
            String rdrmTypeNm,
            String bldgFlrExpln,
            String nowVstrCnt,
            String tseatCnt,
            String useSeatCnt,
            String rsvtSeatCnt,
            String rmndSeatCnt,
            String totDt
    ) {}
}
