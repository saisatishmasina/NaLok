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
import com.shaktee.nalok.notes.model.NoteProjectLink;

public interface NoteProjectLinkRepository extends JpaRepository<NoteProjectLink, UUID> {

    Optional<NoteProjectLink> findByNote_IdAndProject_Id(UUID noteId, UUID projectId);

    List<NoteProjectLink> findByNote_IdOrderByLinkedAtAsc(UUID noteId);

    List<NoteProjectLink> findByProject_IdOrderByLinkedAtAsc(UUID projectId);

    /**
     * Bounded project-wide note page: direct project links PLUS section
     * links (through the section's parent project), deduplicated by the
     * database and paged with a deterministic sort.
     */
    @Query("""
            select n from Note n
            where n.id in (select l.note.id from NoteProjectLink l
                           where l.project.id = :projectId)
               or n.id in (select l.note.id from NoteSectionLink l
                           where l.section.project.id = :projectId)
            """)
    Page<Note> findNotesForProject(@Param("projectId") UUID projectId, Pageable pageable);
}
