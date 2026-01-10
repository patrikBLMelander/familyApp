package com.familyapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FamilyAppApplication {

    public static void main(String[] args) {
        System.out.println("=== FamilyApp Backend Starting - XP System: 24 XP per level (5 levels) ===");
        SpringApplication.run(FamilyAppApplication.class, args);
    }
}



