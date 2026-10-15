package com.schwab.agentic.shortener.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ShortenerConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
