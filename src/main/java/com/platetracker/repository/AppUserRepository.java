package com.platetracker.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.platetracker.domain.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    @EntityGraph(attributePaths = "employee")
    Optional<AppUser> findByUsername(String username);

    @EntityGraph(attributePaths = "employee")
    @Override
    List<AppUser> findAll(Sort sort);

    boolean existsByUsername(String username);
}
