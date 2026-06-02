package com.windowsense.home;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface HomeRepository extends JpaRepository<Home, UUID> {

    Optional<Home> findByAppUserIdAndName(UUID appUserId, String name);
}
