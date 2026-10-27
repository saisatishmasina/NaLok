package com.shaktee.nalok.knowledge.study.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.shaktee.nalok.knowledge.study.model.StudySession;
import com.shaktee.nalok.knowledge.study.model.StudySessionStatus;
import com.shaktee.nalok.world.reference.EntityType;

public interface StudySessionRepository extends JpaRepository<StudySession, UUID> {

    List<StudySession> findByStatus(StudySessionStatus status);

    Page<StudySession> findByStatus(StudySessionStatus status, Pageable pageable);

    /**
     * Sessions whose context points at one reference (F08 section workbench).
     * Context is optional on the entity (null = standalone), so rows without
     * a context simply never match — embedded-null comparisons are excluded,
     * which is exactly the wanted semantics. Derived query keeps the ordering
     * in the service (newest first, stable id tie-break, same as history).
     */
    Page<StudySession> findByContext_EntityTypeAndContext_ReferenceId(
            EntityType entityType, UUID referenceId, Pageable pageable);
}
