package com.windowsense;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class WindowSenseApplication {

    public static void main(String[] args) {
        System.setProperty("debug", System.getProperty("debug", "false"));
        System.setProperty("logging.level.org.springframework", System.getProperty("logging.level.org.springframework", "INFO"));
        SpringApplication.run(WindowSenseApplication.class, args);
    }
}
