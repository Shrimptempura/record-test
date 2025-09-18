package com.mytest.oplib.best.controller;

import com.mytest.oplib.best.dto.BookBestRow;
import com.mytest.oplib.best.dto.BookSort;
import com.mytest.oplib.best.service.BusanBestQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookBestRestController.class)
class BookBestRestControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    BusanBestQueryService queryService;

    @Test
    void 인기대출도서_성공_리턴200() throws Exception {

        given(queryService.parseOrderOrDefault(anyString()))
                .willReturn(BookSort.RANK_ASC);
        given(queryService.page(anyInt(), anyInt(), any(BookSort.class), any(), any()))
                .willReturn(List.of(new BookBestRow(
                        100L, 1, "테스트 제목", "테스트 저자", "테스트도서관",
                        "https://abc.com/cover.jpg", 2025, null
                )));
        given(queryService.count(any(), any()))
                .willReturn(1);

        mockMvc.perform(get("/api/v1/books")
                        .param("pageNo", "1")
                        .param("numOfRows", "20")
                        .param("sort", "RANK_ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("테스트 제목"))
                .andExpect(jsonPath("$.items[0].author").value("테스트 저자"))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.sort").value("RANK_ASC"));
    }

    @Test
    void 인기대출도서_실패_페이지번호0이면400리턴() throws Exception {
        mockMvc.perform(get("/api/v1/books")
                        .param("pageNo", "0")
                        .param("numOfRows", "20"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 인기대출도서_실패_페이지크기101이면400리턴() throws Exception {
        mockMvc.perform(get("/api/v1/books")
                        .param("pageNo", "1")
                        .param("numOfRows", "101"))
                .andExpect(status().isBadRequest());
    }
}