package com.shaktee.nalok.notes.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.shaktee.nalok.projects.model.Project;

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
 * Association note ↔ project, with a database-enforced foreign key on both
 * sides and a unique constraint against duplicate links. The link owns only
 * itself: unlinking removes this row and nothing else — never the note and
 * never the project. linkedAt records when the association was made; it is
 * NOT authorship provenance.
 */
@Getter
@Entity
@Table(name = "note_project_links", uniqueConstraints = @UniqueConstraint(
        name = "uk_note_project_links",
        columnNames = { "note_id", "project_id" }))
public class NoteProjectLink {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "note_id", nullable = false, updatable = false)
    private Note note;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    @Column(name = "linked_at", nullable = false, updatable = false)
    private Instant linkedAt;

    protected NoteProjectLink() {
    }

    public NoteProjectLink(Note note, Project project, Instant linkedAt) {
        if (note == null || project == null) {
            throw new IllegalArgumentException("link requires its note and project");
        }
        this.id = UUID.randomUUID();
        this.note = note;
        this.project = project;
        this.linkedAt = Objects.requireNonNull(linkedAt, "link time is required");
    }
}
