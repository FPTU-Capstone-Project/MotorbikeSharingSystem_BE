package com.mssus.app.infrastructure.config;

import com.mssus.app.service.RoutingService;
import com.mssus.app.service.impl.GoongRoutingServiceImpl;
//import com.mssus.app.service.impl.OsrmRoutingServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RoutingConfig {
    @Value("${routing.provider:osrm}") String provider;
    @Bean
    public RoutingService routingProvider(/*OsrmRoutingServiceImpl osrm, */GoongRoutingServiceImpl goong) {
        return goong;
    }
}

