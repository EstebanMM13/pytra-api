package com.estebanmm13.pytra_api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers {@link UtcDateTimeModule} on the auto-configured JSON mapper (module beans are picked up). */
@Configuration
public class JacksonConfig {

    @Bean
    UtcDateTimeModule utcDateTimeModule() {
        return new UtcDateTimeModule();
    }
}
