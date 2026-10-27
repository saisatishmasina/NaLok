package com.shaktee.nalok.world.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.shaktee.nalok.world.reference.EntityReference;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * One immutable world-history fact: something happened at an instant, to an
 * optional target. This is NOT event sourcing — events describe committed
 * state changes and are never replayed to rebuild state. There is no
 * version and no update path: an event row is written once, in the same
 * transaction as the change it describes, and never edited afterwards.
 *
 * The optional target reuses the shared EntityReference embeddable
 * (both-or-neither; polymorphic, so no FK — a rolled-back event never
 * references anything because the whole transaction rolls back together).
 */
@Getter
@Entity
@Table(name = "world_events")
public class WorldEvent {

    public static final int MAX_DESCRIPTION_LENGTH = 500;

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40)
    private WorldEventKind kind;

    @Column(nullable = false, updatable = false, length = 500)
    private String description;

    /** Optional subject of the event; both-or-neither (EntityReference). */
    @Embedded
    private EntityReference target;

    protected WorldEvent() {
    }

    public WorldEvent(WorldEventKind kind, String description,
            EntityReference target, Instant occurredAt) {
        this.id = UUID.randomUUID();
        this.kind = Objects.requireNonNull(kind, "event kind is required");
        this.description = validateDescription(description);
        this.target = target;
        this.occurredAt = Objects.requireNonNull(occurredAt, "event time is required");
    }

    private static String validateDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Event description cannot be empty");
        }
        String cleaned = description.strip();
        if (cleaned.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException(
                    "Event description cannot exceed " + MAX_DESCRIPTION_LENGTH + " characters");
        }
        return cleaned;
    }
}
