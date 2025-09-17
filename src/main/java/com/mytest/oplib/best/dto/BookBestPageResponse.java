package com.mytest.oplib.best.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "주간 인기 대출 도서 페이지 응답")
public record BookBestPageResponse(

        @Schema(description = "도서 아이템 목록")
        List<BookItem> items,

        @Schema(description = "현재 페이지(1-base)", example = "1")
        int pageNo,

        @Schema(description = "페이지 크기", example = "20")
        int pageSize,

        @Schema(description = "전체 건수", example = "100")
        int totalCount,

        @Schema(description = "정렬 키(랭킹순: RANK_ASC, 발행년도순: PUBLISH_YEAR_DESC)", example = "RANK_ASC")
        BookSort sort,

        @Schema(description = "제목 검색 키워드", example = "행복은 어디에")
        String title,

        @Schema(description = "저자 검색 키워드", example = "홍길동")
        String author
) {
    public static BookBestPageResponse of(
            List<BookBestRow> rows, int pageNo, int pageSize, int totalCount,
            BookSort sort, String title, String author
    ) {
        return new BookBestPageResponse(
                rows.stream().map(BookItem::from).toList(),
                pageNo, pageSize, totalCount, sort, title, author
        );
    }

    @Schema(description = "주간 인기 도서 단건 뷰")
    public record BookItem(

            @Schema(description = "도서 ID", example = "99")
            Long id,

            @Schema(description = "도서 주간 랭킹", example = "1")
            Integer rank,

            @Schema(description = "도서명", example = "어린 왕자")
            String title,

            @Schema(description = "저자명", example = "생텍쥐페리")
            String author,

            @Schema(description = "소장 도서관명", example = "부산시립중앙도서관")
            String libName,

            @Schema(description = "표지 이미지 URL(없을수도 있음)", example = "https://...")
            String image,

            @Schema(description = "발행년도", example = "2001")
            Integer publishYear
    ) {
        public static BookItem from(BookBestRow r) {
            return new BookItem(
                    r.bookId(),
                    r.bookRank(),
                    r.title(),
                    r.author(),
                    r.libName(),
                    r.image(),
                    r.publishYear()
            );
        }
    }
}
