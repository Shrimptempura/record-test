package com.mytest.oplib.best.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "정렬 기준")
public enum BookSort {

    @Schema(description = "기본: 순위 오름차순")
    RANK_ASC,

    @Schema(description = "발행년도 최신순")
    PUBLISH_YEAR_DESC,

    @Schema(description = "발행년도 오래된순")
    PUBLISH_YEAR_ASC
}
