package com.shaktee.nalok.world.event.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.shaktee.nalok.world.event.WorldEvent;
import com.shaktee.nalok.world.event.WorldEventKind;
import com.shaktee.nalok.world.reference.EntityType;

public interface WorldEventRepository extends JpaRepository<WorldEvent, UUID> {

    Page<WorldEvent> findByKind(WorldEventKind kind, Pageable pageable);

    Page<WorldEvent> findByTarget_EntityTypeAndTarget_ReferenceId(
            EntityType entityType, UUID referenceId, Pageable pageable);

    Page<WorldEvent> findByKindAndTarget_EntityTypeAndTarget_ReferenceId(
            WorldEventKind kind, EntityType entityType, UUID referenceId,
            Pageable pageable);
}
