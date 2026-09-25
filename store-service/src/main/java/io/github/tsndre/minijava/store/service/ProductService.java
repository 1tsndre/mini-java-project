package io.github.tsndre.minijava.store.service;

import io.github.tsndre.minijava.store.dto.request.CreateProductRequest;
import io.github.tsndre.minijava.store.dto.request.UpdateProductRequest;
import io.github.tsndre.minijava.store.dto.response.ProductResponse;
import io.github.tsndre.minijava.store.model.Product;
import io.github.tsndre.minijava.store.model.ProductFilter;
import io.github.tsndre.minijava.store.model.Store;
import io.github.tsndre.minijava.store.pagination.Pages;
import io.github.tsndre.minijava.store.repository.ForeignKeyViolationException;
import io.github.tsndre.minijava.store.repository.PageResult;
import io.github.tsndre.minijava.store.repository.ProductRepository;
import io.github.tsndre.minijava.store.repository.StoreRepository;
import io.github.tsndre.minijava.store.service.exception.ConflictException;
import io.github.tsndre.minijava.store.service.exception.ForbiddenException;
import io.github.tsndre.minijava.store.service.exception.InternalException;
import io.github.tsndre.minijava.store.service.exception.NotFoundException;
import io.github.tsndre.minijava.store.service.exception.ValidationException;
import io.github.tsndre.minijava.store.util.GoDecimal;
import io.github.tsndre.minijava.store.util.Uuids;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    /** The smallest price that no longer fits the DECIMAL(15,2) columns. */
    static final BigDecimal MAX_PRICE = BigDecimal.TEN.pow(13);
    private static final int PRICE_SCALE = 2;

    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;

    /** Rejects prices PostgreSQL would refuse or silently round, so the stored price is exactly the one sent. */
    static void validatePrice(BigDecimal price) {
        if (price.signum() <= 0) {
            throw new ValidationException("price must be greater than 0");
        }
        if (price.compareTo(MAX_PRICE) >= 0) {
            throw new ValidationException("price is too large");
        }
        if (price.stripTrailingZeros().scale() > PRICE_SCALE) {
            throw new ValidationException("price must have at most 2 decimal places");
        }
    }

    /** Rejects stock values the INTEGER column cannot hold. */
    static void validateStock(long stock) {
        if (stock < 0) {
            throw new ValidationException("stock must not be negative");
        }
        if (stock > Integer.MAX_VALUE) {
            throw new ValidationException("stock is too large");
        }
    }

    private static BigDecimal parsePrice(String price) {
        BigDecimal parsed = GoDecimal.parse(price).orElseThrow(() -> new ValidationException("invalid price"));
        validatePrice(parsed);
        return parsed;
    }

    private static UUID parseCategoryId(String categoryId) {
        return Uuids.parse(categoryId).orElseThrow(() -> new ValidationException("invalid category_id"));
    }

    public ProductResponse createProduct(UUID userId, CreateProductRequest req) {
        Store store = getStoreByOwner(userId);
        UUID categoryId = parseCategoryId(req.categoryId());
        BigDecimal price = parsePrice(req.price());
        validateStock(req.stock());

        Product product = Product.builder()
                .storeId(store.getId())
                .categoryId(categoryId)
                .name(req.name())
                .description(req.description())
                .price(price)
                .stock(req.stock().intValue())
                .build();

        try {
            product = productRepository.create(product);
        } catch (ForeignKeyViolationException e) {
            throw new NotFoundException("category not found");
        } catch (RuntimeException e) {
            log.error("failed to create product", e);
            throw new InternalException("failed to create product");
        }

        log.atInfo()
                .addKeyValue("product_id", product.getId())
                .addKeyValue("store_id", store.getId())
                .log("product created");

        return product.toResponse();
    }

    public PageResult<ProductResponse> getProducts(ProductFilter filter) {
        Pages.Page page = Pages.normalize(filter.page(), filter.perPage());
        filter = filter.withPage(page.page(), page.perPage());

        PageResult<Product> products;
        try {
            products = productRepository.findAll(filter);
        } catch (RuntimeException e) {
            log.error("failed to fetch products", e);
            throw new InternalException("failed to fetch products");
        }

        return new PageResult<>(products.items().stream().map(Product::toResponse).toList(), products.total());
    }

    public ProductResponse getProductById(UUID id) {
        return findProduct(id).toResponse();
    }

    public ProductResponse updateProduct(UUID userId, UUID id, UpdateProductRequest req) {
        Store store = getStoreByOwner(userId);
        Product product = findProduct(id);

        if (!product.getStoreId().equals(store.getId())) {
            throw new ForbiddenException("forbidden: not product owner");
        }

        if (!req.name().isEmpty()) {
            product.setName(req.name());
        }
        if (!req.description().isEmpty()) {
            product.setDescription(req.description());
        }
        if (!req.price().isEmpty()) {
            product.setPrice(parsePrice(req.price()));
        }
        if (!req.categoryId().isEmpty()) {
            product.setCategoryId(parseCategoryId(req.categoryId()));
        }
        if (req.stock() != null) {
            validateStock(req.stock());
        }

        try {
            product = productRepository.update(product);
        } catch (ForeignKeyViolationException e) {
            throw new NotFoundException("category not found");
        } catch (RuntimeException e) {
            log.error("failed to update product", e);
            throw new InternalException("failed to update product");
        }

        // Stock is written on its own, never through update, so a stale product read above
        // cannot overwrite the atomic decrements made by concurrent checkouts.
        if (req.stock() != null) {
            try {
                productRepository.updateStock(id, req.stock().intValue());
            } catch (RuntimeException e) {
                log.error("failed to update product stock", e);
                throw new InternalException("failed to update product stock");
            }
            product.setStock(req.stock().intValue());
        }

        return product.toResponse();
    }

    public void deleteProduct(UUID userId, UUID id) {
        Store store = getStoreByOwner(userId);
        Product product = findProduct(id);

        if (!product.getStoreId().equals(store.getId())) {
            throw new ForbiddenException("forbidden: not product owner");
        }

        try {
            productRepository.delete(id);
        } catch (ForeignKeyViolationException e) {
            throw new ConflictException("product is in use by existing orders or carts");
        } catch (RuntimeException e) {
            log.error("failed to delete product", e);
            throw new InternalException("failed to delete product");
        }
    }

    public ProductResponse updateImage(UUID userId, UUID id, String imageUrl) {
        Store store = getStoreByOwner(userId);
        Product product = findProduct(id);

        if (!product.getStoreId().equals(store.getId())) {
            throw new ForbiddenException("forbidden: not product owner");
        }

        product.setImageUrl(imageUrl);
        try {
            product = productRepository.update(product);
        } catch (RuntimeException e) {
            log.error("failed to update product image", e);
            throw new InternalException("failed to update product image");
        }

        return product.toResponse();
    }

    private Store getStoreByOwner(UUID userId) {
        try {
            return storeRepository.findByUserId(userId).orElseThrow();
        } catch (RuntimeException e) {
            throw new NotFoundException("store not found for this user");
        }
    }

    private Product findProduct(UUID id) {
        try {
            return productRepository.findById(id).orElseThrow();
        } catch (RuntimeException e) {
            throw new NotFoundException("product not found");
        }
    }
}
