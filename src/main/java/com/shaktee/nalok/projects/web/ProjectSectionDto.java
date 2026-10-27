package com.shaktee.nalok.projects.web;

import java.util.UUID;

import com.shaktee.nalok.projects.model.ProjectSection;

/**
 * Bounded API representation of a project section. The version rides along
 * so an edit can prove it was made against current state (stale edits are
 * rejected, never silently overwrite newer work).
 */
public record ProjectSectionDto(
        UUID id,
        UUID projectId,
        String title,
        String description,
        int position,
        long version) {

    public static ProjectSectionDto of(ProjectSection section) {
        return new ProjectSectionDto(
                section.getId(),
                section.getProject() != null ? section.getProject().getId() : null,
                section.getTitle(),
                section.getDescription(),
                section.getPosition(),
                section.getVersion());
    }
}
