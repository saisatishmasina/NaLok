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
import com.shaktee.nalok.notes.model.NoteSourceLink;

public interface NoteSourceLinkRepository extends JpaRepository<NoteSourceLink, UUID> {

    Optional<NoteSourceLink> findByNote_IdAndSource_Id(UUID noteId, UUID sourceId);

    List<NoteSourceLink> findByNote_IdOrderByLinkedAtAsc(UUID noteId);

    List<NoteSourceLink> findBySource_IdOrderByLinkedAtAsc(UUID sourceId);

    /** Bounded page of a source's notes (Note-rooted so Note sorting works). */
    @Query("""
            select n from Note n
            where n.id in (select l.note.id from NoteSourceLink l
                           where l.source.id = :sourceId)
            """)
    Page<Note> findNotesForSource(@Param("sourceId") UUID sourceId, Pageable pageable);
}
