package com.shaktee.nalok.knowledge.study.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shaktee.nalok.knowledge.study.model.StudySession;
import com.shaktee.nalok.knowledge.study.model.StudySessionStatus;

public interface StudySessionRepository extends JpaRepository<StudySession, UUID> {

    List<StudySession> findByStatus(StudySessionStatus status);
}
