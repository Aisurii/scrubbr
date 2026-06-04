package com.scrubbr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Scrubbr — reveals and removes hidden metadata and sensitive data from images and PDFs. */
@SpringBootApplication
public class ScrubbrApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScrubbrApplication.class, args);
    }
}
