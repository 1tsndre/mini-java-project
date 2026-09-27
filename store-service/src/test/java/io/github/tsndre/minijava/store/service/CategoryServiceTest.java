package io.github.tsndre.minijava.store.service;

import io.github.tsndre.minijava.store.dto.request.CreateCategoryRequest;
import io.github.tsndre.minijava.store.dto.request.UpdateCategoryRequest;
import io.github.tsndre.minijava.store.model.Category;
import io.github.tsndre.minijava.store.repository.CategoryRepository;
import io.github.tsndre.minijava.store.service.exception.ConflictException;
import io.github.tsndre.minijava.store.service.exception.InternalException;
import io.github.tsndre.minijava.store.service.exception.NotFoundException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private final UUID categoryId = UUID.randomUUID();

    @Mock
    private CategoryRepository categoryRepository;
    @InjectMocks
    private CategoryService categoryService;

    private Category electronics() {
        return Category.builder().id(categoryId).name("Electronics").build();
    }

    @Nested
    class CreateCategory {

        @Test
        void success() {
            when(categoryRepository.create(any())).thenAnswer(invocation -> invocation.getArgument(0));

            assertThat(categoryService.createCategory(new CreateCategoryRequest("Electronics")).name())
                    .isEqualTo("Electronics");
        }

        @Test
        void duplicateName() {
            when(categoryRepository.create(any())).thenThrow(TestErrors.duplicateKey());

            assertThatThrownBy(() -> categoryService.createCategory(new CreateCategoryRequest("Electronics")))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("category already exists");
        }

        @Test
        void createFails() {
            when(categoryRepository.create(any())).thenThrow(TestErrors.dbError());

            assertThatThrownBy(() -> categoryService.createCategory(new CreateCategoryRequest("Electronics")))
                    .isInstanceOf(InternalException.class)
                    .hasMessage("failed to create category");
        }
    }

    @Nested
    class GetAllCategories {

        @Test
        void successWithCategories() {
            when(categoryRepository.findAll()).thenReturn(List.of(electronics(),
                    Category.builder().id(UUID.randomUUID()).name("Clothing").build()));

            assertThat(categoryService.getAllCategories()).hasSize(2);
        }

        @Test
        void successEmpty() {
            when(categoryRepository.findAll()).thenReturn(List.of());

            assertThat(categoryService.getAllCategories()).isEmpty();
        }

        @Test
        void dbError() {
            when(categoryRepository.findAll()).thenThrow(TestErrors.dbError());

            assertThatThrownBy(() -> categoryService.getAllCategories())
                    .isInstanceOf(InternalException.class)
                    .hasMessage("failed to fetch categories");
        }
    }

    @Nested
    class UpdateCategory {

        @Test
        void success() {
            when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(electronics()));
            when(categoryRepository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

            assertThat(categoryService.updateCategory(categoryId, new UpdateCategoryRequest("Gadgets")).name())
                    .isEqualTo("Gadgets");
        }

        @Test
        void notFound() {
            when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.updateCategory(categoryId, new UpdateCategoryRequest("Gadgets")))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("category not found");
        }

        @Test
        void duplicateName() {
            when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(electronics()));
            when(categoryRepository.update(any())).thenThrow(TestErrors.duplicateKey());

            assertThatThrownBy(() -> categoryService.updateCategory(categoryId, new UpdateCategoryRequest("Clothing")))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("category already exists");
        }
    }

    @Nested
    class DeleteCategory {

        @Test
        void success() {
            when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(electronics()));

            assertThatCode(() -> categoryService.deleteCategory(categoryId)).doesNotThrowAnyException();
            verify(categoryRepository).delete(categoryId);
        }

        @Test
        void notFound() {
            when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.deleteCategory(categoryId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("category not found");
        }

        @Test
        void stillReferencedByProducts() {
            when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(electronics()));
            doThrow(TestErrors.foreignKeyViolation()).when(categoryRepository).delete(categoryId);

            assertThatThrownBy(() -> categoryService.deleteCategory(categoryId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("category is in use by existing products");
        }
    }
}
