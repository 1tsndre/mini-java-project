package io.github.tsndre.minijava.store.model;

import io.github.tsndre.minijava.store.constant.Role;
import io.github.tsndre.minijava.store.dto.response.UserResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    private UUID id;
    private String email;
    @ToString.Exclude
    private String password;
    private String name;
    private Role role;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public UserResponse toResponse() {
        return new UserResponse(id, email, name, role, createdAt, updatedAt);
    }
}
