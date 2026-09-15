package com.evergarden.evergardenbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** {@code @EnableScheduling} — {@code EditorPresenceScheduler}가 끊긴 편집 세션을 정리한다(ARCH-12). */
@EnableScheduling
@SpringBootApplication
public class EvergardenBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(EvergardenBackendApplication.class, args);
    }

}
