package com.shaktee.nalok.knowledge.study.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shaktee.nalok.knowledge.study.model.StudyWorkInterval;

public interface StudyWorkIntervalRepository extends JpaRepository<StudyWorkInterval, UUID> {

    List<StudyWorkInterval> findBySessionId(UUID sessionId);

    List<StudyWorkInterval> findBySessionIdAndEndedAtIsNull(UUID sessionId);

    /**
     * Lean span projection (timestamps only) — periodic sums should not drag
     * entities into memory. Full SQL-side aggregation (TIMESTAMPDIFF/GREATEST)
     * is deliberately avoided for now: binding Instant parameters through
     * native queries risks a connection-timezone mismatch with the JPA write
     * path; the overlap math stays in the service where tests pin it exactly.
     */
    interface IntervalSpanView {
        Instant getStartedAt();

        Instant getEndedAt();
    }

    /**
     * Earliest interval start — fallback coverage anchor for installations
     * that accumulated interval data before the persisted marker existed.
     */
    @Query("select w.startedAt from StudyWorkInterval w order by w.startedAt asc")
    List<Instant> findEarliestIntervalStart(Pageable pageable);

    /**
     * All intervals of one session overlapping [windowStart, windowEnd).
     */
    @Query("""
            select w from StudyWorkInterval w
            where w.sessionId = :sessionId
              and w.startedAt < :windowEnd
              and (w.endedAt is null or w.endedAt > :windowStart)
            """)
    List<StudyWorkInterval> findOverlappingBySession(
            @Param("sessionId") UUID sessionId,
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd);

    /**
     * All intervals of any session overlapping [windowStart, windowEnd):
     * started before the window ends and not ended (or ended) after the
     * window begins. Open intervals (ended_at null) always qualify. The
     * window windowEnd is exclusive; overlap math with an open interval is
     * done by the caller, which caps it at the reporting "now".
     */
    @Query("""
            select w.startedAt as startedAt,
                   coalesce(w.endedAt, :reportingNow) as endedAt
            from StudyWorkInterval w
            where w.startedAt < :windowEnd
              and (w.endedAt is null or w.endedAt > :windowStart)
            """)
    List<IntervalSpanView> findOverlappingSpans(
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd,
            @Param("reportingNow") Instant reportingNow);

    /**
     * Lean span projection for ONE session's overlapping intervals.
     */
    @Query("""
            select w.startedAt as startedAt,
                   coalesce(w.endedAt, :reportingNow) as endedAt
            from StudyWorkInterval w
            where w.sessionId = :sessionId
              and w.startedAt < :windowEnd
              and (w.endedAt is null or w.endedAt > :windowStart)
            """)
    List<IntervalSpanView> findOverlappingSpansBySession(
            @Param("sessionId") UUID sessionId,
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd,
            @Param("reportingNow") Instant reportingNow);
}
