package de.itsjxsper.advancedreports.backend.category.service;

import de.itsjxsper.advancedreports.backend.category.data.repository.CategoryRepository;
import de.itsjxsper.advancedreports.backend.category.exceptions.CategoryAlreadyExistException;
import de.itsjxsper.advancedreports.backend.category.exceptions.CategoryNotFoundException;
import de.itsjxsper.advancedreports.backend.category.mapper.CategoryMapper;
import de.itsjxsper.advancedreports.common.model.catogory.CategoryDto;
import de.itsjxsper.advancedreports.common.model.catogory.CategoryReportCountDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    public CategoryDto createCategory(CategoryDto categoryDto) {
        log.debug("Creating category with name={}", categoryDto.name());
        this.categoryRepository.findByName(categoryDto.name())
                .ifPresent(category -> {
                    throw new CategoryAlreadyExistException(categoryDto.name());
                });

        var categoryEntity = this.categoryMapper.toEntity(categoryDto);

        var savedEntity = this.categoryRepository.save(categoryEntity);
        log.debug("Created category id={} name={}", savedEntity.getId(), savedEntity.getName());

        return this.categoryMapper.toDto(savedEntity);
    }

    public CategoryDto updateCategory(CategoryDto categoryDto) {
        log.debug("Updating category with id={}", categoryDto);
        var categoryEntity = this.categoryRepository.findById(categoryDto.id())
                .orElseThrow(() -> new CategoryNotFoundException(categoryDto.id()));

        if (!categoryEntity.getName().equals(categoryDto.name())
                && this.categoryRepository.existsByName(categoryDto.name())) {
            throw new CategoryAlreadyExistException(categoryDto.name());
        }

        var categoryEntityUpdated = this.categoryMapper.partialUpdate(categoryDto, categoryEntity);

        var savedEntity = this.categoryRepository.save(categoryEntityUpdated);
        log.debug("Updated category id={} name={}", savedEntity.getId(), savedEntity.getName());

        return this.categoryMapper.toDto(savedEntity);
    }

    public void deleteCategory(Long categoryId) {
        log.debug("Deleting category with id={}", categoryId);
        var categoryEntity = this.categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
        this.categoryRepository.delete(categoryEntity);
        log.debug("Deleted category with id={}", categoryId);
    }

    public CategoryDto getCategory(Long categoryId) {
        log.debug("Fetching category with id={}", categoryId);
        // findById statt findWithReportsById: CategoryDto hat kein Reports-Feld, der EntityGraph hat
        // hier also nur den kompletten Report-Graph der Kategorie mitgeladen und wieder verworfen.
        return this.categoryRepository.findById(categoryId)
                .map(this.categoryMapper::toDto)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
    }

    public CategoryDto getCategoryWithReports(Long categoryId) {
        log.debug("Fetching category with id={} with reports", categoryId);
        return this.categoryRepository.findWithReportsById(categoryId)
                .map(this.categoryMapper::toDto)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
    }

    public long countCategories() {
        var count = this.categoryRepository.count();
        log.debug("Counted categories={}", count);
        return count;
    }

    public List<CategoryReportCountDto> countCategoriesByReportCount() {
        log.debug("Counting categories by report count");
        return this.categoryRepository.countReportsPerCategory()
                .stream()
                .map(row -> new CategoryReportCountDto(
                        (Long) row[0],
                        (String) row[1],
                        (Long) row[2]
                ))
                .toList();
    }

    public Page<CategoryDto> getCategories(Pageable pageable) {
        log.debug("Fetching categories page with pageable={}", pageable);
        return this.categoryRepository.findAll(pageable)
                .map(this.categoryMapper::toDto);
    }

    public List<CategoryDto> getCategoriesWithActiveReports() {
        log.debug("Fetching categories with active reports");
        return this.categoryRepository.findCategoriesWithActiveReports()
                .stream()
                .map(this.categoryMapper::toDto)
                .toList();
    }
}

