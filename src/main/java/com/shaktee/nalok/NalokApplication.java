package com.shaktee.nalok;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.shaktee.nalok.world.event.WorldEvent;
import com.shaktee.nalok.world.event.WorldEventKind;
import com.shaktee.nalok.world.event.repository.WorldEventRepository;

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

    /**
     * The first world event, persisted once — an empty history still says
     * when this world began. Idempotent: only when no events exist at all.
     */
    @Bean
    CommandLineRunner firstWorldEvent(WorldEventRepository events, Clock clock) {
        return args -> {
            if (events.count() == 0) {
                events.save(new WorldEvent(WorldEventKind.WORLD_STARTED,
                        "Started building NaLok", null, clock.instant()));
            }
        };
    }
}
