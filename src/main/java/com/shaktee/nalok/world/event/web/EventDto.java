package com.shaktee.nalok.world.event.web;

import java.time.Instant;
import java.util.UUID;

import com.shaktee.nalok.world.event.WorldEvent;
import com.shaktee.nalok.world.event.WorldEventKind;
import com.shaktee.nalok.world.reference.EntityReference;

/**
 * Bounded API representation of one world event. The target appears only
 * when the event has one; there is no half-reference.
 */
public record EventDto(
        UUID id,
        WorldEventKind kind,
        String description,
        Instant occurredAt,
        TargetDto target) {

    public record TargetDto(String entityType, UUID referenceId) {
    }

    public static EventDto of(WorldEvent event) {
        EntityReference target = event.getTarget();
        return new EventDto(event.getId(), event.getKind(), event.getDescription(),
                event.getOccurredAt(),
                target == null ? null : new TargetDto(
                        target.getEntityType().name(), target.getReferenceId()));
    }
}
