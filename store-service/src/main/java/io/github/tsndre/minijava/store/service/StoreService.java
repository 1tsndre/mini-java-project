package io.github.tsndre.minijava.store.service;

import io.github.tsndre.minijava.store.constant.Role;
import io.github.tsndre.minijava.store.dto.request.CreateStoreRequest;
import io.github.tsndre.minijava.store.dto.request.UpdateStoreRequest;
import io.github.tsndre.minijava.store.dto.response.StoreResponse;
import io.github.tsndre.minijava.store.model.Store;
import io.github.tsndre.minijava.store.repository.StoreRepository;
import io.github.tsndre.minijava.store.repository.UniqueViolationException;
import io.github.tsndre.minijava.store.repository.UserRepository;
import io.github.tsndre.minijava.store.service.exception.ConflictException;
import io.github.tsndre.minijava.store.service.exception.ForbiddenException;
import io.github.tsndre.minijava.store.service.exception.InternalException;
import io.github.tsndre.minijava.store.service.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreService {

    private final StoreRepository storeRepository;
    private final UserRepository userRepository;

    public StoreResponse createStore(UUID userId, CreateStoreRequest req) {
        if (findByUserId(userId).isPresent()) {
            throw new ConflictException("user already has a store");
        }

        Store store = Store.builder()
                .userId(userId)
                .name(req.name())
                .description(req.description())
                .build();

        try {
            store = storeRepository.create(store);
        } catch (UniqueViolationException e) {
            // A concurrent request created this user's store after the check above.
            throw new ConflictException("user already has a store");
        } catch (RuntimeException e) {
            log.error("failed to create store", e);
            throw new InternalException("failed to create store");
        }

        try {
            userRepository.updateRole(userId, Role.SELLER);
        } catch (RuntimeException e) {
            try {
                storeRepository.delete(store.getId());
            } catch (RuntimeException rollbackError) {
                log.atError()
                        .setCause(rollbackError)
                        .addKeyValue("store_id", store.getId())
                        .log("failed to rollback store after role update failure");
            }
            log.error("failed to update user role", e);
            throw new InternalException("failed to create store");
        }

        log.atInfo()
                .addKeyValue("store_id", store.getId())
                .addKeyValue("user_id", userId)
                .log("store created");

        return store.toResponse();
    }

    public StoreResponse getStoreById(UUID id) {
        return findStore(id).toResponse();
    }

    public StoreResponse updateStore(UUID userId, UUID id, UpdateStoreRequest req) {
        Store store = findStore(id);

        if (!store.getUserId().equals(userId)) {
            throw new ForbiddenException("forbidden: not store owner");
        }

        if (!req.name().isEmpty()) {
            store.setName(req.name());
        }
        if (!req.description().isEmpty()) {
            store.setDescription(req.description());
        }

        try {
            store = storeRepository.update(store);
        } catch (RuntimeException e) {
            log.error("failed to update store", e);
            throw new InternalException("failed to update store");
        }

        return store.toResponse();
    }

    public StoreResponse updateLogo(UUID userId, UUID id, String logoUrl) {
        Store store = findStore(id);

        if (!store.getUserId().equals(userId)) {
            throw new ForbiddenException("forbidden: not store owner");
        }

        store.setLogoUrl(logoUrl);
        try {
            store = storeRepository.update(store);
        } catch (RuntimeException e) {
            log.error("failed to update store logo", e);
            throw new InternalException("failed to update store logo");
        }

        return store.toResponse();
    }

    /** Any lookup failure reads as "not found", like the Go service. */
    private Store findStore(UUID id) {
        try {
            return storeRepository.findById(id).orElseThrow();
        } catch (RuntimeException e) {
            throw new NotFoundException("store not found");
        }
    }

    private Optional<Store> findByUserId(UUID userId) {
        try {
            return storeRepository.findByUserId(userId);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
