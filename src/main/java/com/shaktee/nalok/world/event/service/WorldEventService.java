package com.shaktee.nalok.world.event.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shaktee.nalok.world.event.WorldEvent;
import com.shaktee.nalok.world.event.WorldEventKind;
import com.shaktee.nalok.world.event.repository.WorldEventRepository;
import com.shaktee.nalok.world.reference.EntityReference;
import com.shaktee.nalok.world.reference.EntityType;

/**
 * Appends immutable world events and reads them back bounded. The record
 * operations join the CALLER's transaction (REQUIRED): this is the E01a
 * atomicity guarantee — a state change and its event commit or roll back
 * together, so a rolled-back operation never leaves an event behind and a
 * committed one is never described by a missing event.
 */
@Service
public class WorldEventService {

    private static final int MAX_PAGE_SIZE = 100;

    private final WorldEventRepository eventRepository;
    private final Clock clock;

    public WorldEventService(WorldEventRepository eventRepository, Clock clock) {
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    @Transactional
    public WorldEvent record(WorldEventKind kind, String description) {
        return record(kind, description, null);
    }

    /**
     * Appends one event as part of the caller's transaction. Callers must
     * invoke this only AFTER the state change they describe has succeeded —
     * events record committed facts, not attempted ones.
     */
    @Transactional
    public WorldEvent record(WorldEventKind kind, String description,
            EntityReference target) {
        return eventRepository.save(new WorldEvent(kind, description, target,
                clock.instant()));
    }

    /**
     * Bounded, newest-first event page. Both filters optional; target means
     * type AND id (half-queries are rejected by the API boundary).
     */
    @Transactional(readOnly = true)
    public Page<WorldEvent> listEvents(WorldEventKind kind, EntityReference target,
            int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pageRequest = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.asc("id")));

        if (kind == null && target == null) {
            return eventRepository.findAll(pageRequest);
        }
        if (kind == null) {
            return eventRepository.findByTarget_EntityTypeAndTarget_ReferenceId(
                    target.getEntityType(), target.getReferenceId(), pageRequest);
        }
        if (target == null) {
            return eventRepository.findByKind(kind, pageRequest);
        }
        return eventRepository.findByKindAndTarget_EntityTypeAndTarget_ReferenceId(
                kind, target.getEntityType(), target.getReferenceId(), pageRequest);
    }

    /**
     * Builds a target reference after both-or-neither validation at the
     * boundary; never construct half-references.
     */
    public static EntityReference requireTarget(EntityType entityType, UUID targetId) {
        return EntityReference.of(entityType, targetId);
    }
}
