package io.github.tsndre.minijava.store.service;

import io.github.tsndre.minijava.store.constant.Role;
import io.github.tsndre.minijava.store.dto.request.CreateStoreRequest;
import io.github.tsndre.minijava.store.dto.request.UpdateStoreRequest;
import io.github.tsndre.minijava.store.dto.response.StoreResponse;
import io.github.tsndre.minijava.store.model.Store;
import io.github.tsndre.minijava.store.repository.StoreRepository;
import io.github.tsndre.minijava.store.repository.UserRepository;
import io.github.tsndre.minijava.store.service.exception.ConflictException;
import io.github.tsndre.minijava.store.service.exception.ForbiddenException;
import io.github.tsndre.minijava.store.service.exception.InternalException;
import io.github.tsndre.minijava.store.service.exception.NotFoundException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class StoreServiceTest {

    private final UUID storeId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @Mock
    private StoreRepository storeRepository;
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private StoreService storeService;

    private Store ownedStore() {
        return Store.builder().id(storeId).userId(ownerId).name("My Store").build();
    }

    @Nested
    class CreateStore {

        private final CreateStoreRequest req = new CreateStoreRequest("My Store", "A test store");

        @Test
        void success() {
            when(storeRepository.findByUserId(ownerId)).thenReturn(Optional.empty());
            when(storeRepository.create(any())).thenAnswer(invocation -> invocation.getArgument(0));

            StoreResponse resp = storeService.createStore(ownerId, req);

            assertThat(resp.name()).isEqualTo("My Store");
            assertThat(resp.userId()).isEqualTo(ownerId);
            verify(userRepository).updateRole(ownerId, Role.SELLER);
        }

        @Test
        void userAlreadyHasAStore() {
            when(storeRepository.findByUserId(ownerId)).thenReturn(Optional.of(ownedStore()));

            assertThatThrownBy(() -> storeService.createStore(ownerId, req))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("user already has a store");
        }

        @Test
        void createFails() {
            when(storeRepository.findByUserId(ownerId)).thenReturn(Optional.empty());
            when(storeRepository.create(any())).thenThrow(TestErrors.dbError());

            assertThatThrownBy(() -> storeService.createStore(ownerId, req))
                    .isInstanceOf(InternalException.class)
                    .hasMessage("failed to create store");
        }

        @Test
        void concurrentCreateForTheSameUser() {
            when(storeRepository.findByUserId(ownerId)).thenReturn(Optional.empty());
            when(storeRepository.create(any())).thenThrow(TestErrors.duplicateKey());

            assertThatThrownBy(() -> storeService.createStore(ownerId, req))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("user already has a store");
        }

        @Test
        void updateRoleFailsAndTheStoreIsRolledBack() {
            when(storeRepository.findByUserId(ownerId)).thenReturn(Optional.empty());
            when(storeRepository.create(any())).thenReturn(ownedStore());
            doThrow(TestErrors.dbError()).when(userRepository).updateRole(ownerId, Role.SELLER);

            assertThatThrownBy(() -> storeService.createStore(ownerId, req))
                    .isInstanceOf(InternalException.class)
                    .hasMessage("failed to create store");
            verify(storeRepository).delete(storeId);
        }
    }

    @Nested
    class GetStoreById {

        @Test
        void success() {
            when(storeRepository.findById(storeId)).thenReturn(Optional.of(ownedStore()));

            assertThat(storeService.getStoreById(storeId).id()).isEqualTo(storeId);
        }

        @Test
        void storeNotFound() {
            when(storeRepository.findById(storeId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> storeService.getStoreById(storeId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("store not found");
        }

        @Test
        void lookupFailureReadsAsNotFound() {
            when(storeRepository.findById(storeId)).thenThrow(TestErrors.dbError());

            assertThatThrownBy(() -> storeService.getStoreById(storeId))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("store not found");
        }
    }

    @Nested
    class UpdateStore {

        @Test
        void success() {
            when(storeRepository.findById(storeId)).thenReturn(Optional.of(ownedStore()));
            when(storeRepository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

            StoreResponse resp = storeService.updateStore(ownerId, storeId,
                    new UpdateStoreRequest("Updated Store", "Updated desc"));

            assertThat(resp.name()).isEqualTo("Updated Store");
            assertThat(resp.description()).isEqualTo("Updated desc");
        }

        @Test
        void emptyFieldsKeepTheCurrentValues() {
            when(storeRepository.findById(storeId)).thenReturn(Optional.of(ownedStore()));
            when(storeRepository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

            assertThat(storeService.updateStore(ownerId, storeId, new UpdateStoreRequest("", "")).name())
                    .isEqualTo("My Store");
        }

        @Test
        void storeNotFound() {
            when(storeRepository.findById(storeId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> storeService.updateStore(ownerId, storeId, new UpdateStoreRequest("Updated", "")))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("store not found");
        }

        @Test
        void notStoreOwner() {
            when(storeRepository.findById(storeId)).thenReturn(Optional.of(ownedStore()));

            assertThatThrownBy(() -> storeService.updateStore(UUID.randomUUID(), storeId,
                    new UpdateStoreRequest("Updated", "")))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("forbidden: not store owner");
            verify(storeRepository, never()).update(any());
        }

        @Test
        void updateFails() {
            when(storeRepository.findById(storeId)).thenReturn(Optional.of(ownedStore()));
            when(storeRepository.update(any())).thenThrow(TestErrors.dbError());

            assertThatThrownBy(() -> storeService.updateStore(ownerId, storeId, new UpdateStoreRequest("Updated", "")))
                    .isInstanceOf(InternalException.class)
                    .hasMessage("failed to update store");
        }
    }

    @Nested
    class UpdateLogo {

        private static final String LOGO_URL = "https://example.com/logo.png";

        @Test
        void success() {
            when(storeRepository.findById(storeId)).thenReturn(Optional.of(ownedStore()));
            when(storeRepository.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

            assertThat(storeService.updateLogo(ownerId, storeId, LOGO_URL).logoUrl()).isEqualTo(LOGO_URL);
        }

        @Test
        void storeNotFound() {
            when(storeRepository.findById(storeId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> storeService.updateLogo(ownerId, storeId, LOGO_URL))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("store not found");
        }

        @Test
        void notStoreOwner() {
            when(storeRepository.findById(storeId)).thenReturn(Optional.of(ownedStore()));

            assertThatThrownBy(() -> storeService.updateLogo(UUID.randomUUID(), storeId, LOGO_URL))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("forbidden: not store owner");
        }

        @Test
        void updateFails() {
            when(storeRepository.findById(storeId)).thenReturn(Optional.of(ownedStore()));
            when(storeRepository.update(any())).thenThrow(TestErrors.dbError());

            assertThatThrownBy(() -> storeService.updateLogo(ownerId, storeId, LOGO_URL))
                    .isInstanceOf(InternalException.class)
                    .hasMessage("failed to update store logo");
        }
    }
}
