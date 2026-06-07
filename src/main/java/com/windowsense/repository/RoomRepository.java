package com.windowsense.repository;

import com.windowsense.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {

    boolean existsByHomeIdAndNameIgnoreCase(UUID homeId, String name);

    boolean existsByHomeIdAndNameIgnoreCaseAndIdNot(UUID homeId, String name, UUID id);

    List<Room> findByHomeAppUserIdOrderByNameAsc(UUID appUserId);

    Optional<Room> findByIdAndHomeAppUserId(UUID id, UUID appUserId);
}
