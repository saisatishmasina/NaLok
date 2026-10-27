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
import com.shaktee.nalok.notes.model.NoteSectionLink;

public interface NoteSectionLinkRepository extends JpaRepository<NoteSectionLink, UUID> {

    Optional<NoteSectionLink> findByNote_IdAndSection_Id(UUID noteId, UUID sectionId);

    List<NoteSectionLink> findByNote_IdOrderByLinkedAtAsc(UUID noteId);

    List<NoteSectionLink> findBySection_IdOrderByLinkedAtAsc(UUID sectionId);

    /**
     * Section links by the section's parent project: this is how a
     * section-linked note becomes reachable project-wide without a
     * duplicate project link.
     */
    List<NoteSectionLink> findBySection_Project_IdOrderByLinkedAtAsc(UUID projectId);

    /** Bounded page of a section's notes (Note-rooted so Note sorting works). */
    @Query("""
            select n from Note n
            where n.id in (select l.note.id from NoteSectionLink l
                           where l.section.id = :sectionId)
            """)
    Page<Note> findNotesForSection(@Param("sectionId") UUID sectionId, Pageable pageable);
}
