package com.shaktee.nalok.world.event;

import java.time.Instant;
import java.util.UUID;

public record WorldEvent(
        UUID id, Instant occuredAt, String description) {
    public static WorldEvent record(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException(
                    "Event Description cannot be empty.");
        }

        return new WorldEvent(UUID.randomUUID(), Instant.now(), description.strip());
    }
}
