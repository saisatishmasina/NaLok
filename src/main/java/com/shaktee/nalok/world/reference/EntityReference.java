package com.shaktee.nalok.world.reference;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * Optional shared context value: a polymorphic type + UUID pointing at a
 * project or project section. A present reference has BOTH fields; there is
 * no half-reference. An absent (null) embeddable means standalone.
 *
 * No foreign key is possible across target tables; existence is validated by
 * services, and dangling references after a target deletion are a documented
 * limitation (see docs/ARCHITECTURE.md and docs/DECISIONS.md).
 */
@Embeddable
public class EntityReference {

    @Enumerated(EnumType.STRING)
    @Column(name = "context_type", length = 40)
    private EntityType entityType;

    @Column(name = "context_id", columnDefinition = "BINARY(16)")
    private UUID referenceId;

    protected EntityReference() {
    }

    private EntityReference(EntityType entityType, UUID referenceId) {
        if (entityType == null || referenceId == null) {
            throw new IllegalArgumentException(
                    "A present reference requires type AND id; both or neither.");
        }

        this.entityType = entityType;
        this.referenceId = referenceId;
    }

    public static EntityReference of(EntityType entityType, UUID referenceId) {
        return new EntityReference(entityType, referenceId);
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    @Override
    public String toString() {
        return entityType + "#" + referenceId;
    }
}
