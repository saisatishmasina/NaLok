package com.shaktee.nalok.sources.web;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
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

import com.shaktee.nalok.notes.model.Note;
import com.shaktee.nalok.notes.service.NoteLinkService;
import com.shaktee.nalok.notes.web.NoteDto;
import com.shaktee.nalok.sources.model.SavedSource;
import com.shaktee.nalok.sources.service.SavedSourceService;
import com.shaktee.nalok.web.PageEnvelope;

/**
 * Thin web layer over the saved-source operations. HTTP concerns only:
 * parameter binding, validation, DTO projection; every rule lives in the
 * model/service layer. R01a is URL identity only — no fetching.
 */
@RestController
@RequestMapping("/api/sources")
public class SourcesController {

    private final SavedSourceService sourceService;
    private final NoteLinkService noteLinkService;

    public SourcesController(SavedSourceService sourceService,
            NoteLinkService noteLinkService) {
        this.sourceService = sourceService;
        this.noteLinkService = noteLinkService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SourceDto create(@Valid @RequestBody CreateSourceRequest request) {
        return SourceDto.of(sourceService.createSource(
                request.title(), request.url(), request.description()));
    }

    @GetMapping
    public PageEnvelope<SourceDto.SourceSummaryDto> list(
            @RequestParam(required = false) Boolean archived,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<SavedSource> pageResult = sourceService.listSources(archived, page, size);
        List<SourceDto.SourceSummaryDto> content = pageResult.getContent().stream()
                .map(SourceDto.SourceSummaryDto::of)
                .toList();
        return PageEnvelope.of(content, pageResult.getNumber(), pageResult.getSize(),
                pageResult.getTotalElements(), pageResult.getTotalPages());
    }

    @GetMapping("/{id}")
    public SourceDto get(@PathVariable UUID id) {
        return SourceDto.of(sourceService.getSource(id));
    }

    @PutMapping("/{id}")
    public SourceDto update(@PathVariable UUID id,
            @Valid @RequestBody UpdateSourceRequest request) {
        return SourceDto.of(sourceService.updateSource(id, request.title(),
                request.description(), request.expectedVersion()));
    }

    @PostMapping("/{id}/archive")
    public SourceDto archive(@PathVariable UUID id) {
        return SourceDto.of(sourceService.archiveSource(id));
    }

    @PostMapping("/{id}/restore")
    public SourceDto restore(@PathVariable UUID id) {
        return SourceDto.of(sourceService.restoreSource(id));
    }

    /** Notes associated with this source; small panels only. */
    @GetMapping("/{id}/notes")
    public List<NoteDto.NoteSummaryDto> notes(@PathVariable UUID id) {
        return noteLinkService.listNotesForSource(id).stream()
                .map(NoteDto.NoteSummaryDto::of)
                .toList();
    }

    /** Bounded page of the source's notes (same semantics, capped). */
    @GetMapping("/{id}/notes/page")
    public PageEnvelope<NoteDto.NoteSummaryDto> notesPage(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Note> pageResult =
                noteLinkService.listNotesForSourcePage(id, page, size);
        List<NoteDto.NoteSummaryDto> content = pageResult.getContent().stream()
                .map(NoteDto.NoteSummaryDto::of)
                .toList();
        return PageEnvelope.of(content, pageResult.getNumber(), pageResult.getSize(),
                pageResult.getTotalElements(), pageResult.getTotalPages());
    }
}
