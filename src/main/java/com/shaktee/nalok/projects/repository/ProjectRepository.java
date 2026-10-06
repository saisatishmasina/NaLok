package com.shaktee.nalok.projects.repository;

import com.shaktee.nalok.projects.model.Project;
import com.shaktee.nalok.projects.model.ProjectStatus;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findByStatus(ProjectStatus status);

}
