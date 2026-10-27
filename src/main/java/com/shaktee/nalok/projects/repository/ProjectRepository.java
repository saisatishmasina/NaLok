package com.shaktee.nalok.projects.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.shaktee.nalok.projects.model.Project;
import com.shaktee.nalok.projects.model.ProjectStatus;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findByStatus(ProjectStatus status);

    Page<Project> findByStatus(ProjectStatus status, Pageable pageable);

    long countByStatus(ProjectStatus status);

    /**
     * Locking read used to SERIALIZE per-project write intents (section
     * appends). The row lock is held until the transaction commits, so
     * concurrent appends cannot compute the same next position.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.id = :id")
    Optional<Project> findByIdForWrite(@Param("id") UUID id);
}
