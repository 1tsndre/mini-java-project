package io.github.tsndre.minijava.store.service;

import io.github.tsndre.minijava.store.dto.request.AddCartItemRequest;
import io.github.tsndre.minijava.store.dto.request.UpdateCartItemRequest;
import io.github.tsndre.minijava.store.dto.response.CartResponse;
import io.github.tsndre.minijava.store.model.Cart;
import io.github.tsndre.minijava.store.model.CartItem;
import io.github.tsndre.minijava.store.model.Product;
import io.github.tsndre.minijava.store.repository.CartRepository;
import io.github.tsndre.minijava.store.repository.ProductRepository;
import io.github.tsndre.minijava.store.service.exception.InsufficientStockException;
import io.github.tsndre.minijava.store.service.exception.InternalException;
import io.github.tsndre.minijava.store.service.exception.NotFoundException;
import io.github.tsndre.minijava.store.service.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CartServiceTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    @Mock
    private CartRepository cartRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private CartLock cartLock;
    @InjectMocks
    private CartService cartService;

    @BeforeEach
    void lockAlwaysSucceeds() {
        when(cartLock.lock(any())).thenReturn(() -> { });
    }

    private Cart cart(CartItem... items) {
        Cart cart = new Cart(userId);
        cart.setItems(new ArrayList<>(List.of(items)));
        return cart;
    }

    private CartItem item(UUID id, String name, String price, int quantity) {
        return CartItem.builder().productId(id).name(name).price(price == null ? null : new BigDecimal(price))
                .quantity(quantity).imageUrl("").build();
    }

    private Product product(int stock) {
        return Product.builder().id(productId).name("Test Product").price(new BigDecimal("10000")).stock(stock).build();
    }

    @Nested
    class GetCart {

        /**
         * The stored item is a snapshot from when it was added; checkout charges the current
         * price, so the cart must show the current price too.
         */
        @Test
        void showsTheCurrentProductPriceNotTheStoredSnapshot() {
            when(cartRepository.getCart(userId)).thenReturn(cart(item(productId, "Test Product", "10000", 2)));
            when(productRepository.findById(productId)).thenReturn(Optional.of(
                    Product.builder().id(productId).name("Test Product v2").price(new BigDecimal("12500")).build()));

            CartResponse resp = cartService.getCart(userId);

            assertThat(resp.items()).hasSize(1);
            assertThat(resp.items().getFirst().name()).isEqualTo("Test Product v2");
            assertThat(resp.items().getFirst().price()).isEqualByComparingTo("12500");
            assertThat(resp.total()).isEqualByComparingTo("25000");
        }

        @Test
        void productCannotBeLoadedSoTheStoredValuesAreKept() {
            when(cartRepository.getCart(userId)).thenReturn(cart(item(productId, "Test Product", "10000", 2)));
            when(productRepository.findById(productId)).thenThrow(TestErrors.dbError());

            CartResponse resp = cartService.getCart(userId);

            assertThat(resp.items().getFirst().name()).isEqualTo("Test Product");
            assertThat(resp.total()).isEqualByComparingTo("20000");
        }

        @Test
        void repositoryError() {
            when(cartRepository.getCart(userId)).thenThrow(TestErrors.dbError());

            assertThatThrownBy(() -> cartService.getCart(userId))
                    .isInstanceOf(InternalException.class)
                    .hasMessage("failed to fetch cart");
        }

        @Test
        void emptyCartHasZeroTotalAndGoZeroTime() {
            when(cartRepository.getCart(userId)).thenReturn(cart());

            CartResponse resp = cartService.getCart(userId);

            assertThat(resp.items()).isEmpty();
            assertThat(resp.total()).isEqualByComparingTo("0");
            assertThat(resp.updatedAt()).isEqualTo(Cart.ZERO_TIME);
        }
    }

    @Nested
    class AddItem {

        @Test
        void successNewItem() {
            when(productRepository.findById(productId)).thenReturn(Optional.of(product(10)));
            when(cartRepository.getCart(userId)).thenReturn(cart());

            CartResponse resp = cartService.addItem(userId, new AddCartItemRequest(productId.toString(), 2L));

            assertThat(resp.items()).singleElement().satisfies(i -> assertThat(i.quantity()).isEqualTo(2));
            assertThat(resp.total()).isEqualByComparingTo("20000");
            verify(cartRepository).saveCart(any());
        }

        /** Saving after a failed load would overwrite the user's whole cart. */
        @Test
        void loadCartFailsAndTheCartIsNotOverwritten() {
            when(productRepository.findById(productId)).thenReturn(Optional.of(product(10)));
            when(cartRepository.getCart(userId)).thenThrow(TestErrors.dbError());

            assertThatThrownBy(() -> cartService.addItem(userId, new AddCartItemRequest(productId.toString(), 1L)))
                    .isInstanceOf(InternalException.class)
                    .hasMessage("failed to load cart");
            verify(cartRepository, never()).saveCart(any());
        }

        @Test
        void successExistingItemIncremented() {
            when(productRepository.findById(productId)).thenReturn(Optional.of(product(10)));
            when(cartRepository.getCart(userId)).thenReturn(cart(item(productId, "Test Product", "10000", 2)));
            ArgumentCaptor<Cart> saved = ArgumentCaptor.forClass(Cart.class);

            cartService.addItem(userId, new AddCartItemRequest(productId.toString(), 1L));

            verify(cartRepository).saveCart(saved.capture());
            assertThat(saved.getValue().getItems()).singleElement()
                    .satisfies(i -> assertThat(i.getQuantity()).isEqualTo(3));
        }

        @Test
        void insufficientStockForExistingPlusNewQuantity() {
            when(productRepository.findById(productId)).thenReturn(Optional.of(product(3)));
            when(cartRepository.getCart(userId)).thenReturn(cart(item(productId, null, null, 2)));

            assertThatThrownBy(() -> cartService.addItem(userId, new AddCartItemRequest(productId.toString(), 2L)))
                    .isInstanceOf(InsufficientStockException.class)
                    .hasMessage("insufficient stock");
        }

        @Test
        void invalidProductId() {
            assertThatThrownBy(() -> cartService.addItem(userId, new AddCartItemRequest("not-a-uuid", 1L)))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("invalid product_id");
        }

        @Test
        void quantityZero() {
            assertThatThrownBy(() -> cartService.addItem(userId, new AddCartItemRequest(productId.toString(), 0L)))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("quantity must be greater than 0");
        }

        @Test
        void productNotFound() {
            when(productRepository.findById(productId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.addItem(userId, new AddCartItemRequest(productId.toString(), 1L)))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("product not found");
        }

        @Test
        void insufficientStock() {
            when(productRepository.findById(productId)).thenReturn(Optional.of(product(3)));

            assertThatThrownBy(() -> cartService.addItem(userId, new AddCartItemRequest(productId.toString(), 5L)))
                    .isInstanceOf(InsufficientStockException.class)
                    .hasMessage("insufficient stock");
            verify(cartLock, never()).lock(any());
        }

        @Test
        void saveCartFails() {
            when(productRepository.findById(productId)).thenReturn(Optional.of(product(10)));
            when(cartRepository.getCart(userId)).thenReturn(cart());
            doThrow(TestErrors.dbError()).when(cartRepository).saveCart(any());

            assertThatThrownBy(() -> cartService.addItem(userId, new AddCartItemRequest(productId.toString(), 1L)))
                    .isInstanceOf(InternalException.class)
                    .hasMessage("failed to save cart");
        }

        @Test
        void lockNotAcquired() {
            when(productRepository.findById(productId)).thenReturn(Optional.of(product(10)));
            when(cartLock.lock(userId)).thenThrow(new InternalException("failed to acquire cart lock, please try again"));

            assertThatThrownBy(() -> cartService.addItem(userId, new AddCartItemRequest(productId.toString(), 1L)))
                    .isInstanceOf(InternalException.class)
                    .hasMessage("failed to acquire cart lock, please try again");
            verify(cartRepository, never()).getCart(any());
        }
    }

    @Nested
    class UpdateItem {

        @Test
        void success() {
            when(cartRepository.getCart(userId)).thenReturn(cart(item(productId, "Test Product", "10000", 1)));
            when(productRepository.findById(productId)).thenReturn(Optional.of(product(10)));

            CartResponse resp = cartService.updateItem(userId, productId, new UpdateCartItemRequest(3L));

            assertThat(resp.items().getFirst().quantity()).isEqualTo(3);
        }

        @Test
        void insufficientStock() {
            when(cartRepository.getCart(userId)).thenReturn(cart(item(productId, "Test Product", "10000", 1)));
            when(productRepository.findById(productId)).thenReturn(Optional.of(product(3)));

            assertThatThrownBy(() -> cartService.updateItem(userId, productId, new UpdateCartItemRequest(5L)))
                    .isInstanceOf(InsufficientStockException.class)
                    .hasMessage("insufficient stock");
        }

        @Test
        void quantityZero() {
            assertThatThrownBy(() -> cartService.updateItem(userId, productId, new UpdateCartItemRequest(0L)))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("quantity must be greater than 0");
        }

        @Test
        void cartCannotBeLoaded() {
            when(cartRepository.getCart(userId)).thenThrow(TestErrors.dbError());

            assertThatThrownBy(() -> cartService.updateItem(userId, productId, new UpdateCartItemRequest(1L)))
                    .isInstanceOf(InternalException.class)
                    .hasMessage("failed to load cart");
        }

        @Test
        void itemNotInCart() {
            when(cartRepository.getCart(userId)).thenReturn(cart(item(productId, "Test Product", "10000", 1)));

            assertThatThrownBy(() -> cartService.updateItem(userId, UUID.randomUUID(), new UpdateCartItemRequest(1L)))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("item not found in cart");
        }

        @Test
        void saveFails() {
            when(cartRepository.getCart(userId)).thenReturn(cart(item(productId, "Test Product", "10000", 1)));
            when(productRepository.findById(productId)).thenReturn(Optional.of(product(10)));
            doThrow(TestErrors.dbError()).when(cartRepository).saveCart(any());

            assertThatThrownBy(() -> cartService.updateItem(userId, productId, new UpdateCartItemRequest(2L)))
                    .isInstanceOf(InternalException.class)
                    .hasMessage("failed to save cart");
        }
    }

    @Nested
    class RemoveItem {

        @Test
        void success() {
            when(cartRepository.getCart(userId)).thenReturn(cart(item(productId, "Test Product", "10000", 1)));

            assertThat(cartService.removeItem(userId, productId).items()).isEmpty();
        }

        @Test
        void cartCannotBeLoaded() {
            when(cartRepository.getCart(userId)).thenThrow(TestErrors.dbError());

            assertThatThrownBy(() -> cartService.removeItem(userId, productId))
                    .isInstanceOf(InternalException.class)
                    .hasMessage("failed to load cart");
        }

        @Test
        void itemNotInCart() {
            when(cartRepository.getCart(userId)).thenReturn(cart(item(productId, "Test Product", "10000", 1)));

            assertThatThrownBy(() -> cartService.removeItem(userId, UUID.randomUUID()))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("item not found in cart");
        }

        @Test
        void saveFails() {
            when(cartRepository.getCart(userId)).thenReturn(cart(item(productId, "Test Product", "10000", 1)));
            doThrow(TestErrors.dbError()).when(cartRepository).saveCart(any());

            assertThatThrownBy(() -> cartService.removeItem(userId, productId))
                    .isInstanceOf(InternalException.class)
                    .hasMessage("failed to save cart");
        }
    }
}
