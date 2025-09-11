package com.mytest.oplib.dto;

import java.time.LocalDateTime;

public record BookBestRow(
    Long bookId,
    Integer bookRank,
    String title,
    String author,
    String libName,
    String image,
    Integer publishYear,
    LocalDateTime fetchedAt
) { }
