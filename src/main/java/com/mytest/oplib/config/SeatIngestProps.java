package com.mytest.oplib.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "seat.ingest")
public class SeatIngestProps {
    boolean enabled;
    String cron;
    int numOfRows;
    List<String> targets;
}
