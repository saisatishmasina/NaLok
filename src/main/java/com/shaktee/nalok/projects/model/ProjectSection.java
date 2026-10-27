package com.shaktee.nalok.projects.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

@Getter
@Entity
@Table(name = "project_sections")
public class ProjectSection {

    // Fields
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    /**
     * Concurrent-edit protection: a stale UI form that re-submits an edit
     * based on an older snapshot is rejected rather than overwrite newer
     * work (mirrors Project's boundary contract).
     */
    @Version
    @Column(nullable = false)
    private long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Constructors
    protected ProjectSection() {

    }

    public ProjectSection(
            Project project,
            String title,
            String description,
            int position) {
        this.id = UUID.randomUUID();
        this.project = Objects.requireNonNull(project, "Section must belong to a Project");
        this.title = validateTitle(title);
        this.description = description;
        changePosition(position);
        this.createdAt = Instant.now();
    }

    public void rename(String title) {
        this.title = validateTitle(title);
    }

    public void updateDescription(String description) {
        this.description = description;
    }

    public void changePosition(int position) {
        if (position < 0) {
            throw new IllegalArgumentException("Section position cannot be Negative");
        }

        this.position = position;
    }

    private static String validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Project section title cannot be empty");
        }

        String cleanedTitle = title.strip();

        if (cleanedTitle.length() > 200) {
            throw new IllegalArgumentException("section title cannot exceed the character lenght of 200");
        }

        return cleanedTitle;
    }

}
