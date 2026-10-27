package com.shaktee.nalok.world.event.web;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shaktee.nalok.web.PageEnvelope;
import com.shaktee.nalok.world.event.WorldEvent;
import com.shaktee.nalok.world.event.WorldEventKind;
import com.shaktee.nalok.world.event.service.WorldEventService;
import com.shaktee.nalok.world.reference.EntityReference;
import com.shaktee.nalok.world.reference.EntityType;

/**
 * Read-only world history. Events are appended by the operations they
 * describe; there is no write endpoint — inventing history over HTTP is
 * not a feature (E01a).
 */
@RestController
@RequestMapping("/api/events")
public class EventsController {

    private final WorldEventService eventService;

    public EventsController(WorldEventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public PageEnvelope<EventDto> list(
            @RequestParam(required = false) WorldEventKind kind,
            @RequestParam(required = false) EntityType targetType,
            @RequestParam(required = false) UUID targetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // A present reference requires type AND id — same rule as everywhere.
        if ((targetType == null) != (targetId == null)) {
            throw new IllegalArgumentException(
                    "targetType and targetId must be given together");
        }
        EntityReference target = targetType == null ? null
                : EntityReference.of(targetType, targetId);

        Page<WorldEvent> pageResult = eventService.listEvents(kind, target, page, size);
        List<EventDto> content = pageResult.getContent().stream()
                .map(EventDto::of)
                .toList();
        return PageEnvelope.of(content, pageResult.getNumber(), pageResult.getSize(),
                pageResult.getTotalElements(), pageResult.getTotalPages());
    }
}
