package com.shaktee.nalok.projects.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.shaktee.nalok.projects.model.ProjectStatus;
import com.shaktee.nalok.projects.repository.ProjectRepository;
import com.shaktee.nalok.projects.service.ProjectSectionService;
import com.shaktee.nalok.projects.service.ProjectService;
import com.shaktee.nalok.web.PageEnvelope;

/**
 * Projects API. Read endpoints landed first; project create/edit write
 * operations ride on the validated service contract. Lifecycle transitions
 * (start/pause/complete/archive) arrive as their own endpoint actions.
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

    /** POST → 201 Created with the persisted PLANNED project. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectDto create(@Valid @RequestBody CreateProjectRequest request) {
        return ProjectDto.of(projectService.createProject(request.title(), request.description()));
    }

    /** PUT → 200 with the updated project (stale snapshot version → 409). */
    @PutMapping("/{id}")
    public ProjectDto update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProjectRequest request) {
        return ProjectDto.of(projectService.updateProject(
                id, request.version(), request.title(), request.description()));
    }

    // ---------- F07: lifecycle transitions ----------

    /** Each transition follows the model's own state rules; violations → 409. */
    @PostMapping("/{id}/start")
    public ProjectDto start(@PathVariable UUID id) {
        return ProjectDto.of(projectService.startProject(id));
    }

    @PostMapping("/{id}/pause")
    public ProjectDto pause(@PathVariable UUID id) {
        return ProjectDto.of(projectService.pauseProject(id));
    }

    @PostMapping("/{id}/complete")
    public ProjectDto complete(@PathVariable UUID id) {
        return ProjectDto.of(projectService.completeProject(id));
    }

    @PostMapping("/{id}/archive")
    public ProjectDto archive(@PathVariable UUID id) {
        return ProjectDto.of(projectService.archiveProject(id));
    }

    // ---------- F07: sections ----------

    /** POST → 201 Created with the section APPENDED after the last position. */
    @PostMapping("/{id}/sections")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectSectionDto createSection(
            @PathVariable UUID id,
            @Valid @RequestBody CreateSectionRequest request) {
        return ProjectSectionDto.of(
                projectSectionService.appendSection(id, request.title(), request.description()));
    }

    /** PUT → 200 with the edited section (stale snapshot version → 409). */
    @PutMapping("/sections/{sectionId}")
    public ProjectSectionDto updateSection(
            @PathVariable UUID sectionId,
            @Valid @RequestBody UpdateSectionRequest request) {
        return ProjectSectionDto.of(projectSectionService.updateSection(
                sectionId, request.version(), request.title(), request.description()));
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
