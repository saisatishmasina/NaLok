package com.shaktee.nalok.projects.repository;

import java.util.List;
import java.util.UUID;

import com.shaktee.nalok.projects.model.Project;
import com.shaktee.nalok.projects.model.ProjectStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findByStatus(ProjectStatus status);

    Page<Project> findByStatus(ProjectStatus status, Pageable pageable);

    long countByStatus(ProjectStatus status);
}
