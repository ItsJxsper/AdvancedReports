package de.itsjxsper.advancedreports.backend.category.service;

import de.itsjxsper.advancedreports.backend.category.data.entity.CategoryEntity;
import de.itsjxsper.advancedreports.backend.category.data.repository.CategoryRepository;
import de.itsjxsper.advancedreports.backend.category.exceptions.CategoryAlreadyExistException;
import de.itsjxsper.advancedreports.backend.category.exceptions.CategoryNotFoundException;
import de.itsjxsper.advancedreports.backend.category.mapper.CategoryMapper;
import de.itsjxsper.advancedreports.common.model.catogory.CategoryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryService")
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryMapper categoryMapper;

    @InjectMocks
    private CategoryService categoryService;

    private CategoryEntity categoryEntity;
    private CategoryDto categoryDto;

    @BeforeEach
    void setUp() {
        categoryEntity = new CategoryEntity();
        categoryEntity.setId(1L);
        categoryEntity.setName("bugs");
        categoryEntity.setDisplayName("Bugs");
        categoryEntity.setDescription("Fehlermeldungen und Bugreports");
        categoryEntity.setCooldownSec(60L);
        // "active" ist final mit Default true in CategoryEntity und kann nicht gesetzt werden

        categoryDto = new CategoryDto(1L, "bugs", "Bugs", "Fehlermeldungen und Bugreports", 60L, true);
    }

    @Nested
    @DisplayName("createCategory")
    class CreateCategory {

        @Test
        @DisplayName("legt eine neue Kategorie an, wenn der Name noch nicht existiert")
        void shouldCreateCategoryWhenNameDoesNotExist() {
            when(categoryRepository.findByName(categoryDto.name())).thenReturn(Optional.empty());
            when(categoryMapper.toEntity(categoryDto)).thenReturn(categoryEntity);
            when(categoryRepository.save(categoryEntity)).thenReturn(categoryEntity);
            when(categoryMapper.toDto(categoryEntity)).thenReturn(categoryDto);

            CategoryDto result = categoryService.createCategory(categoryDto);

            assertThat(result).isEqualTo(categoryDto);
            verify(categoryRepository).save(categoryEntity);
        }

        @Test
        @DisplayName("wirft CategoryAlreadyExistException, wenn der Name bereits existiert")
        void shouldThrowWhenNameAlreadyExists() {
            when(categoryRepository.findByName(categoryDto.name()))
                    .thenReturn(Optional.of(categoryEntity));

            assertThatThrownBy(() -> categoryService.createCategory(categoryDto))
                    .isInstanceOf(CategoryAlreadyExistException.class);

            verify(categoryRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updateCategory")
    class UpdateCategory {

        @Test
        @DisplayName("aktualisiert eine bestehende Kategorie")
        void shouldUpdateCategory() {
            var updatedDto = new CategoryDto(1L, "feature-requests", "Feature Requests",
                    "Wünsche für neue Features", 120L, true);
            var updatedEntity = new CategoryEntity();
            updatedEntity.setId(1L);
            updatedEntity.setName("feature-requests");
            updatedEntity.setDisplayName("Feature Requests");
            updatedEntity.setDescription("Wünsche für neue Features");
            updatedEntity.setCooldownSec(120L);

            when(categoryRepository.findById(1L)).thenReturn(Optional.of(categoryEntity));
            when(categoryRepository.existsByName(updatedDto.name())).thenReturn(false);
            when(categoryMapper.partialUpdate(updatedDto, categoryEntity)).thenReturn(updatedEntity);
            when(categoryRepository.save(updatedEntity)).thenReturn(updatedEntity);
            when(categoryMapper.toDto(updatedEntity)).thenReturn(updatedDto);

            CategoryDto result = categoryService.updateCategory(updatedDto);

            assertThat(result.name()).isEqualTo("feature-requests");
            assertThat(result.displayName()).isEqualTo("Feature Requests");
            verify(categoryRepository).save(updatedEntity);
        }

        @Test
        @DisplayName("wirft CategoryNotFoundException, wenn die Kategorie nicht existiert")
        void shouldThrowWhenCategoryNotFound() {
            when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.updateCategory(categoryDto))
                    .isInstanceOf(CategoryNotFoundException.class);

            verify(categoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("wirft CategoryAlreadyExistException, wenn der neue Name bereits vergeben ist")
        void shouldThrowWhenNewNameAlreadyExists() {
            var updatedDto = new CategoryDto(1L, "feature-requests", "Feature Requests",
                    "Wünsche für neue Features", 120L, true);

            when(categoryRepository.findById(1L)).thenReturn(Optional.of(categoryEntity));
            when(categoryRepository.existsByName(updatedDto.name())).thenReturn(true);

            assertThatThrownBy(() -> categoryService.updateCategory(updatedDto))
                    .isInstanceOf(CategoryAlreadyExistException.class);

            verify(categoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("prüft existsByName nicht, wenn sich der Name nicht ändert")
        void shouldNotCheckExistsByNameWhenNameUnchanged() {
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(categoryEntity));
            when(categoryMapper.partialUpdate(categoryDto, categoryEntity)).thenReturn(categoryEntity);
            when(categoryRepository.save(categoryEntity)).thenReturn(categoryEntity);
            when(categoryMapper.toDto(categoryEntity)).thenReturn(categoryDto);

            categoryService.updateCategory(categoryDto);

            verify(categoryRepository, never()).existsByName(any());
        }
    }

    @Nested
    @DisplayName("deleteCategory")
    class DeleteCategory {

        @Test
        @DisplayName("löscht eine bestehende Kategorie")
        void shouldDeleteCategory() {
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(categoryEntity));

            categoryService.deleteCategory(1L);

            verify(categoryRepository).delete(categoryEntity);
        }

        @Test
        @DisplayName("wirft CategoryNotFoundException, wenn die Kategorie nicht existiert")
        void shouldThrowWhenCategoryNotFound() {
            when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.deleteCategory(1L))
                    .isInstanceOf(CategoryNotFoundException.class);

            verify(categoryRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("getCategory")
    class GetCategory {

        @Test
        @DisplayName("liefert eine Kategorie mit Reports zurück")
        void shouldReturnCategory() {
            when(categoryRepository.findWithReportsById(1L)).thenReturn(Optional.of(categoryEntity));
            when(categoryMapper.toDto(categoryEntity)).thenReturn(categoryDto);

            CategoryDto result = categoryService.getCategory(1L);

            assertThat(result).isEqualTo(categoryDto);
        }

        @Test
        @DisplayName("wirft CategoryNotFoundException, wenn die Kategorie nicht existiert")
        void shouldThrowWhenNotFound() {
            when(categoryRepository.findWithReportsById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.getCategory(1L))
                    .isInstanceOf(CategoryNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("countCategories")
    class CountCategories {

        @Test
        @DisplayName("gibt die Gesamtanzahl der Kategorien zurück")
        void shouldReturnCategoryCount() {
            when(categoryRepository.count()).thenReturn(5L);

            long result = categoryService.countCategories();

            assertThat(result).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("getCategories")
    class GetCategories {

        @Test
        @DisplayName("liefert eine paginierte Liste von Kategorien zurück")
        void shouldReturnPagedCategories() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<CategoryEntity> entityPage = new PageImpl<>(List.of(categoryEntity));

            when(categoryRepository.findAll(pageable)).thenReturn(entityPage);
            when(categoryMapper.toDto(categoryEntity)).thenReturn(categoryDto);

            Page<CategoryDto> result = categoryService.getCategories(pageable);

            assertThat(result.getContent()).containsExactly(categoryDto);
        }
    }

    @Nested
    @DisplayName("getCategoriesWithActiveReports")
    class GetCategoriesWithActiveReports {

        @Test
        @DisplayName("liefert nur Kategorien mit aktiven Reports zurück")
        void shouldReturnCategoriesWithActiveReports() {
            when(categoryRepository.findCategoriesWithActiveReports())
                    .thenReturn(List.of(categoryEntity));
            when(categoryMapper.toDto(categoryEntity)).thenReturn(categoryDto);

            List<CategoryDto> result = categoryService.getCategoriesWithActiveReports();

            assertThat(result).containsExactly(categoryDto);
        }

        @Test
        @DisplayName("liefert eine leere Liste, wenn keine Kategorien aktive Reports haben")
        void shouldReturnEmptyListWhenNoneHaveActiveReports() {
            when(categoryRepository.findCategoriesWithActiveReports()).thenReturn(List.of());

            List<CategoryDto> result = categoryService.getCategoriesWithActiveReports();

            assertThat(result).isEmpty();
        }
    }
}