package com.mytest.oplib.best.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

// 외부 OpenAPI 응답 매핑용
@JsonIgnoreProperties(ignoreUnknown = true)
public record BookBestResponse(Envelope response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Envelope(Header header, Body body) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(String resultMsg, String resultCode) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(String totalCount, Items items, String pageNo, String numOfRows) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Items(List<Item> item) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String rank,
            String title,
            String author,
            String publisher,
            String shelf_loc_name,
            String cnt,
            String publish_year,
            String lib_name,
            String image
    ) {}
}