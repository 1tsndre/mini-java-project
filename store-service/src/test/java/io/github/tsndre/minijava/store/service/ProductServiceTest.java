package io.github.tsndre.minijava.store.service;

import io.github.tsndre.minijava.store.dto.request.CreateProductRequest;
import io.github.tsndre.minijava.store.dto.request.UpdateProductRequest;
import io.github.tsndre.minijava.store.dto.response.ProductResponse;
import io.github.tsndre.minijava.store.model.Product;
import io.github.tsndre.minijava.store.model.ProductFilter;
import io.github.tsndre.minijava.store.model.Store;
import io.github.tsndre.minijava.store.repository.PageResult;
import io.github.tsndre.minijava.store.repository.ProductRepository;
import io.github.tsndre.minijava.store.repository.StoreRepository;
import io.github.tsndre.minijava.store.service.exception.ConflictException;
import io.github.tsndre.minijava.store.service.exception.ForbiddenException;
import io.github.tsndre.minijava.store.service.exception.InternalException;
import io.github.tsndre.minijava.store.service.exception.NotFoundException;
import io.github.tsndre.minijava.store.service.exception.ValidationException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID storeId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();

    @Mock
    private ProductRepository productRepository;
    @Mock
    private StoreRepository storeRepository;
    @InjectMocks
    private ProductService productService;

    private void userOwnsStore() {
        when(storeRepository.findByUserId(userId)).thenReturn(Optional.of(Store.builder().id(storeId).userId(userId).build()));
    }

    private Product product(UUID ownerStoreId) {
        return Product.builder().id(productId).storeId(ownerStoreId).categoryId(categoryId).name("Laptop")
                .price(new BigDecimal("15000000")).stock(10).build();
    }

    @Nested
    class CreateProduct {

        @Test
        void success() {
            userOwnsStore();
            when(productRepository.create(any())).thenAnswer(invocation -> invocation.getArgument(0));

            ProductResponse resp = productService.createProduct(userId,
                    new CreateProductRequest(categoryId.toString(), "Laptop", "A nice laptop", "15000000", 10L));

            assertThat(resp.name()).isEqualTo("Laptop");
            assertThat(resp.storeId()).isEqualTo(storeId);
            assertThat(resp.imageUrl()).isEmpty();
        }

        @Test
        void noStoreFound() {
            when(storeRepository.findByUserId(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.createProduct(userId,
                    new CreateProductRequest(categoryId.toString(), "Laptop", "", "15000000", 0L)))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("store not found for this user");
        }

        @Test
        void invalidPrice() {
            userOwnsStore();

            assertThatThrownBy(() -> productService.createProduct(userId,
                    new CreateProductRequest(categoryId.toString(), "Laptop", "", "not-a-number", 0L)))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("invalid price");
        }

        @Test
        void invalidCategoryId() {
            userOwnsStore();

            assertThatThrownBy(() -> productService.createProduct(userId,
                    new CreateProductRequest("not-a-uuid", "Laptop", "", "15000000", 0L)))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("invalid category_id");
        }

        @Test
        void rejectsAPriceTheColumnCannotHold() {
            userOwnsStore();

            assertThatThrownBy(() -> productService.createProduct(userId,
                    new CreateProductRequest(categoryId.toString(), "Laptop", "", "99999999999999", 0L)))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("price is too large");
        }

        @Test
        void unknownCategory() {
            userOwnsStore();
            when(productRepository.create(any())).thenThrow(TestErrors.foreignKeyViolation());

            assertThatThrownBy(() -> productService.createProduct(userId,
                    new CreateProductRequest(categoryId.toString(), "Laptop", "", "10", 1L)))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("category not found");
        }
    }

    @Nested
    class GetProducts {

        private ProductFilter filter(long page, long perPage) {
            return new ProductFilter("", "", "", "", "", "", "", page, perPage);
        }

        @Test
        void successWithProducts() {
            when(productRepository.findAll(any())).thenReturn(new PageResult<>(List.of(product(storeId),
                    Product.builder().id(UUID.randomUUID()).name("Phone").price(new BigDecimal("5000000")).build()), 2));

            PageResult<ProductResponse> result = productService.getProducts(filter(1, 10));

            assertThat(result.items()).hasSize(2);
            assertThat(result.total()).isEqualTo(2);
        }

        @Test
        void defaultPaginationWhenZero() {
            ArgumentCaptor<ProductFilter> used = ArgumentCaptor.forClass(ProductFilter.class);
            when(productRepository.findAll(used.capture())).thenReturn(new PageResult<>(List.of(), 0));

            assertThat(productService.getProducts(filter(0, 0)).items()).isEmpty();
            assertThat(used.getValue().page()).isEqualTo(1);
            assertThat(used.getValue().perPage()).isEqualTo(10);
        }

        @Test
        void dbError() {
            when(productRepository.findAll(any())).thenThrow(TestErrors.dbError());

            assertThatThrownBy(() -> productService.getProducts(filter(1, 10)))
                    .isInstanceOf(InternalException.class)
                    .hasMessage("failed to fetch products");
        }
    }

    @Nested
    class UpdateProduct {

        @Test
        void stockIsWrittenSeparately() {
            userOwnsStore();
            when(productRepository.findById(productId)).thenReturn(Optional.of(product(storeId)));
            when(productRepository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

            ProductResponse resp = productService.updateProduct(userId, productId,
                    new UpdateProductRequest("", "Gaming Laptop", "", "1200.50", 3L));

            assertThat(resp.name()).isEqualTo("Gaming Laptop");
            assertThat(resp.price()).isEqualByComparingTo("1200.50");
            assertThat(resp.stock()).isEqualTo(3);
            verify(productRepository).updateStock(productId, 3);
        }

        @Test
        void withoutStockTheStockIsNotTouched() {
            userOwnsStore();
            when(productRepository.findById(productId)).thenReturn(Optional.of(product(storeId)));
            when(productRepository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

            productService.updateProduct(userId, productId, new UpdateProductRequest("", "Laptop 2", "", "", null));

            verify(productRepository, never()).updateStock(any(), org.mockito.ArgumentMatchers.anyInt());
        }

        @Test
        void notProductOwner() {
            userOwnsStore();
            when(productRepository.findById(productId)).thenReturn(Optional.of(product(UUID.randomUUID())));

            assertThatThrownBy(() -> productService.updateProduct(userId, productId,
                    new UpdateProductRequest("", "x", "", "", null)))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("forbidden: not product owner");
        }

        @Test
        void negativeStock() {
            userOwnsStore();
            when(productRepository.findById(productId)).thenReturn(Optional.of(product(storeId)));

            assertThatThrownBy(() -> productService.updateProduct(userId, productId,
                    new UpdateProductRequest("", "", "", "", -1L)))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("stock must not be negative");
        }
    }

    @Nested
    class DeleteProduct {

        @Test
        void success() {
            userOwnsStore();
            when(productRepository.findById(productId)).thenReturn(Optional.of(product(storeId)));

            assertThatCode(() -> productService.deleteProduct(userId, productId)).doesNotThrowAnyException();
            verify(productRepository).delete(productId);
        }

        @Test
        void notProductOwner() {
            userOwnsStore();
            when(productRepository.findById(productId)).thenReturn(Optional.of(product(UUID.randomUUID())));

            assertThatThrownBy(() -> productService.deleteProduct(userId, productId))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("forbidden: not product owner");
        }

        @Test
        void productNotFound() {
            userOwnsStore();
            when(productRepository.findById(productId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.deleteProduct(userId, productId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("product not found");
        }

        @Test
        void stillReferencedByOrdersOrCarts() {
            userOwnsStore();
            when(productRepository.findById(productId)).thenReturn(Optional.of(product(storeId)));
            doThrow(TestErrors.foreignKeyViolation()).when(productRepository).delete(productId);

            assertThatThrownBy(() -> productService.deleteProduct(userId, productId))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("product is in use by existing orders or carts");
        }
    }

    /**
     * Prices and stock must fit the DECIMAL(15,2) and INTEGER columns exactly; otherwise
     * PostgreSQL rejects them (a 500) or silently rounds the price.
     */
    @Nested
    class ValidatePriceAndStock {

        @ParameterizedTest
        @CsvSource({
                "0, price must be greater than 0",
                "-1, price must be greater than 0",
                "0.01, ",
                "100.000, ",
                "9999999999999.99, ",
                "10000000000000, price is too large",
                "10.005, price must have at most 2 decimal places",
        })
        void price(String price, String expectedError) {
            if (expectedError == null) {
                assertThatCode(() -> ProductService.validatePrice(new BigDecimal(price))).doesNotThrowAnyException();
            } else {
                assertThatThrownBy(() -> ProductService.validatePrice(new BigDecimal(price))).hasMessage(expectedError);
            }
        }

        @Test
        void stock() {
            assertThatThrownBy(() -> ProductService.validateStock(-1)).hasMessage("stock must not be negative");
            assertThatCode(() -> ProductService.validateStock(0)).doesNotThrowAnyException();
            assertThatCode(() -> ProductService.validateStock(Integer.MAX_VALUE)).doesNotThrowAnyException();
            assertThatThrownBy(() -> ProductService.validateStock(Integer.MAX_VALUE + 1L)).hasMessage("stock is too large");
        }
    }
}
