package com.shaktee.nalok;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.shaktee.nalok.world.event.WorldEvent;

import java.time.Clock;

@SpringBootApplication
public class NalokApplication {

    public static void main(String[] args) {
        SpringApplication.run(NalokApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    CommandLineRunner firstWorldEvent() {
        return args -> {
            WorldEvent prakriya = WorldEvent.record("Started Building my Naa Lokam as NaLok");

            System.out.println(prakriya);
        };
    }
}
