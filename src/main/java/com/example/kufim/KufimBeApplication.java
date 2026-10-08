package com.example.kufim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KufimBeApplication {
    public static void main(String[] args) {
        SpringApplication.run(KufimBeApplication.class, args);
    }
}
