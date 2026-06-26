package com.linkup.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    /** All users except the given id — for the conversation-creation picker. */
    List<User> findByIdNot(UUID id);
}
