package com.buckshot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling   // ConnectionHeartbeat (끊김 감지 ping)
public class BuckShotRouletteServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(BuckShotRouletteServerApplication.class, args);
    }

}
