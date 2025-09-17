package com.mytest.oplib.best.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record BookBestRequest(
        @Min(1) int pageNo,
        @Min(1) @Max(100) int numOfRows,
        String sort,
        String title,
        String author
) {}
