package com.shaktee.nalok.projects.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

import com.shaktee.nalok.projects.model.ProjectStatus;
import com.shaktee.nalok.projects.repository.ProjectRepository;
import com.shaktee.nalok.projects.service.ProjectSectionService;
import com.shaktee.nalok.projects.service.ProjectService;
import com.shaktee.nalok.web.PageEnvelope;

/**
 * Read-only Projects API for the first frontend slice. Write operations land
 * when the Projects UI needs them; controllers stay thin wrappers.
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectsController {

    private final ProjectService projectService;
    private final ProjectSectionService projectSectionService;
    private final ProjectRepository projectRepository;

    public ProjectsController(
            ProjectService projectService,
            ProjectSectionService projectSectionService,
            ProjectRepository projectRepository) {
        this.projectService = projectService;
        this.projectSectionService = projectSectionService;
        this.projectRepository = projectRepository;
    }

    @GetMapping
    public PageEnvelope<ProjectDto> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ProjectStatus statusFilter = parseStatus(status);

        var projects = projectService.listProjectPage(statusFilter, page, size);
        List<ProjectDto> content = projects.getContent().stream()
                .map(ProjectDto::of)
                .toList();
        return PageEnvelope.of(content, projects.getNumber(), projects.getSize(),
                projects.getTotalElements(), projects.getTotalPages());
    }

    @GetMapping("/summary")
    public ProjectSummaryDto summary() {
        return new ProjectSummaryDto(
                projectRepository.count(),
                projectRepository.countByStatus(ProjectStatus.ACIVE),
                projectRepository.countByStatus(ProjectStatus.PAUSED),
                projectRepository.countByStatus(ProjectStatus.COMPLETED),
                projectRepository.countByStatus(ProjectStatus.ARCHIVED));
    }

    @GetMapping("/{id}")
    public ProjectDto get(@PathVariable UUID id) {
        return ProjectDto.of(projectService.getProject(id));
    }

    @GetMapping("/{id}/sections")
    public List<ProjectSectionDto> sections(@PathVariable UUID id) {
        return projectSectionService.listSections(id).stream()
                .map(ProjectSectionDto::of)
                .toList();
    }

    private ProjectStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ProjectDto.fromApiStatus(status);
        } catch (IllegalArgumentException unknownStatus) {
            throw new IllegalArgumentException("Unknown project status: " + status);
        }
    }

    public record ProjectSummaryDto(
            long total,
            long active,
            long paused,
            long completed,
            long archived) {
    }
}
