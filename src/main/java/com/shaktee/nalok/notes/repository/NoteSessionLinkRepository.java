package com.shaktee.nalok.notes.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shaktee.nalok.notes.model.Note;
import com.shaktee.nalok.notes.model.NoteSessionLink;

public interface NoteSessionLinkRepository extends JpaRepository<NoteSessionLink, UUID> {

    Optional<NoteSessionLink> findByNote_IdAndSession_Id(UUID noteId, UUID sessionId);

    List<NoteSessionLink> findByNote_IdOrderByLinkedAtAsc(UUID noteId);

    List<NoteSessionLink> findBySession_IdOrderByLinkedAtAsc(UUID sessionId);

    /** Bounded page of a session's notes (Note-rooted so Note sorting works). */
    @Query("""
            select n from Note n
            where n.id in (select l.note.id from NoteSessionLink l
                           where l.session.id = :sessionId)
            """)
    Page<Note> findNotesForSession(@Param("sessionId") UUID sessionId, Pageable pageable);
}
