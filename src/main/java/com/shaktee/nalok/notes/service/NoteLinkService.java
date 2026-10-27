package com.shaktee.nalok.notes.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.shaktee.nalok.knowledge.study.model.StudySession;
import com.shaktee.nalok.knowledge.study.service.GuardUnavailableException;
import com.shaktee.nalok.knowledge.study.service.StudySessionService;
import com.shaktee.nalok.notes.model.Note;
import com.shaktee.nalok.notes.model.NoteProjectLink;
import com.shaktee.nalok.notes.model.NoteSectionLink;
import com.shaktee.nalok.notes.model.NoteSessionLink;
import com.shaktee.nalok.notes.model.NoteSourceLink;
import com.shaktee.nalok.notes.repository.NoteProjectLinkRepository;
import com.shaktee.nalok.notes.repository.NoteRepository;
import com.shaktee.nalok.notes.repository.NoteSectionLinkRepository;
import com.shaktee.nalok.notes.repository.NoteSessionLinkRepository;
import com.shaktee.nalok.notes.repository.NoteSourceLinkRepository;
import com.shaktee.nalok.projects.service.ProjectSectionService;
import com.shaktee.nalok.projects.service.ProjectService;
import com.shaktee.nalok.sources.service.SavedSourceService;
import com.shaktee.nalok.web.NotFoundException;

/**
 * Note associations: one note reused across many projects, sections and
 * study sessions. Attach/unlink touch ONLY link rows — note content and
 * revision history are never affected, and unlinking never deletes the
 * note. Duplicate attaches are idempotent (single row, original linkedAt);
 * unlinking a non-linked target is a no-op. Unknown notes or targets are
 * rejected up front.
 *
 * Attaches serialize on a per-note MySQL named lock (same proven pattern as
 * the study active-session guard): two simultaneous attaches of the same
 * pair would otherwise both pass the existence check and collide on the
 * unique constraint. The lock makes the second one wait, find the existing
 * row, and answer idempotently.
 */
@Service
@Transactional(readOnly = true)
public class NoteLinkService {

    private static final String NOTE_LINK_GUARD_PREFIX = "nalok:note-links:";
    private static final int GUARD_TIMEOUT_SECONDS = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final NoteRepository noteRepository;
    private final NoteProjectLinkRepository projectLinkRepository;
    private final NoteSectionLinkRepository sectionLinkRepository;
    private final NoteSessionLinkRepository sessionLinkRepository;
    private final NoteSourceLinkRepository sourceLinkRepository;
    private final ProjectService projectService;
    private final ProjectSectionService projectSectionService;
    private final StudySessionService studySessionService;
    private final SavedSourceService sourceService;
    private final Clock clock;
    private final JdbcTemplate jdbcTemplate;

    public NoteLinkService(NoteRepository noteRepository,
            NoteProjectLinkRepository projectLinkRepository,
            NoteSectionLinkRepository sectionLinkRepository,
            NoteSessionLinkRepository sessionLinkRepository,
            NoteSourceLinkRepository sourceLinkRepository,
            ProjectService projectService,
            ProjectSectionService projectSectionService,
            StudySessionService studySessionService,
            SavedSourceService sourceService,
            Clock clock,
            JdbcTemplate jdbcTemplate) {
        this.noteRepository = noteRepository;
        this.projectLinkRepository = projectLinkRepository;
        this.sectionLinkRepository = sectionLinkRepository;
        this.sessionLinkRepository = sessionLinkRepository;
        this.sourceLinkRepository = sourceLinkRepository;
        this.projectService = projectService;
        this.projectSectionService = projectSectionService;
        this.studySessionService = studySessionService;
        this.sourceService = sourceService;
        this.clock = clock;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public NoteProjectLink linkToProject(UUID noteId, UUID projectId) {
        acquireLinkGuard(noteId);
        releaseGuardAfterTransactionEnds(noteId);
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
        acquireLinkGuard(noteId);
        releaseGuardAfterTransactionEnds(noteId);
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
        acquireLinkGuard(noteId);
        releaseGuardAfterTransactionEnds(noteId);
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
    public NoteSourceLink linkToSource(UUID noteId, UUID sourceId) {
        acquireLinkGuard(noteId);
        releaseGuardAfterTransactionEnds(noteId);
        Note note = findNote(noteId);
        requireSource(sourceId);
        NoteSourceLink existing =
                sourceLinkRepository.findByNote_IdAndSource_Id(noteId, sourceId)
                        .orElse(null);
        if (existing != null) {
            return existing;
        }
        return sourceLinkRepository.save(
                new NoteSourceLink(note, sourceService.getSource(sourceId),
                        clock.instant()));
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

    @Transactional
    public void unlinkFromSource(UUID noteId, UUID sourceId) {
        findNote(noteId);
        sourceLinkRepository.findByNote_IdAndSource_Id(noteId, sourceId)
                .ifPresent(sourceLinkRepository::delete);
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

    public List<NoteSourceLink> listSourceLinks(UUID noteId) {
        findNote(noteId);
        return sourceLinkRepository.findByNote_IdOrderByLinkedAtAsc(noteId);
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

    /**
     * Bounded context pages: the database does the union and dedup for
     * projects (direct + section links) and plain paging elsewhere; the
     * unpaginated variants above remain for small, fully-rendered panels.
     */
    public Page<Note> listNotesForProjectPage(UUID projectId, int page, int size) {
        requireProject(projectId);
        return projectLinkRepository.findNotesForProject(projectId, pageRequest(page, size));
    }

    public Page<Note> listNotesForSectionPage(UUID sectionId, int page, int size) {
        requireSection(sectionId);
        return sectionLinkRepository.findNotesForSection(sectionId, pageRequest(page, size));
    }

    public Page<Note> listNotesForSessionPage(UUID sessionId, int page, int size) {
        studySessionService.getSession(sessionId);
        return sessionLinkRepository.findNotesForSession(sessionId, pageRequest(page, size));
    }

    public List<Note> listNotesForSource(UUID sourceId) {
        requireSource(sourceId);
        return newestFirst(sourceLinkRepository
                .findBySource_IdOrderByLinkedAtAsc(sourceId)
                .stream().map(NoteSourceLink::getNote).toList());
    }

    public Page<Note> listNotesForSourcePage(UUID sourceId, int page, int size) {
        requireSource(sourceId);
        return sourceLinkRepository.findNotesForSource(sourceId, pageRequest(page, size));
    }

    private PageRequest pageRequest(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("id")));
    }

    private Note findNote(UUID noteId) {
        return noteRepository.findById(noteId)
                .orElseThrow(() -> new NotFoundException("Note not found: " + noteId));
    }

    /**
     * One link mutation per note at a time. GET_LOCK binds to the current
     * transaction's connection, so the release must happen on that same
     * connection AFTER commit/rollback — released via transaction
     * synchronization while the connection is still bound (proven pattern
     * from the study active-session guard).
     */
    private void acquireLinkGuard(UUID noteId) {
        Integer result = jdbcTemplate.queryForObject(
                "SELECT GET_LOCK(?, ?)", Integer.class,
                NOTE_LINK_GUARD_PREFIX + noteId, GUARD_TIMEOUT_SECONDS);

        if (result == null || result != 1) {
            throw new GuardUnavailableException(
                    "could not acquire the note-link guard; try again");
        }
    }

    private void releaseGuardAfterTransactionEnds(UUID noteId) {
        String guardName = NOTE_LINK_GUARD_PREFIX + noteId;
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        try {
                            jdbcTemplate.queryForObject(
                                    "SELECT RELEASE_LOCK(?)", Integer.class, guardName);
                        } catch (RuntimeException releasedElsewhereOrGone) {
                            // Best effort; the lock dies with the connection.
                        }
                    }
                });
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

    private void requireSource(UUID sourceId) {
        if (!sourceService.sourceExists(sourceId)) {
            throw new NotFoundException("Source not found: " + sourceId);
        }
    }

    private List<Note> newestFirst(List<Note> notes) {
        List<Note> sorted = new ArrayList<>(notes);
        sorted.sort(Comparator.comparing(Note::getUpdatedAt,
                Comparator.reverseOrder()).thenComparing(Note::getId));
        return sorted;
    }
}
