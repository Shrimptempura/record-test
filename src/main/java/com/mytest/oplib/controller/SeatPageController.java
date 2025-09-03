package com.mytest.oplib.controller;

import com.mytest.oplib.dto.SeatSnapshotView;
import com.mytest.oplib.service.SeatIngestService;
import com.mytest.oplib.service.SeatQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 최종 테스트 버전
/**
 * Thymeleaf 기반 SSR 페이지 컨트롤러
 * - GET /seats : 목록 + 검색 + 페이징
 * 목록 페이징은 "다음 페이지가 있는지"만 판단(hasNext)한다.
 * totalCount가 필요하면 count 쿼리를 별도로 추가해야 함.
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
                       @RequestParam(defaultValue = "1") Integer page,   // 1-base
                       @RequestParam(defaultValue = "20") Integer size,   // page size
                       Model model) {

        // 서비스에서 limit/offset 가드 처리함 (size 상한 100)
        List<SeatSnapshotView> items = queryService.search(name, region, page, size);

        boolean hasPrev = page > 1;
        boolean hasNext = (items.size() == Math.min(size, 100)); // 더 있을 가능성 힌트

        model.addAttribute("items", items);
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("hasPrev", hasPrev);
        model.addAttribute("hasNext", hasNext);
        model.addAttribute("prevPage", hasPrev ? page - 1 : 1);
        model.addAttribute("nextPage", hasNext ? page + 1 : page);

        // 검색 파라미터 유지
        model.addAttribute("name", name);
        model.addAttribute("region", region);

        return "seats"; // templates/seats.html
    }

    // 브라우저에서 바로 수집 트리거할 수 있게 GET도 허용(연습/확인용)
    @GetMapping("/ingest")
    public String ingest(@RequestParam(required = false) String pblibId,
                         @RequestParam(required = false) String rdrmId,
                         @RequestParam(required = false) Integer pageNo,
                         @RequestParam(required = false) Integer numOfRows) {
        ingestService.ingestAndMaterialize(pblibId, rdrmId, pageNo, numOfRows); // null/null 허용
        return "redirect:/seats";
    }

    // 예: /seats/ingest-all?pageFrom=1&pageTo=30&size=100
    @GetMapping("/ingest-all")
    public String ingestAll(@RequestParam(defaultValue="1") Integer pageFrom,
                            @RequestParam(defaultValue="1") Integer pageTo,
                            @RequestParam(defaultValue="100") Integer size) {
        int total = 0;
        for (int p = pageFrom; p <= pageTo; p++) {
            total += ingestService.ingestAndMaterialize(null, null, p, size);
        }
        // 옵션: totalCount를 파싱해서 pageTo를 자동 계산하도록 개선 가능
        return "redirect:/seats";
    }


    // 임시: 원문 응답 디버그(키 없이도 호출)
    @GetMapping("/debug-fetch")
    @ResponseBody
    public String debugFetch(@RequestParam(required = false) String pblibId,
                             @RequestParam(required = false) String rdrmId,
                             @RequestParam(required = false) Integer pageNo,
                             @RequestParam(required = false) Integer numOfRows) {
        String raw = ingestService.debugFetchRaw(pblibId, rdrmId, pageNo, numOfRows);
        return "len=" + (raw == null ? 0 : raw.length())
                + "\nfirst300=\n" + (raw == null ? "null" : raw.substring(0, Math.min(300, raw.length())));
    }
}

