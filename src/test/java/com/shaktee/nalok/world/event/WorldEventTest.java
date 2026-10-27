package com.shaktee.nalok.world.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.shaktee.nalok.world.reference.EntityReference;
import com.shaktee.nalok.world.reference.EntityType;

/**
 * Model-level rules for world events: immutable facts need a kind, a real
 * description and an instant; the optional target is both-or-neither.
 */
class WorldEventTest {

    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");

    @Test
    void descriptionIsRequiredStrippedAndCapped() {
        assertThatThrownBy(() -> new WorldEvent(WorldEventKind.WORLD_STARTED, null, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WorldEvent(WorldEventKind.WORLD_STARTED, "   ", null, NOW))
                .isInstanceOf(IllegalArgumentException.class);

        WorldEvent event = new WorldEvent(WorldEventKind.WORLD_STARTED, "  began  ", null, NOW);
        assertThat(event.getDescription()).isEqualTo("began");
        assertThat(event.getOccurredAt()).isEqualTo(NOW);

        assertThatThrownBy(() -> new WorldEvent(WorldEventKind.WORLD_STARTED,
                "d".repeat(501), null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void targetIsOptionalButNeverHalf() {
        // Absent whole target: standalone event.
        WorldEvent standalone = new WorldEvent(WorldEventKind.WORLD_STARTED, "began", null, NOW);
        assertThat(standalone.getTarget()).isNull();

        // Present whole target is accepted.
        EntityReference session = EntityReference.of(EntityType.STUDY_SESSION,
                UUID.randomUUID());
        WorldEvent targeted = new WorldEvent(WorldEventKind.STUDY_SESSION_STARTED,
                "started", session, NOW);
        assertThat(targeted.getTarget()).isSameAs(session);

        // Half-reference cannot even be constructed (EntityReference.of).
        assertThatThrownBy(() -> EntityReference.of(EntityType.STUDY_SESSION, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
