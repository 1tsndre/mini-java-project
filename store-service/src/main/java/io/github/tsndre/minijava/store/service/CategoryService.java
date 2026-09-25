package io.github.tsndre.minijava.store.service;

import io.github.tsndre.minijava.store.dto.request.CreateCategoryRequest;
import io.github.tsndre.minijava.store.dto.request.UpdateCategoryRequest;
import io.github.tsndre.minijava.store.dto.response.CategoryResponse;
import io.github.tsndre.minijava.store.model.Category;
import io.github.tsndre.minijava.store.repository.CategoryRepository;
import io.github.tsndre.minijava.store.repository.ForeignKeyViolationException;
import io.github.tsndre.minijava.store.repository.UniqueViolationException;
import io.github.tsndre.minijava.store.service.exception.ConflictException;
import io.github.tsndre.minijava.store.service.exception.InternalException;
import io.github.tsndre.minijava.store.service.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryResponse createCategory(CreateCategoryRequest req) {
        Category category = Category.builder().name(req.name()).build();

        try {
            category = categoryRepository.create(category);
        } catch (RuntimeException e) {
            log.error("failed to create category", e);
            if (e instanceof UniqueViolationException) {
                throw new ConflictException("category already exists");
            }
            throw new InternalException("failed to create category");
        }

        return category.toResponse();
    }

    public List<CategoryResponse> getAllCategories() {
        List<Category> categories;
        try {
            categories = categoryRepository.findAll();
        } catch (RuntimeException e) {
            log.error("failed to fetch categories", e);
            throw new InternalException("failed to fetch categories");
        }
        return categories.stream().map(Category::toResponse).toList();
    }

    public CategoryResponse updateCategory(UUID id, UpdateCategoryRequest req) {
        Category category = findCategory(id);

        if (!req.name().isEmpty()) {
            category.setName(req.name());
        }

        try {
            category = categoryRepository.update(category);
        } catch (RuntimeException e) {
            log.error("failed to update category", e);
            if (e instanceof UniqueViolationException) {
                throw new ConflictException("category already exists");
            }
            throw new InternalException("failed to update category");
        }

        return category.toResponse();
    }

    public void deleteCategory(UUID id) {
        findCategory(id);

        try {
            categoryRepository.delete(id);
        } catch (ForeignKeyViolationException e) {
            throw new ConflictException("category is in use by existing products");
        } catch (RuntimeException e) {
            log.error("failed to delete category", e);
            throw new InternalException("failed to delete category");
        }
    }

    private Category findCategory(UUID id) {
        try {
            return categoryRepository.findById(id).orElseThrow();
        } catch (RuntimeException e) {
            throw new NotFoundException("category not found");
        }
    }
}
