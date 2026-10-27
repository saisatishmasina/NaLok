package com.shaktee.nalok.projects.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shaktee.nalok.projects.model.ProjectSection;
import com.shaktee.nalok.projects.repository.ProjectRepository;
import com.shaktee.nalok.projects.repository.ProjectSectionRepository;
import com.shaktee.nalok.projects.model.Project;
import com.shaktee.nalok.web.NotFoundException;

import org.springframework.dao.OptimisticLockingFailureException;

@Service
@Transactional(readOnly = true)
public class ProjectSectionService {

    private final ProjectRepository projectRepository;
    private final ProjectSectionRepository sectionRepository;

    public ProjectSectionService(
            ProjectRepository projectRepository,
            ProjectSectionRepository projectSectionRepository) {
        this.projectRepository = projectRepository;
        this.sectionRepository = projectSectionRepository;
    }

    public ProjectSection getSection(UUID sectionId) {

        return findSection(sectionId);

    }

    public List<ProjectSection> listSections(UUID projectId) {
        findProject(projectId);

        return sectionRepository.findWithProjectByProjectId(projectId);
    }

    @Transactional
    public ProjectSection createSection(
            UUID projectId,
            String title,
            String description,
            int position) {
        Project project = findProject(projectId);
        ProjectSection section = new ProjectSection(project, title, description, position);
        return sectionRepository.save(section);
    }

    /**
     * UI-friendly creation: the section is APPENDED after the current last
     * position, so the caller never has to know about ordering internals.
     *
     * Concurrency: both reads are PESSIMISTIC — the project row lock
     * serializes racing appends onto one project, and the locking section
     * read then sees every committed section, so max(position)+1 cannot be
     * computed twice for the same project. createSection(position) does NOT
     * take this path and stays for internal/existing callers.
     */
    @Transactional
    public ProjectSection appendSection(UUID projectId, String title, String description) {
        Project project = projectRepository.findByIdForWrite(projectId)
                .orElseThrow(() -> new NotFoundException(
                        "Project not found: " + projectId));
        List<ProjectSection> existing = sectionRepository.findByProjectIdForWrite(projectId);
        int nextPosition = existing.stream()
                .mapToInt(ProjectSection::getPosition)
                .max()
                .orElse(-1) + 1;
        ProjectSection section = new ProjectSection(project, title, description, nextPosition);
        return sectionRepository.save(section);
    }

    /**
     * Rename AND description land in ONE transaction, mirroring the project
     * edit: a section edit is a single intent. The caller carries the
     * snapshot's version; a mismatched version means somebody else edited
     * first — reject rather than overwrite newer work.
     */
    @Transactional
    public ProjectSection updateSection(UUID sectionId, long expectedVersion, String title,
            String description) {
        ProjectSection section = findSection(sectionId);
        if (section.getVersion() != expectedVersion) {
            throw new OptimisticLockingFailureException(
                    "The record changed concurrently; reload and try again");
        }
        section.rename(title);
        section.updateDescription(description);
        return section;
    }

    @Transactional
    public ProjectSection renameSection(UUID sectionId, String title) {
        ProjectSection section = findSection(sectionId);
        section.rename(title);
        return section;
    }

    @Transactional
    public ProjectSection updateDescription(
            UUID sectionId,
            String description) {
        ProjectSection section = findSection(sectionId);
        section.updateDescription(description);
        return section;
    }

    @Transactional
    public ProjectSection changePosition(UUID sectionId, int position) {
        ProjectSection section = findSection(sectionId);
        section.changePosition(position);
        return section;
    }

    public boolean sectionExists(UUID sectionId) {
        return sectionRepository.existsById(sectionId);
    }

    private Project findProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException(
                        "Project not found: " + projectId));
    }

    private ProjectSection findSection(UUID sectionId) {
        return sectionRepository.findById(sectionId)
                .orElseThrow(() -> new NotFoundException(
                        "Section not found: " + sectionId));
    }

}
