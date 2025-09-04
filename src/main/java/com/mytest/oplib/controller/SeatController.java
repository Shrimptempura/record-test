//package com.mytest.oplib.controller;
//
//import com.mytest.oplib.dto.SeatSnapshotView;
//import com.mytest.oplib.service.SeatIngestService;
//import com.mytest.oplib.service.SeatQueryService;
//import lombok.RequiredArgsConstructor;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.List;
//
///**
// * REST 전환 컨트롤러
// * - /api/seats/ingest : 외부 -> raw 저장 -> materialize 수정
// * - /api/seat/rooms/{...} : 좌석 현황 조회 : PK 조회
// * - /api/seat/rooms : 검색 + 페이징
// */
//@RequiredArgsConstructor
//@RestController
//@RequestMapping("/api/seats")
//public class SeatController {
//
//    private final SeatIngestService ingestService;
//    private final SeatQueryService queryService;
//
//    // 수동 수집 & materialize 트리거 (스케줄러 전 테스트용)
//    @PostMapping("/ingest")
//    public ResponseEntity<?> ingest(@RequestParam String pblibId,
//                                    @RequestParam String rdrmId,
//                                    @RequestParam(required = false) Integer pageNo,
//                                    @RequestParam(required = false) Integer numOfRows) {
//        int affected = ingestService.ingestAndMaterialize(pblibId, rdrmId, pageNo, numOfRows);
//        return ResponseEntity.ok().body(
//                new Result("OK", affected)
//        );
//    }
//
//    // PK 단건 조회
//    @GetMapping("/rooms/{pblibId}/{rdrmId}")
//    public ResponseEntity<SeatSnapshotView> getByKey(@PathVariable String pblibId,
//                                                     @PathVariable String rdrmId) {
//        SeatSnapshotView item = queryService.getByKey(pblibId, rdrmId);
//        return ResponseEntity.ok(item);
//    }
//
//    // 검색 + 페이징
//    @GetMapping("/rooms")
//    public ResponseEntity<List<SeatSnapshotView>> search(@RequestParam(required = false) String name,
//                                                         @RequestParam(required = false) String region,
//                                                         @RequestParam(defaultValue = "1") Integer page,
//                                                         @RequestParam(defaultValue = "20") Integer size) {
//        return ResponseEntity.ok(queryService.search(name, region, page, size));
//    }
//
//    // 단순 응답용 dto (inner class)
//    private record Result(String status, int affected) {}
//}
