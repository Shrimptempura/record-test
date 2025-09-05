package com.mytest.oplib.controller;

import com.mytest.oplib.dto.SeatSnapshotView;
import com.mytest.oplib.service.SeatIngestService;
import com.mytest.oplib.service.SeatQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * GET /seats : (1) 데이터 없으면 자동 전량 수집 → (2) 검색+페이징 조회 → (3) 화면 표출
 * - totalCount/totalPages/hasPrev/hasNext 제공
 * - ingest/debug 엔드포인트 제거
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/seats")
public class SeatPageController {

    private final SeatQueryService queryService;
    private final SeatIngestService ingestService;

    @GetMapping
    public String list(@RequestParam(required = false) String name,
                       @RequestParam(required = false) String region,
                       @RequestParam(required = false) String stdgCd,
                       @RequestParam(defaultValue = "1") Integer page,   // 1-base
                       @RequestParam(defaultValue = "20") Integer size,   // page size (UI에서 늘리고 싶으면 조절)
                       Model model) {
        ingestService.ingestAllAuto(100, 5000);

        // 1) 총 건수(검색 반영)
        int total = queryService.count(stdgCd, name, region);
        int safeMax = 500;

        // 2) 페이징 보정
        int safeSize = Math.max(1, Math.min(size, 500)); // 과도한 1페이지 폭은 제한(원하면 더 키워도 됨)
        int totalPages = Math.max(1, (total + safeSize - 1) / safeSize);
        int safePage = Math.min(Math.max(1, page), totalPages);

        // 3) 목록 조회
        List<SeatSnapshotView> items = queryService.search(stdgCd, name, region, safePage, safeSize);

        // 4) 모델 구성 (총 페이지/이전다음/검색값 유지)
        boolean hasPrev = safePage > 1;
        boolean hasNext = safePage < totalPages;

        model.addAttribute("items", items);
        model.addAttribute("page", safePage);
        model.addAttribute("size", safeSize);
        model.addAttribute("total", total);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("hasPrev", hasPrev);
        model.addAttribute("hasNext", hasNext);
        model.addAttribute("prevPage", hasPrev ? safePage - 1 : 1);
        model.addAttribute("nextPage", hasNext ? safePage + 1 : safePage);

        model.addAttribute("name", name);
        model.addAttribute("region", region);

        return "seats";
    }

//    // SeatPageController.java
//    @GetMapping(value = "/debug-fetch", produces = "text/plain; charset=UTF-8")
//    @ResponseBody
//    public String debugFetch(@RequestParam(required = false) String pblibId,
//                             @RequestParam(required = false) String rdrmId,
//                             @RequestParam(defaultValue = "1") Integer pageNo,
//                             @RequestParam(defaultValue = "100") Integer numOfRows) {
//        String raw = ingestService.debugFetchRaw(pblibId, rdrmId, pageNo, numOfRows);
//        if (raw == null) return "len=0\nfirst1000=\n";
//        int len = raw.length();
//        int head = Math.min(1000, len);
//        return "len=" + len + "\nfirst1000=\n" + raw.substring(0, head);
//    }

}


