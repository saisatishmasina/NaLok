package com.shaktee.nalok.knowledge.study.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shaktee.nalok.knowledge.study.model.StudyReportingCoverage;

/**
 * Singleton coverage marker (see D09 / model javadoc): when interval-based
 * reporting became available.
 */
public interface StudyReportingCoverageRepository
        extends JpaRepository<StudyReportingCoverage, Long> {

    Optional<StudyReportingCoverage> findFirstByOrderByCoverageStartAsc();
}
