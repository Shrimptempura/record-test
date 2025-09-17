package com.mytest.oplib.best.dto;

import java.util.List;

public record BookBestPageResponse(
        List<BookItem> items,
        int pageNo,
        int pageSize,
        int totalCount,
        String sort,
        String title,
        String author
) {
    public static BookBestPageResponse of(
            List<BookBestRow> rows, int pageNo, int pageSize, int totalCount,
            String sort, String title, String author
    ) {
        return new BookBestPageResponse(
                rows.stream().map(BookItem::from).toList(),
                pageNo, pageSize, totalCount, sort, title, author
        );
    }

    public record BookItem(
            Long id,
            Integer rank,
            String title,
            String author,
            String libName,
            String image,
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
