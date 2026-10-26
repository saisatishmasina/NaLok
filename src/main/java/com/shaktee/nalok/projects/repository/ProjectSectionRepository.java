package com.shaktee.nalok.projects.repository;

import java.util.List;
import java.util.UUID;

import com.shaktee.nalok.projects.model.ProjectSection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectSectionRepository extends JpaRepository<ProjectSection, UUID> {

    List<ProjectSection> findByProject_IdOrderByPositionAsc(UUID projectId);

    /**
     * Sections with their owning project joined eagerly, so a read-only
     * listing can be mapped to API DTOs outside the service transaction
     * without lazy-loading surprises.
     */
    @Query("""
            select s from ProjectSection s
            join fetch s.project
            where s.project.id = :projectId
            order by s.position asc
            """)
    List<ProjectSection> findWithProjectByProjectId(@Param("projectId") UUID projectId);
}
