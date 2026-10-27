package com.shaktee.nalok.notes.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.shaktee.nalok.projects.model.ProjectSection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

/**
 * Association note ↔ project section. Same rules as NoteProjectLink: FK
 * integrity, unique (note, section), unlink-safe, linkedAt is not
 * authorship.
 */
@Getter
@Entity
@Table(name = "note_section_links", uniqueConstraints = @UniqueConstraint(
        name = "uk_note_section_links",
        columnNames = { "note_id", "section_id" }))
public class NoteSectionLink {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "note_id", nullable = false, updatable = false)
    private Note note;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false, updatable = false)
    private ProjectSection section;

    @Column(name = "linked_at", nullable = false, updatable = false)
    private Instant linkedAt;

    protected NoteSectionLink() {
    }

    public NoteSectionLink(Note note, ProjectSection section, Instant linkedAt) {
        if (note == null || section == null) {
            throw new IllegalArgumentException("link requires its note and section");
        }
        this.id = UUID.randomUUID();
        this.note = note;
        this.section = section;
        this.linkedAt = Objects.requireNonNull(linkedAt, "link time is required");
    }
}
