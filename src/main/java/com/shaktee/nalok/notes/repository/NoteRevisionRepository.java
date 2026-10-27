package com.shaktee.nalok.notes.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shaktee.nalok.notes.model.NoteRevision;

public interface NoteRevisionRepository extends JpaRepository<NoteRevision, UUID> {

    List<NoteRevision> findByNote_IdOrderByRevisionNumberDesc(UUID noteId);

    Optional<NoteRevision> findByIdAndNote_Id(UUID revisionId, UUID noteId);

    long countByNote_Id(UUID noteId);
}
