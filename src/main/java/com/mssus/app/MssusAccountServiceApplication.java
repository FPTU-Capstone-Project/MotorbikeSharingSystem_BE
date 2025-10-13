package com.mssus.app;

import com.mssus.app.config.properties.RideConfigurationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
@EnableConfigurationProperties({RideConfigurationProperties.class})
public class MssusAccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MssusAccountServiceApplication.class, args);
    }

}
