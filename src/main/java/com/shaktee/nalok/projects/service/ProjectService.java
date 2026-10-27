package com.shaktee.nalok.projects.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shaktee.nalok.projects.model.Project;
import com.shaktee.nalok.projects.model.ProjectStatus;
import com.shaktee.nalok.projects.repository.ProjectRepository;
import com.shaktee.nalok.web.NotFoundException;

import org.springframework.dao.OptimisticLockingFailureException;

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

    /**
     * Bounded, newest-first page of projects for UI lists. Null status = all
     * projects; page size is capped like the study history.
     */
    public Page<Project> listProjectPage(ProjectStatus status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        PageRequest pageRequest = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));

        if (status == null) {
            return projectRepository.findAll(pageRequest);
        }

        return projectRepository.findByStatus(status, pageRequest);
    }

    @Transactional
    public Project updateProject(UUID projectId, long expectedVersion, String title,
            String description) {
        // Rename AND description land in ONE transaction: an edit is a single
        // intent, not two sequential writes that could diverge. The version
        // carried from the user's snapshot must still be current — a stale
        // form is rejected instead of silently overwriting newer work.
        Project project = findProject(projectId);
        if (project.getVersion() != expectedVersion) {
            throw new OptimisticLockingFailureException(
                    "The record changed concurrently; reload and try again");
        }
        project.rename(title);
        project.updateDescription(description);
        return project;
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

    public boolean projectExists(UUID projectId) {
        return projectRepository.existsById(projectId);
    }

    private Project findProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException(
                        "Project not found: " + projectId));
    }
}
