package com.shaktee.nalok.notes.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shaktee.nalok.notes.model.Note;
import com.shaktee.nalok.notes.model.NoteKind;

public interface NoteRepository extends JpaRepository<Note, UUID> {

    /**
     * Locking read for content-changing saves. Taking the note row lock
     * FIRST gives concurrent editors a deterministic lock order: without
     * it, InnoDB deadlocks when one transaction holds the note row while
     * the other holds the revision unique-index gap (both flush around the
     * same commit). With it, the loser waits here and then fails the
     * version predicate as a clean optimistic-lock conflict (HTTP 409).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Note n where n.id = :id")
    Optional<Note> findByIdForUpdate(@Param("id") UUID id);

    Page<Note> findByArchived(boolean archived, Pageable pageable);

    Page<Note> findByKind(NoteKind kind, Pageable pageable);

    Page<Note> findByKindAndArchived(NoteKind kind, boolean archived, Pageable pageable);
}
