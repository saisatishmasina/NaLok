package com.shaktee.nalok.projects.web;

import java.util.UUID;

import com.shaktee.nalok.projects.model.ProjectSection;

/**
 * Bounded API representation of a project section.
 */
public record ProjectSectionDto(
        UUID id,
        UUID projectId,
        String title,
        String description,
        int position) {

    public static ProjectSectionDto of(ProjectSection section) {
        return new ProjectSectionDto(
                section.getId(),
                section.getProject() != null ? section.getProject().getId() : null,
                section.getTitle(),
                section.getDescription(),
                section.getPosition());
    }
}
