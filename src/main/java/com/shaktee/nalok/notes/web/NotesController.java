package com.shaktee.nalok.notes.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.shaktee.nalok.notes.model.NoteKind;
import com.shaktee.nalok.notes.service.NoteLinkService;
import com.shaktee.nalok.notes.service.NoteService;
import com.shaktee.nalok.web.PageEnvelope;

/**
 * Thin web layer over the notes application operations. HTTP concerns only:
 * parameter binding, validation, DTO projection; every rule lives in the
 * model/service layer.
 */
@RestController
@RequestMapping("/api/notes")
public class NotesController {

    private final NoteService noteService;
    private final NoteLinkService noteLinkService;

    public NotesController(NoteService noteService, NoteLinkService noteLinkService) {
        this.noteService = noteService;
        this.noteLinkService = noteLinkService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NoteDto create(@Valid @RequestBody CreateNoteRequest request) {
        return NoteDto.of(noteService.createNote(
                request.title(), request.body(), request.kind()));
    }

    @GetMapping
    public PageEnvelope<NoteDto.NoteSummaryDto> list(
            @RequestParam(required = false) NoteKind kind,
            @RequestParam(required = false) Boolean archived,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var pageResult = noteService.listNotes(kind, archived, page, size);
        List<NoteDto.NoteSummaryDto> content = pageResult.getContent().stream()
                .map(NoteDto.NoteSummaryDto::of)
                .toList();
        return PageEnvelope.of(content, pageResult.getNumber(), pageResult.getSize(),
                pageResult.getTotalElements(), pageResult.getTotalPages());
    }

    @GetMapping("/{id}")
    public NoteDto get(@PathVariable UUID id) {
        return NoteDto.of(noteService.getNote(id));
    }

    @PutMapping("/{id}")
    public NoteDto update(@PathVariable UUID id,
            @Valid @RequestBody UpdateNoteRequest request) {
        return NoteDto.of(noteService.updateNote(id, request.title(), request.body(),
                request.kind(), request.expectedVersion()));
    }

    @PostMapping("/{id}/archive")
    public NoteDto archive(@PathVariable UUID id) {
        return NoteDto.of(noteService.archiveNote(id));
    }

    @PostMapping("/{id}/restore")
    public NoteDto restore(@PathVariable UUID id) {
        return NoteDto.of(noteService.restoreNote(id));
    }

    @GetMapping("/{id}/revisions")
    public List<NoteDto.NoteRevisionDto> revisions(@PathVariable UUID id) {
        return noteService.listRevisions(id).stream()
                .map(NoteDto.NoteRevisionDto::of)
                .toList();
    }

    @GetMapping("/{id}/revisions/{revisionId}")
    public NoteDto.NoteRevisionDto revision(@PathVariable UUID id,
            @PathVariable UUID revisionId) {
        return NoteDto.NoteRevisionDto.of(noteService.getRevision(id, revisionId));
    }

    @GetMapping("/{id}/links")
    public NoteLinksDto links(@PathVariable UUID id) {
        return NoteLinksDto.of(noteLinkService.listProjectLinks(id),
                noteLinkService.listSectionLinks(id),
                noteLinkService.listSessionLinks(id));
    }

    /**
     * Idempotent attaches answer 200 with the updated link set, so a
     * retried request is never an error for the UI.
     */
    @PostMapping("/{id}/links/projects/{projectId}")
    public NoteLinksDto linkProject(@PathVariable UUID id,
            @PathVariable UUID projectId) {
        noteLinkService.linkToProject(id, projectId);
        return links(id);
    }

    @PostMapping("/{id}/links/sections/{sectionId}")
    public NoteLinksDto linkSection(@PathVariable UUID id,
            @PathVariable UUID sectionId) {
        noteLinkService.linkToSection(id, sectionId);
        return links(id);
    }

    @PostMapping("/{id}/links/sessions/{sessionId}")
    public NoteLinksDto linkSession(@PathVariable UUID id,
            @PathVariable UUID sessionId) {
        noteLinkService.linkToSession(id, sessionId);
        return links(id);
    }

    @DeleteMapping("/{id}/links/projects/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlinkProject(@PathVariable UUID id, @PathVariable UUID projectId) {
        noteLinkService.unlinkFromProject(id, projectId);
    }

    @DeleteMapping("/{id}/links/sections/{sectionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlinkSection(@PathVariable UUID id, @PathVariable UUID sectionId) {
        noteLinkService.unlinkFromSection(id, sectionId);
    }

    @DeleteMapping("/{id}/links/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlinkSession(@PathVariable UUID id, @PathVariable UUID sessionId) {
        noteLinkService.unlinkFromSession(id, sessionId);
    }

    /**
     * Context retrieval: project-wide includes section-linked notes.
     */
    @GetMapping("/for-project/{projectId}")
    public List<NoteDto.NoteSummaryDto> forProject(@PathVariable UUID projectId) {
        return noteLinkService.listNotesForProject(projectId).stream()
                .map(NoteDto.NoteSummaryDto::of)
                .toList();
    }

    @GetMapping("/for-section/{sectionId}")
    public List<NoteDto.NoteSummaryDto> forSection(@PathVariable UUID sectionId) {
        return noteLinkService.listNotesForSection(sectionId).stream()
                .map(NoteDto.NoteSummaryDto::of)
                .toList();
    }

    @GetMapping("/for-session/{sessionId}")
    public List<NoteDto.NoteSummaryDto> forSession(@PathVariable UUID sessionId) {
        return noteLinkService.listNotesForSession(sessionId).stream()
                .map(NoteDto.NoteSummaryDto::of)
                .toList();
    }
}
