package com.shaktee.nalok.notes.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shaktee.nalok.notes.model.Note;
import com.shaktee.nalok.notes.model.NoteKind;
import com.shaktee.nalok.notes.model.NoteRevision;
import com.shaktee.nalok.notes.model.NoteRevisionSummary;
import com.shaktee.nalok.notes.repository.NoteRepository;
import com.shaktee.nalok.notes.repository.NoteRevisionRepository;
import com.shaktee.nalok.web.NotFoundException;

/**
 * Complete note operations and their transaction boundary. The note and its
 * revisions commit or roll back together: a failed save must never leave a
 * partial update or an orphan revision.
 *
 * Expected-version edits: the caller sends the version it read; a mismatch
 * is rejected BEFORE any mutation, so the newer server content is preserved
 * and the client keeps the chance to recover its draft.
 */
@Service
@Transactional(readOnly = true)
public class NoteService {

    private static final int MAX_PAGE_SIZE = 100;

    private final NoteRepository noteRepository;
    private final NoteRevisionRepository noteRevisionRepository;
    private final Clock clock;

    public NoteService(NoteRepository noteRepository,
            NoteRevisionRepository noteRevisionRepository,
            Clock clock) {
        this.noteRepository = noteRepository;
        this.noteRevisionRepository = noteRevisionRepository;
        this.clock = clock;
    }

    /**
     * Creation records the initial revision so even never-edited writing has
     * a preserved moment. The revision counter is set before the first
     * insert, so a fresh note is a single INSERT at version 0 — no phantom
     * version bump on creation.
     */
    @Transactional
    public Note createNote(String title, String body, NoteKind kind) {
        Instant now = clock.instant();
        Note note = new Note(title, body, kind, now);
        note.revisionRecorded(now);
        Note saved = noteRepository.save(note);
        noteRevisionRepository.save(new NoteRevision(saved, 1, now));
        return saved;
    }

    public Note getNote(UUID noteId) {
        return findNote(noteId);
    }

    /**
     * Content-changing save: records a revision. An unchanged save (same
     * title, body and kind) records nothing — no duplicate history noise.
     * Null kind keeps the current kind.
     */
    @Transactional
    public Note updateNote(UUID noteId, String title, String body, NoteKind kind,
            long expectedVersion) {
        // Locking read first (see NoteRepository.findByIdForUpdate): this
        // serializes concurrent editors on the note row before either can
        // touch note_revisions, so the race loser answers a clean version
        // conflict rather than a database deadlock.
        Note note = noteRepository.findByIdForUpdate(noteId)
                .orElseThrow(() -> new NotFoundException("Note not found: " + noteId));
        if (note.getVersion() != expectedVersion) {
            throw new ObjectOptimisticLockingFailureException(Note.class, noteId);
        }

        String previousTitle = note.getTitle();
        String previousBody = note.getBody();
        NoteKind previousKind = note.getKind();

        note.changeContent(title, body, kind == null ? previousKind : kind);

        boolean contentChanged = !Objects.equals(previousTitle, note.getTitle())
                || !Objects.equals(previousBody, note.getBody())
                || previousKind != note.getKind();

        if (contentChanged) {
            recordRevision(note, clock.instant());
        }
        return note;
    }

    /**
     * Reversible archive. Not a content change: no revision, but the record
     * version and updatedAt still move so concurrent editors are protected.
     */
    @Transactional
    public Note archiveNote(UUID noteId) {
        Note note = findNote(noteId);
        note.archive(clock.instant());
        return note;
    }

    @Transactional
    public Note restoreNote(UUID noteId) {
        Note note = findNote(noteId);
        note.restore(clock.instant());
        return note;
    }

    /**
     * Bounded, newest-first page of note summaries for UI lists. Null kind =
     * all kinds; null archived = both states. Page size is capped like the
     * study and projects history.
     */
    public Page<Note> listNotes(NoteKind kind, Boolean archived, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        PageRequest pageRequest = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("id")));

        if (kind == null && archived == null) {
            return noteRepository.findAll(pageRequest);
        }
        if (kind == null) {
            return noteRepository.findByArchived(archived, pageRequest);
        }
        if (archived == null) {
            return noteRepository.findByKind(kind, pageRequest);
        }
        return noteRepository.findByKindAndArchived(kind, archived, pageRequest);
    }

    /** Full writing trail of one note, newest first. */
    public List<NoteRevision> listRevisions(UUID noteId) {
        findNote(noteId);
        return noteRevisionRepository.findByNote_IdOrderByRevisionNumberDesc(noteId);
    }

    /**
     * Bounded revision-history page, newest first, loaded as a closed
     * projection (metadata only — bodies are never selected). The
     * unpaginated list above stays for the editor's full trail.
     */
    public Page<NoteRevisionSummary> listRevisionPage(UUID noteId, int page, int size) {
        findNote(noteId);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Pageable pageRequest = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Order.desc("revisionNumber")));
        return noteRevisionRepository.findSummariesByNote_Id(noteId, pageRequest);
    }

    public NoteRevision getRevision(UUID noteId, UUID revisionId) {
        findNote(noteId);
        return noteRevisionRepository.findByIdAndNote_Id(revisionId, noteId)
                .orElseThrow(() -> new NotFoundException(
                        "Note revision not found: " + revisionId));
    }

    private void recordRevision(Note note, Instant recordedAt) {
        NoteRevision revision = new NoteRevision(
                note, note.nextRevisionNumber(), recordedAt);
        noteRevisionRepository.save(revision);
        note.revisionRecorded(recordedAt);
    }

    private Note findNote(UUID noteId) {
        return noteRepository.findById(noteId)
                .orElseThrow(() -> new NotFoundException(
                        "Note not found: " + noteId));
    }
}
