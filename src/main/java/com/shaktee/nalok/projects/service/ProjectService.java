package com.shaktee.nalok.projects.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shaktee.nalok.projects.model.Project;
import com.shaktee.nalok.projects.model.ProjectStatus;
import com.shaktee.nalok.projects.repository.ProjectRepository;

@Service
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public Project getProject(UUID projectId) {
        return findProject(projectId);
    }

    public List<Project> listProjects(ProjectStatus status) {
        return projectRepository.findByStatus(status);
    }

    @Transactional
    public Project createProject(String title, String description) {
        Project project = new Project(title, description);
        return projectRepository.save(project);
    }

    @Transactional
    public Project renameProject(UUID projectId, String title) {
        Project project = findProject(projectId);
        project.rename(title);
        return project;
    }

    @Transactional
    public Project updateDescription(UUID projectId, String description) {
        Project project = findProject(projectId);
        project.updateDescription(description);
        return project;
    }

    @Transactional
    public Project startProject(UUID projectId) {

        Project project = findProject(projectId);
        project.start();
        return project;

    }

    @Transactional
    public Project pauseProject(UUID projectId) {

        Project project = findProject(projectId);
        project.pause();
        return project;

    }

    @Transactional
    public Project completeProject(UUID projectId) {

        Project project = findProject(projectId);
        project.complete();
        return project;

    }

    @Transactional
    public Project archiveProject(UUID projectId) {

        Project project = findProject(projectId);
        project.archive();
        return project;

    }

    private Project findProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found" + projectId));
    }
}
