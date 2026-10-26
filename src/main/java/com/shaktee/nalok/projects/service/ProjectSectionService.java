package com.shaktee.nalok.projects.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shaktee.nalok.projects.model.ProjectSection;
import com.shaktee.nalok.projects.repository.ProjectRepository;
import com.shaktee.nalok.projects.repository.ProjectSectionRepository;
import com.shaktee.nalok.projects.model.Project;

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

        return sectionRepository.findByProject_IdOrderByPositionAsc(projectId);
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

    private Project findProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Project not found: " + projectId));
    }

    private ProjectSection findSection(UUID sectionId) {
        return sectionRepository.findById(sectionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Section not found: " + sectionId));
    }

}
