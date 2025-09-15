package com.mytest.oplib.dao;

import com.mytest.oplib.dto.BookBestRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BookBestMapper {

    // 테이블 내용 삭제
    int deleteAll();

    // 테이블 대입
    int batchInsert(@Param("rows") List<BookBestRow> rows);

    List<BookBestRow> selectPage(@Param("orderBy") String orderBy,
                                 @Param("limit") int limit,
                                 @Param("offset") int offset,
                                 @Param("title") String title,
                                 @Param("author") String author);

    int countAll(@Param("title") String title,
                 @Param("author") String author);
}
