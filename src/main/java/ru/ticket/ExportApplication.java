package ru.ticket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Locale;

@SpringBootApplication
public class ExportApplication {
    public static void main(String[] args) {
        Locale.setDefault(Locale.ROOT);
        SpringApplication.run(ExportApplication.class, args);
    }
}