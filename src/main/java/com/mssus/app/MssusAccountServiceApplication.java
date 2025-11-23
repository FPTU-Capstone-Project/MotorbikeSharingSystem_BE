package com.mssus.app;

import com.mssus.app.appconfig.config.properties.AiConfigurationProperties;
import com.mssus.app.appconfig.config.properties.RideConfigurationProperties;
import com.mssus.app.appconfig.config.properties.SosConfigurationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableConfigurationProperties({
    RideConfigurationProperties.class, 
    SosConfigurationProperties.class,
    AiConfigurationProperties.class
})
@EnableScheduling
public class MssusAccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MssusAccountServiceApplication.class, args);
    }

}
