package com.chalo.repository;

import com.chalo.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // Loads interests in the same query — used before building recommendations
    @EntityGraph(attributePaths = "interests")
    Optional<User> findWithInterestsById(Long id);
}
