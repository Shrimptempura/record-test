package com.mytest.oplib.best.dto;

// 프런트용 요약 dto
public record BookBestView(
        String rank,
        String title,
        String author,
        String libName,
        String image,
        String publishYear      // 정렬필드
) {}
