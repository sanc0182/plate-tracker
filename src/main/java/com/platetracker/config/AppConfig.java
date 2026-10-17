package com.platetracker.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /** One clock for the whole app so "time out"/"time in" are always in the dealership's zone. */
    @Bean
    public Clock clock(@Value("${app.timezone:America/Toronto}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
