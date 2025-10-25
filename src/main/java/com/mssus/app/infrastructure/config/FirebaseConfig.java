package com.mssus.app.infrastructure.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.*;
import java.nio.charset.StandardCharsets;

@Configuration
@Slf4j
public class FirebaseConfig {
    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        String prodSecretPath = "/etc/secrets/mssus-fcm-firebase-adminsdk-fbsvc-938443350c.json";
        String secretPath = "mssus-fcm-firebase-adminsdk-fbsvc-938443350c.json";

        InputStream credentialsStream = null;

        File prodFile = new File(prodSecretPath);
        if (prodFile.exists()) {
            credentialsStream = new FileInputStream(prodFile);
            log.info("Loaded Firebase credentials from production path: {}", prodSecretPath);
        } else {
            ClassPathResource classPathResource = new ClassPathResource(secretPath);
            if (classPathResource.exists()) {
                credentialsStream = classPathResource.getInputStream();
                log.info("Loaded Firebase credentials from classpath: {}", secretPath);
            }
        }

        if (credentialsStream == null) {
            throw new FileNotFoundException("Firebase credentials not found in any configured location");
        }

        FirebaseOptions options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(credentialsStream))
            .build();

        FirebaseApp app = FirebaseApp.initializeApp(options);
        log.info("Firebase initialized successfully");
        return app;
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}
