package de.itsjxsper.advancedreports.backend.category.mapper;

import de.itsjxsper.advancedreports.backend.category.data.entity.CategoryEntity;
import de.itsjxsper.advancedreports.backend.support.TestDataFactory;
import de.itsjxsper.advancedreports.common.model.catogory.CategoryDto;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CategoryMapper")
class CategoryMapperTest {

    private final CategoryMapper mapper = new CategoryMapperImpl();

    @Nested
    @DisplayName("toEntity und toDto")
    class RoundTrip {

        @Test
        @DisplayName("überträgt Name, Anzeigename, Beschreibung und Cooldown")
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
        @DisplayName("bildet eine Entity zurück auf ein DTO ab")
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
        @DisplayName("liefert null für null-Eingaben")
        void shouldMapNullToNull() {
            assertThat(mapper.toEntity(null)).isNull();
            assertThat(mapper.toDto(null)).isNull();
        }
    }

    @Nested
    @DisplayName("partialUpdate")
    class PartialUpdate {

        @Test
        @DisplayName("überschreibt nur die im DTO gesetzten Felder")
        void shouldIgnoreNullValues() {
            CategoryEntity entity = TestDataFactory.category("bugs");
            entity.setId(1L);

            CategoryDto dto = new CategoryDto(null, null, "Fehler & Bugs", null, 120L, null);

            CategoryEntity result = mapper.partialUpdate(dto, entity);

            assertThat(result.getDisplayName()).isEqualTo("Fehler & Bugs");
            assertThat(result.getCooldownSec()).isEqualTo(120L);
            // Untouched, because the DTO carried null.
            assertThat(result.getName()).isEqualTo("bugs");
            assertThat(result.getDescription()).isEqualTo("Beschreibung für bugs");
        }
    }

    @Nested
    @DisplayName("active-Flag")
    class ActiveFlag {

        @Test
        @DisplayName("übernimmt active = false aus dem DTO")
        void shouldMapActiveFalse() {
            CategoryDto dto = new CategoryDto(1L, "bugs", "Bugs", "Fehlermeldungen", 60L, false);

            CategoryEntity entity = mapper.toEntity(dto);

            assertThat(entity.getActive()).isFalse();
        }

    }
}
