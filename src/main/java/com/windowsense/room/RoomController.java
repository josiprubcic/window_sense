package com.windowsense.room;

import com.windowsense.room.dto.ConnectPhysicalDeviceRequest;
import com.windowsense.room.dto.CreateRoomRequest;
import com.windowsense.room.dto.PairPhysicalDeviceRequest;
import com.windowsense.room.dto.RoomResponse;
import com.windowsense.room.dto.RoomTelemetryResponse;
import com.windowsense.room.dto.UpdateRoomRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping
    public List<RoomResponse> listRooms() {
        return roomService.listCurrentUserRooms();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse createRoom(@Valid @RequestBody CreateRoomRequest request) {
        return roomService.createRoom(request);
    }

    @PutMapping("/{roomId}")
    public RoomResponse updateRoom(@PathVariable UUID roomId, @Valid @RequestBody UpdateRoomRequest request) {
        return roomService.updateRoom(roomId, request);
    }

    @PostMapping("/{roomId}/devices/physical")
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse connectPhysicalDevice(@PathVariable UUID roomId, @Valid @RequestBody ConnectPhysicalDeviceRequest request) {
        return roomService.connectPhysicalDevice(roomId, request);
    }

    @PostMapping("/{roomId}/devices/pair")
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse pairPhysicalDevice(@PathVariable UUID roomId, @Valid @RequestBody PairPhysicalDeviceRequest request) {
        return roomService.pairPhysicalDevice(roomId, request);
    }

    @GetMapping("/{roomId}/telemetry/latest")
    public RoomTelemetryResponse latestTelemetry(@PathVariable UUID roomId) {
        return roomService.latestTelemetry(roomId);
    }

    @DeleteMapping("/{roomId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRoom(@PathVariable UUID roomId) {
        roomService.deleteRoom(roomId);
    }
}
