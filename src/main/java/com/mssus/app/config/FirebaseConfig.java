package com.mssus.app.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.*;
import java.nio.charset.StandardCharsets;

@Configuration
@Slf4j
public class FirebaseConfig {

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        String firebaseCredentials = System.getenv("FIREBASE_CREDENTIALS");

        String firebasePath = System.getenv("FIREBASE_CREDENTIALS_PATH");

        String defaultSecretPath = "/etc/secrets/mssus-fcm-firebase-adminsdk-fbsvc-938443350c.json";

        InputStream credentialsStream = null;

        if (firebaseCredentials != null && !firebaseCredentials.isBlank()) {
            log.info("Loading Firebase credentials from environment variable FIREBASE_CREDENTIALS");
            credentialsStream = new ByteArrayInputStream(firebaseCredentials.getBytes(StandardCharsets.UTF_8));

        } else if (firebasePath != null && !firebasePath.isBlank()) {
            log.info("Loading Firebase credentials from path: {}", firebasePath);
            credentialsStream = new FileInputStream(firebasePath);

        } else {
            File defaultFile = new File(defaultSecretPath);
            if (defaultFile.exists()) {
                log.info("Loading Firebase credentials from Render secret file: {}", defaultSecretPath);
                credentialsStream = new FileInputStream(defaultFile);
            } else {
                throw new FileNotFoundException("Firebase credentials not found in environment variables or secret files.");
            }
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
