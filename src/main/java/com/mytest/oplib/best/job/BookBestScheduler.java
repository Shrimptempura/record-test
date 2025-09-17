package com.mytest.oplib.best.job;

import com.mytest.oplib.best.service.BusanBestIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "bookbest.ingest", name = "enabled", havingValue = "true")
public class BookBestScheduler {

    private final BusanBestIngestService ingestService;

    // 매주 월요일 스케쥴러 (03시)
    @Scheduled(cron = "${bookbest.ingest.cron:0 0 3 * * MON}")
    public void run() {
        try {
            log.info("BookBestScheduler - 주간 인기 대출 도서 갱신 시작");
            ingestService.refreshWeekly(null, null);
            log.info("BookBestScheduler - 주간 인기 대출 도서 갱신 완료");
        } catch (Exception e) {
            log.error("BookBestScheduler - 주간 인기 대출 도서 갱신 중 오류 발생", e);
        }
    }

}
