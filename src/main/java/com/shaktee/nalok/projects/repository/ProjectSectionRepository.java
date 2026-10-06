package com.shaktee.nalok.projects.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shaktee.nalok.projects.model.ProjectSection;

public interface ProjectSectionRepository extends JpaRepository<ProjectSection, UUID> {

    List<ProjectSection> findByProject_IdOrderByPositionAsc(UUID projectId);

}
