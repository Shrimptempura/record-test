package com.mytest.oplib.best.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "주간 인기 대출 도서 원본 DB 행")
public record BookBestRow(

        @Schema(description = "도서 ID", example = "99")
        Long bookId,

        @Schema(description = "도서 주간 랭킹", example = "1")
        Integer bookRank,

        @Schema(description = "도서명", example = "어린 왕자")
        String title,

        @Schema(description = "저자명", example = "생텍쥐페리")
        String author,

        @Schema(description = "소장 도서관명", example = "부산시립중앙도서관")
        String libName,

        @Schema(description = "표지 이미지 URL(없을수도 있음)", example = "https://...")
        String image,

        @Schema(description = "발행년도", example = "2001")
        Integer publishYear,

        @Schema(description = "데이터 수집 시각", example = "2025-09-09T12:00:00")
        LocalDateTime fetchedAt
) {
}
