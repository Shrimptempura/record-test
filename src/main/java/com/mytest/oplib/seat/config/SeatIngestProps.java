package com.mytest.oplib.seat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "seat.ingest")
public record SeatIngestProps(
        boolean enabled,
        String cron,
        int numOfRows,
        int maxPages
) { }
