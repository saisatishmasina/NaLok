package com.shaktee.nalok.notes.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shaktee.nalok.knowledge.study.model.StudySession;
import com.shaktee.nalok.knowledge.study.service.StudySessionService;
import com.shaktee.nalok.notes.model.Note;
import com.shaktee.nalok.notes.model.NoteProjectLink;
import com.shaktee.nalok.notes.model.NoteSectionLink;
import com.shaktee.nalok.notes.model.NoteSessionLink;
import com.shaktee.nalok.notes.repository.NoteProjectLinkRepository;
import com.shaktee.nalok.notes.repository.NoteRepository;
import com.shaktee.nalok.notes.repository.NoteSectionLinkRepository;
import com.shaktee.nalok.notes.repository.NoteSessionLinkRepository;
import com.shaktee.nalok.projects.service.ProjectSectionService;
import com.shaktee.nalok.projects.service.ProjectService;
import com.shaktee.nalok.web.NotFoundException;

/**
 * Note associations: one note reused across many projects, sections and
 * study sessions. Attach/unlink touch ONLY link rows — note content and
 * revision history are never affected, and unlinking never deletes the
 * note. Duplicate attaches are idempotent (single row, original linkedAt);
 * unlinking a non-linked target is a no-op. Unknown notes or targets are
 * rejected up front.
 */
@Service
@Transactional(readOnly = true)
public class NoteLinkService {

    private final NoteRepository noteRepository;
    private final NoteProjectLinkRepository projectLinkRepository;
    private final NoteSectionLinkRepository sectionLinkRepository;
    private final NoteSessionLinkRepository sessionLinkRepository;
    private final ProjectService projectService;
    private final ProjectSectionService projectSectionService;
    private final StudySessionService studySessionService;
    private final Clock clock;

    public NoteLinkService(NoteRepository noteRepository,
            NoteProjectLinkRepository projectLinkRepository,
            NoteSectionLinkRepository sectionLinkRepository,
            NoteSessionLinkRepository sessionLinkRepository,
            ProjectService projectService,
            ProjectSectionService projectSectionService,
            StudySessionService studySessionService,
            Clock clock) {
        this.noteRepository = noteRepository;
        this.projectLinkRepository = projectLinkRepository;
        this.sectionLinkRepository = sectionLinkRepository;
        this.sessionLinkRepository = sessionLinkRepository;
        this.projectService = projectService;
        this.projectSectionService = projectSectionService;
        this.studySessionService = studySessionService;
        this.clock = clock;
    }

    @Transactional
    public NoteProjectLink linkToProject(UUID noteId, UUID projectId) {
        Note note = findNote(noteId);
        requireProject(projectId);
        NoteProjectLink existing =
                projectLinkRepository.findByNote_IdAndProject_Id(noteId, projectId)
                        .orElse(null);
        if (existing != null) {
            return existing;
        }
        return projectLinkRepository.save(
                new NoteProjectLink(note, projectService.getProject(projectId),
                        clock.instant()));
    }

    @Transactional
    public NoteSectionLink linkToSection(UUID noteId, UUID sectionId) {
        Note note = findNote(noteId);
        requireSection(sectionId);
        NoteSectionLink existing =
                sectionLinkRepository.findByNote_IdAndSection_Id(noteId, sectionId)
                        .orElse(null);
        if (existing != null) {
            return existing;
        }
        return sectionLinkRepository.save(
                new NoteSectionLink(note, projectSectionService.getSection(sectionId),
                        clock.instant()));
    }

    @Transactional
    public NoteSessionLink linkToSession(UUID noteId, UUID sessionId) {
        Note note = findNote(noteId);
        StudySession session = studySessionService.getSession(sessionId);
        NoteSessionLink existing =
                sessionLinkRepository.findByNote_IdAndSession_Id(noteId, sessionId)
                        .orElse(null);
        if (existing != null) {
            return existing;
        }
        return sessionLinkRepository.save(
                new NoteSessionLink(note, session, clock.instant()));
    }

    @Transactional
    public void unlinkFromProject(UUID noteId, UUID projectId) {
        findNote(noteId);
        projectLinkRepository.findByNote_IdAndProject_Id(noteId, projectId)
                .ifPresent(projectLinkRepository::delete);
    }

    @Transactional
    public void unlinkFromSection(UUID noteId, UUID sectionId) {
        findNote(noteId);
        sectionLinkRepository.findByNote_IdAndSection_Id(noteId, sectionId)
                .ifPresent(sectionLinkRepository::delete);
    }

    @Transactional
    public void unlinkFromSession(UUID noteId, UUID sessionId) {
        findNote(noteId);
        sessionLinkRepository.findByNote_IdAndSession_Id(noteId, sessionId)
                .ifPresent(sessionLinkRepository::delete);
    }

    public List<NoteProjectLink> listProjectLinks(UUID noteId) {
        findNote(noteId);
        return projectLinkRepository.findByNote_IdOrderByLinkedAtAsc(noteId);
    }

    public List<NoteSectionLink> listSectionLinks(UUID noteId) {
        findNote(noteId);
        return sectionLinkRepository.findByNote_IdOrderByLinkedAtAsc(noteId);
    }

    public List<NoteSessionLink> listSessionLinks(UUID noteId) {
        findNote(noteId);
        return sessionLinkRepository.findByNote_IdOrderByLinkedAtAsc(noteId);
    }

    /**
     * All notes usable in a project: directly linked plus section-linked.
     * Section-linked notes are reachable project-wide WITHOUT a duplicate
     * project link; a note linked both ways appears once.
     */
    public List<Note> listNotesForProject(UUID projectId) {
        requireProject(projectId);
        Map<UUID, Note> notes = new LinkedHashMap<>();
        projectLinkRepository.findByProject_IdOrderByLinkedAtAsc(projectId)
                .forEach(link -> notes.put(link.getNote().getId(), link.getNote()));
        sectionLinkRepository.findBySection_Project_IdOrderByLinkedAtAsc(projectId)
                .forEach(link -> notes.putIfAbsent(link.getNote().getId(), link.getNote()));
        return newestFirst(List.copyOf(notes.values()));
    }

    public List<Note> listNotesForSection(UUID sectionId) {
        requireSection(sectionId);
        return newestFirst(sectionLinkRepository
                .findBySection_IdOrderByLinkedAtAsc(sectionId)
                .stream().map(NoteSectionLink::getNote).toList());
    }

    public List<Note> listNotesForSession(UUID sessionId) {
        studySessionService.getSession(sessionId);
        return newestFirst(sessionLinkRepository
                .findBySession_IdOrderByLinkedAtAsc(sessionId)
                .stream().map(NoteSessionLink::getNote).toList());
    }

    private Note findNote(UUID noteId) {
        return noteRepository.findById(noteId)
                .orElseThrow(() -> new NotFoundException("Note not found: " + noteId));
    }

    private void requireProject(UUID projectId) {
        if (!projectService.projectExists(projectId)) {
            throw new NotFoundException("Project not found: " + projectId);
        }
    }

    private void requireSection(UUID sectionId) {
        if (!projectSectionService.sectionExists(sectionId)) {
            throw new NotFoundException("Project section not found: " + sectionId);
        }
    }

    private List<Note> newestFirst(List<Note> notes) {
        List<Note> sorted = new ArrayList<>(notes);
        sorted.sort(Comparator.comparing(Note::getUpdatedAt,
                Comparator.reverseOrder()).thenComparing(Note::getId));
        return sorted;
    }
}
