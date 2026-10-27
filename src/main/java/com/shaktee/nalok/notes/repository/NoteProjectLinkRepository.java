package com.shaktee.nalok.notes.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shaktee.nalok.notes.model.NoteProjectLink;

public interface NoteProjectLinkRepository extends JpaRepository<NoteProjectLink, UUID> {

    Optional<NoteProjectLink> findByNote_IdAndProject_Id(UUID noteId, UUID projectId);

    List<NoteProjectLink> findByNote_IdOrderByLinkedAtAsc(UUID noteId);

    List<NoteProjectLink> findByProject_IdOrderByLinkedAtAsc(UUID projectId);
}
