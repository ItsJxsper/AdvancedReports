package de.itsjxsper.advancedreports.backend.category.mapper;

import de.itsjxsper.advancedreports.backend.category.data.entity.CategoryEntity;
import de.itsjxsper.advancedreports.backend.support.TestDataFactory;
import de.itsjxsper.advancedreports.common.model.catogory.CategoryDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CategoryMapper")
class CategoryMapperTest {

    private final CategoryMapper mapper = new CategoryMapperImpl();

    @Nested
    @DisplayName("toEntity and toDto")
    class RoundTrip {

        @Test
        @DisplayName("carries name, display name, description and cooldown")
        void shouldMapCoreFields() {
            CategoryDto dto = new CategoryDto(1L, "bugs", "Bugs", "Fehlermeldungen", 60L, true);

            CategoryEntity entity = mapper.toEntity(dto);

            assertThat(entity.getId()).isEqualTo(1L);
            assertThat(entity.getName()).isEqualTo("bugs");
            assertThat(entity.getDisplayName()).isEqualTo("Bugs");
            assertThat(entity.getDescription()).isEqualTo("Fehlermeldungen");
            assertThat(entity.getCooldownSec()).isEqualTo(60L);
        }

        @Test
        @DisplayName("maps an entity back to a DTO")
        void shouldMapBackToDto() {
            CategoryEntity entity = TestDataFactory.category("bugs");
            entity.setId(1L);

            CategoryDto dto = mapper.toDto(entity);

            assertThat(dto.id()).isEqualTo(1L);
            assertThat(dto.name()).isEqualTo("bugs");
            assertThat(dto.displayName()).isEqualTo("Bugs");
            assertThat(dto.cooldownSec()).isEqualTo(60L);
            assertThat(dto.active()).isTrue();
        }

        @Test
        @DisplayName("returns null for null input")
        void shouldMapNullToNull() {
            assertThat(mapper.toEntity(null)).isNull();
            assertThat(mapper.toDto(null)).isNull();
        }
    }

    @Nested
    @DisplayName("partialUpdate")
    class PartialUpdate {

        @Test
        @DisplayName("overwrites only the fields set in the DTO")
        void shouldIgnoreNullValues() {
            CategoryEntity entity = TestDataFactory.category("bugs");
            entity.setId(1L);

            CategoryDto dto = new CategoryDto(null, null, "Fehler & Bugs", null, 120L, null);

            CategoryEntity result = mapper.partialUpdate(dto, entity);

            assertThat(result.getDisplayName()).isEqualTo("Fehler & Bugs");
            assertThat(result.getCooldownSec()).isEqualTo(120L);
            // Untouched, because the DTO carried null.
            assertThat(result.getName()).isEqualTo("bugs");
            assertThat(result.getDescription()).isEqualTo("Description for bugs");
        }
    }

    @Nested
    @DisplayName("active flag")
    class ActiveFlag {

        @Test
        @DisplayName("takes active = false from the DTO")
        void shouldMapActiveFalse() {
            CategoryDto dto = new CategoryDto(1L, "bugs", "Bugs", "Fehlermeldungen", 60L, false);

            CategoryEntity entity = mapper.toEntity(dto);

            assertThat(entity.getActive()).isFalse();
        }

    }
}
