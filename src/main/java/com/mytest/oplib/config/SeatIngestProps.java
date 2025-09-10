package com.mytest.oplib.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "seat.ingest")
public record SeatIngestProps(
        boolean enabled,
        String cron,
        int numOfRows,
        int maxPages
) { }
