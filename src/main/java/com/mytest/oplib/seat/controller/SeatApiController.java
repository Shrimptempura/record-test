package com.mytest.oplib.seat.controller;

import com.mytest.oplib.seat.dto.SeatSnapshotView;
import com.mytest.oplib.seat.service.SeatQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Seats", description = "실시간 좌석 조회 API")
@RestController
@RequestMapping("/api/v1/seats")
@RequiredArgsConstructor
public class SeatApiController {

    private final SeatQueryService query;

    @Operation(
            summary = "좌석 스냅샷 목록 조회",
            description = "도서관 이름/지역/표준 코드로 필터링 하여 좌석 스냅샷을 페이지 단위로 조회"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "정상 조회",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PageResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "잘못된 요청", content = @Content),
            @ApiResponse(responseCode = "500", description = "서버 오류", content = @Content)
    })

    @GetMapping
    public PageResponse<SeatSnapshotView> list(
            @Parameter(description = "도서관/열람실 이름 키워드", example = "중앙도서관")
            @RequestParam(required = false) String name,

            @Parameter(description = "지역명(시/군/구) 키워드", example = "서울")
            @RequestParam(required = false) String region,

            @Parameter(description = "행정 표준 코드(보강 코드)", example = "2345000000")
            @RequestParam(required = false) String stdgCd,

            @Parameter(description = "페이지 번호(1-base)", example = "1")
            @RequestParam(defaultValue = "1") int page,

            @Parameter(description = "페이지 크기(최대: 500)", example = "20")
            @RequestParam(defaultValue = "20") int size
    ) {
        int safeSize = Math.max(1, Math.min(size, 500));
        int total = query.count(stdgCd, name, region);
        int totalPages = Math.max(1, (total + safeSize - 1) / safeSize);
        int safePage = Math.min(Math.max(1, page), totalPages);

        List<SeatSnapshotView> items = query.search(stdgCd, name, region, safePage, safeSize);
        return new PageResponse<>(items, safePage, safeSize, total, totalPages);
    }

    @Schema(description = "페이지 응답 래퍼")
    public record PageResponse<T>(
            @Schema(description = "아이템 목록")
            List<T> items,

            @Schema(description = "현재 페이지(1-base)", example = "1")
            int page,

            @Schema(description = "페이지 크기", example = "20")
            int size,

            @Schema(description = "총 건수", example = "221")
            int total,

            @Schema(description = "총 페이지 수", example = "10")
            int totalPages
    ) {}


}


