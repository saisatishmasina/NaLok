package com.shaktee.nalok.projects.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

import java.time.Instant;

@Getter
@Entity
@Table(name = "projects")
public class Project {

    // Fields
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    // Constructors
    protected Project() {

    }

    public Project(String title, String description) {
        this.id = UUID.randomUUID();
        this.title = validateTitle(title);
        this.description = description;
        this.status = ProjectStatus.PLANNED;
        this.createdAt = Instant.now();
    }

    //   methods
    public void rename(String title) {
        this.title = validateTitle(title);
    }

    public void updateDescription(String description) {
        this.description = description;
    }

    public void start() {
        if (status != ProjectStatus.PLANNED && status != ProjectStatus.PAUSED) {
            throw new IllegalStateException("only planned or paused projects can be started");
        }

        this.status = ProjectStatus.ACIVE;
    }

    public void pause() {
        if (status != ProjectStatus.ACIVE) {
            throw new IllegalStateException("only active projects can be paused");
        }

        this.status = ProjectStatus.PAUSED;
    }

    public void complete() {
        if (status != ProjectStatus.ACIVE) {
            throw new IllegalStateException("only active projects can be completed");
        }

        this.status = ProjectStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void archive() {
        if (status == ProjectStatus.ARCHIVED) {
            throw new IllegalStateException("project is already archived");
        }

        this.status = ProjectStatus.ARCHIVED;
    }

    private static String validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Project title cannot be empty");
        }

        String cleanedTitle = title.strip();

        if (cleanedTitle.length() > 200) {
            throw new IllegalArgumentException("Project title cannot exceed 200 characters");
        }

        return cleanedTitle;
    }

}
