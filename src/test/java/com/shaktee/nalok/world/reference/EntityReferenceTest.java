package com.shaktee.nalok.world.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class EntityReferenceTest {

    @Test
    void presentReferenceRequiresTypeAndId() {
        UUID id = UUID.randomUUID();

        EntityReference reference = EntityReference.of(EntityType.PROJECT, id);
        assertThat(reference.getEntityType()).isEqualTo(EntityType.PROJECT);
        assertThat(reference.getReferenceId()).isEqualTo(id);

        assertThatThrownBy(() -> EntityReference.of(null, id))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EntityReference.of(EntityType.PROJECT, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EntityReference.of(null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
