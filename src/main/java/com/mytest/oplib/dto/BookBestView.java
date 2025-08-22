package com.mytest.oplib.dto;

// 프런트용 요약 dto
public record BookBestView(
        String rank,
        String title,
        String author,
        String libName,
        String image
) {}
