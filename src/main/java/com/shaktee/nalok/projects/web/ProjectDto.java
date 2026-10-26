package com.shaktee.nalok.projects.web;

import java.time.Instant;
import java.util.UUID;

import com.shaktee.nalok.projects.model.Project;
import com.shaktee.nalok.projects.model.ProjectStatus;

/**
 * Bounded API representation of a project. The persisted enum constant is
 * misspelled ACIVE (stored data keeps it); the API boundary presents the
 * correct ACTIVE spelling.
 */
public record ProjectDto(
        UUID id,
        String title,
        String description,
        String status,
        Instant createdAt,
        Instant completedAt) {

    public static final String ACTIVE_API_NAME = "ACTIVE";

    public static ProjectDto of(Project project) {
        return new ProjectDto(
                project.getId(),
                project.getTitle(),
                project.getDescription(),
                apiStatus(project.getStatus()),
                project.getCreatedAt(),
                project.getCompletedAt());
    }

    private static String apiStatus(ProjectStatus status) {
        return status == ProjectStatus.ACIVE
                ? ACTIVE_API_NAME
                : status.name();
    }

    /**
     * Maps an API status string (including the corrected ACTIVE) back to the
     * persisted enum.
     */
    public static ProjectStatus fromApiStatus(String apiStatus) {
        if (ACTIVE_API_NAME.equals(apiStatus)) {
            return ProjectStatus.ACIVE;
        }
        return ProjectStatus.valueOf(apiStatus);
    }
}
