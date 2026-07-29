package de.itsjxsper.advancedreports.backend.category.controller;

import de.itsjxsper.advancedreports.backend.category.service.CategoryService;
import de.itsjxsper.advancedreports.backend.ratelimit.annotation.RateLimited;
import de.itsjxsper.advancedreports.common.model.catogory.CategoryDto;
import de.itsjxsper.advancedreports.common.model.catogory.CategoryReportCountDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Category API", description = "Category API")
public class CategoryController {

    private final CategoryService categoryService;

    @RateLimited
    @GetMapping
    @Operation(summary = "Get all categories", description = "Retrieve a paginated list of categories")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successful operation"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Page<CategoryDto>> getAllCategories(
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "100")
            @RequestParam(defaultValue = "100") int size
    ) {
        log.debug("Getting all categories with page={} and size={}", page, size);
        Page<CategoryDto> categories = this.categoryService.getCategories(PageRequest.of(page, size));
        return ResponseEntity.ok(categories);
    }

    @RateLimited
    @PostMapping
    @Operation(summary = "Create a new category", description = "Create a new category")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Category created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "409", description = "Category already exists"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CategoryDto> createCategory(@Valid @RequestBody CategoryDto categoryDto) {
        log.debug("Creating category with name={}", categoryDto.name());
        CategoryDto category = this.categoryService.createCategory(categoryDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(category);
    }

    @RateLimited
    @PatchMapping("/")
    @Operation(summary = "Update category", description = "Update an existing category by id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Category updated successfully"),
            @ApiResponse(responseCode = "404", description = "Category not found"),
            @ApiResponse(responseCode = "409", description = "Category already exists"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CategoryDto> updateCategory(@Valid @RequestBody CategoryDto categoryDto) {
        log.debug("Updating category with id={}", categoryDto.id());
        CategoryDto category = this.categoryService.updateCategory(categoryDto);
        return ResponseEntity.ok(category);
    }

    @RateLimited
    @DeleteMapping("/{categoryId}")
    @Operation(summary = "Delete category", description = "Delete a category by id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Category deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Category not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Void> deleteCategory(@PathVariable Long categoryId) {
        log.debug("Deleting category with id={}", categoryId);
        this.categoryService.deleteCategory(categoryId);
        return ResponseEntity.noContent().build();
    }

    @RateLimited
    @GetMapping("/{categoryId}")
    @Operation(summary = "Get category by id", description = "Retrieve category information by id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Category found successfully"),
            @ApiResponse(responseCode = "404", description = "Category not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CategoryDto> getCategory(@PathVariable Long categoryId) {
        log.debug("Getting category with id={}", categoryId);
        return ResponseEntity.ok(this.categoryService.getCategory(categoryId));
    }

    @RateLimited
    @GetMapping("/{categoryId}/reports")
    @Operation(summary = "Get category with reports", description = "Retrieve a category including report information")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Category found successfully"),
            @ApiResponse(responseCode = "404", description = "Category not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CategoryDto> getCategoryWithReports(@PathVariable Long categoryId) {
        log.debug("Getting category with reports for id={}", categoryId);
        return ResponseEntity.ok(this.categoryService.getCategoryWithReports(categoryId));
    }

    @RateLimited
    @GetMapping("/count")
    @Operation(summary = "Get category count", description = "Retrieve the total number of categories")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Category count retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Long> getCategoryCount() {
        log.debug("Getting category count");
        return ResponseEntity.ok(this.categoryService.countCategories());
    }

    @RateLimited
    @GetMapping("/reports/count")
    @Operation(summary = "Get report counts per category", description = "Retrieve report count grouped by category")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Counts retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<CategoryReportCountDto>> getReportCountPerCategory() {
        log.debug("Getting report count per category");
        return ResponseEntity.ok(this.categoryService.countCategoriesByReportCount());
    }

    @RateLimited
    @GetMapping("/reports/active")
    @Operation(summary = "Get categories with active reports", description = "Retrieve categories that currently have active reports")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Categories retrieved successfully"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<List<CategoryDto>> getCategoriesWithActiveReports() {
        log.debug("Getting categories with active reports");
        return ResponseEntity.ok(this.categoryService.getCategoriesWithActiveReports());
    }
}

