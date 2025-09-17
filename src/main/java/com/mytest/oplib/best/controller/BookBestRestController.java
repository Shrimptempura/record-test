package com.mytest.oplib.best.controller;

import com.mytest.oplib.best.dto.BookBestPageResponse;
import com.mytest.oplib.best.dto.BookBestRow;
import com.mytest.oplib.best.dto.BookSort;
import com.mytest.oplib.best.service.BusanBestQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Books", description = "주간 인기 대출 도서 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/books")
public class BookBestRestController {

    private final BusanBestQueryService queryService;
    
    @Operation(
            summary = "주간 인기 대출 도서 조회",
            description = "부산 지역 도서관 기준의 인기 대출 도서를 페이지 단위로 조회 "
                    + "제목/저자 키워드 검색과 정렬(랭킹순, 발행년도순) 지원"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "정상 조회",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BookBestPageResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content),
            @ApiResponse(responseCode = "500", description = "서버 오류", content = @Content)
    })

    @GetMapping
    public BookBestPageResponse getBooks(
            @Parameter(description = "페이지 번호(1-base)", example = "1")
            @Min(1) @RequestParam(defaultValue = "1") int pageNo,

            @Parameter(description = "페이지 크기", example = "20")
            @Min(1) @Max(100) @RequestParam(name="numOfRows", defaultValue = "20") int pageSize,

            @Parameter(
                    description = "정렬 키(랭킹순: RANK_ASC, 발행년도순: PUBLISH_YEAR_DESC)",
                    schema = @Schema(implementation = BookSort.class),
                    example = "RANK_ASC")
            @RequestParam(required = false) String sort,

            @Parameter(description = "제목 키워드", example = "행복은 어디에")
            @RequestParam(required = false) String title,

            @Parameter(description = "저자 키워드", example = "홍길동")
            @RequestParam(required = false) String author
    ) {
        BookSort order = queryService.parseOrderOrDefault(sort);
        List<BookBestRow> rows = queryService.page(pageNo, pageSize, order, title, author);
        int total = queryService.count(title, author);

        return BookBestPageResponse.of(
                rows, pageNo, pageSize, total,
                order, title, author
        );
    }
}
