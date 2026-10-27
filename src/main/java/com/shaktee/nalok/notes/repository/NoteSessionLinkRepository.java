package com.shaktee.nalok.notes.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shaktee.nalok.notes.model.NoteSessionLink;

public interface NoteSessionLinkRepository extends JpaRepository<NoteSessionLink, UUID> {

    Optional<NoteSessionLink> findByNote_IdAndSession_Id(UUID noteId, UUID sessionId);

    List<NoteSessionLink> findByNote_IdOrderByLinkedAtAsc(UUID noteId);

    List<NoteSessionLink> findBySession_IdOrderByLinkedAtAsc(UUID sessionId);
}
