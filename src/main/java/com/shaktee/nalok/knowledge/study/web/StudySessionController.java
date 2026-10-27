package com.shaktee.nalok.knowledge.study.web;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.shaktee.nalok.knowledge.study.model.StudySession;
import com.shaktee.nalok.knowledge.study.model.StudySessionStatus;
import com.shaktee.nalok.knowledge.study.repository.StudySessionRepository;
import com.shaktee.nalok.knowledge.study.service.StudySessionService;
import com.shaktee.nalok.knowledge.study.service.StudyWeeklySummary;
import com.shaktee.nalok.web.PageEnvelope;
import com.shaktee.nalok.world.reference.EntityReference;
import com.shaktee.nalok.world.reference.EntityType;

/**
 * Thin web layer over the study application operations. It owns HTTP
 * concerns only: parameter binding, validation, DTO projection and error
 * mapping; every rule lives in the model/service layer.
 */
@RestController
@RequestMapping("/api/study")
public class StudySessionController {

    private final StudySessionService studySessionService;
    private final StudySessionRepository studySessionRepository;
    private final Clock clock;

    public StudySessionController(
            StudySessionService studySessionService,
            StudySessionRepository studySessionRepository,
            Clock clock) {
        this.studySessionService = studySessionService;
        this.studySessionRepository = studySessionRepository;
        this.clock = clock;
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public StudySessionDto create(@Valid @RequestBody CreateStudySessionRequest request) {
        StudySession created = studySessionService.createSession(
                request.title(),
                request.summary(),
                toContext(request.context()));
        return StudySessionDto.of(created, clock.instant());
    }

    @GetMapping("/sessions")
    public PageEnvelope<StudySessionDto> list(
            @RequestParam(required = false) StudySessionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var history = studySessionService.listSessionHistory(status, page, size);
        List<StudySessionDto> content = history.getContent().stream()
                .map(this::toDto)
                .toList();
        return PageEnvelope.of(content, history.getNumber(), history.getSize(),
                history.getTotalElements(), history.getTotalPages());
    }

    /**
     * Sessions whose context references ONE target (F08 section workbench).
     * Both query params are required together — an incomplete pair is a bad
     * request, keeping the EntityReference both-or-neither rule at the URL.
     */
    @GetMapping("/sessions/by-context")
    public PageEnvelope<StudySessionDto> listByContext(
            @RequestParam EntityType entityType,
            @RequestParam UUID referenceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var sessions = studySessionService.listSessionsByContext(entityType, referenceId, page, size);
        List<StudySessionDto> content = sessions.getContent().stream()
                .map(this::toDto)
                .toList();
        return PageEnvelope.of(content, sessions.getNumber(), sessions.getSize(),
                sessions.getTotalElements(), sessions.getTotalPages());
    }

    /**
     * The globally focused session (only one can be ACTIVE, D06), returned
     * with 204 when none is: the UI needs a definitive answer.
     */
    @GetMapping("/sessions/current")
    public ResponseEntity<StudySessionDto> current() {
        List<StudySession> active = studySessionRepository.findByStatus(StudySessionStatus.ACTIVE);
        if (active.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(StudySessionDto.of(active.get(0), clock.instant()));
    }

    @GetMapping("/sessions/{id}")
    public StudySessionDto get(@PathVariable UUID id) {
        return toDto(studySessionService.getSession(id));
    }

    @PostMapping("/sessions/{id}/start")
    public StudySessionDto start(@PathVariable UUID id) {
        return toDto(studySessionService.startSession(id));
    }

    @PostMapping("/sessions/{id}/pause")
    public StudySessionDto pause(@PathVariable UUID id) {
        return toDto(studySessionService.pauseSession(id));
    }

    @PostMapping("/sessions/{id}/resume")
    public StudySessionDto resume(@PathVariable UUID id) {
        return toDto(studySessionService.resumeSession(id));
    }

    @PostMapping("/sessions/{id}/finish")
    public StudySessionDto finish(@PathVariable UUID id) {
        return toDto(studySessionService.finishSession(id));
    }

    @PostMapping("/sessions/{id}/recover-interrupted")
    public StudySessionDto recoverInterrupted(@PathVariable UUID id) {
        return toDto(studySessionService.recoverInterrupted(id));
    }

    @GetMapping("/summary")
    public StudyWeeklySummary summary(
            @RequestParam(defaultValue = "UTC") String zone) {
        try {
            return studySessionService.weeklySummary(ZoneId.of(zone));
        } catch (DateTimeException unknownZone) {
            throw new IllegalArgumentException("Unknown time zone: " + zone);
        }
    }

    private StudySessionDto toDto(StudySession session) {
        return StudySessionDto.of(session, clock.instant());
    }

    private EntityReference toContext(CreateStudySessionRequest.ContextRequest context) {
        if (context == null) {
            return null;
        }
        return EntityReference.of(context.entityType(), context.referenceId());
    }
}
