package com.shaktee.nalok.sources.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.shaktee.nalok.sources.model.SavedSource;

public interface SavedSourceRepository extends JpaRepository<SavedSource, UUID> {

    /** Identity check by exact-URL hash (see SavedSource.urlKeyFor). */
    boolean existsByUrlKey(String urlKey);

    Page<SavedSource> findByArchived(boolean archived, Pageable pageable);
}
