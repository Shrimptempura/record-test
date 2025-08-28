//package com.mytest.oplib.controller;
//
//import com.mytest.oplib.dto.BookBestResponse;
//import com.mytest.oplib.dto.BookBestView;
//import com.mytest.oplib.service.BusanBestService;
//import lombok.RequiredArgsConstructor;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RequestParam;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.util.List;
//
//@RestController
//@RequiredArgsConstructor
//@RequestMapping("/api/busan/weekly-best")
//public class BookBestController {
//
//    private final BusanBestService busanBestService;
//
//    /**
//     * 원문 구조 그대로 반환 (디버그/검증용)
//     * 예) GET /api/busan/weekly-best/raw?pageNo=1&numOfRows=10
//     */
//    @GetMapping("/raw")
//    public BookBestResponse raw(
//            @RequestParam(defaultValue = "1") int pageNo,
//            @RequestParam(defaultValue = "10") int numOfRows
//    ) {
//        return busanBestService.fetch(pageNo, numOfRows, null, null);
//    }
//
//    /**
//     * 프런트에서 바로 쓰기 쉬운 요약 리스트
//     * 예) GET /api/busan/weekly-best?pageNo=1&numOfRows=10
//     */
//    @GetMapping
//    public List<BookBestView> list(
//            @RequestParam(defaultValue = "1") int pageNo,
//            @RequestParam(defaultValue = "10") int numOfRows
//    ) {
//        return busanBestService.fetchSimple(pageNo, numOfRows);
//    }
//
//    // 홈 첫 화면 전용: 상위 12개 고정
//    @GetMapping("/top")
//    public List<BookBestView> top() {
//        return busanBestService.fetchSimple(1, 12);
//    }
//}
