package com.mytest.oplib.job;

import com.mytest.oplib.service.BusanBestIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@RequiredArgsConstructor
public class BookBestScheduler {

    private final BusanBestIngestService ingestService;

    // 매주 월요일 스케쥴러 (03시)
    @Scheduled(cron = "0 0 3 * * MON")
    public void refreshWeekly() {
        log.info("BookBestScheduler - 주간 인기 대출 도서 갱신 시작");

        ingestService.refreshWeekly(null, null);
        log.info("BookBestScheduler - 주간 인기 대출 도서 갱신 완료");
    }

}
