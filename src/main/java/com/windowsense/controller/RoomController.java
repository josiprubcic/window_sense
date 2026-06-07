package com.windowsense.controller;

import com.windowsense.dto.AddVirtualDeviceRequest;
import com.windowsense.dto.AttachPhysicalDeviceTokenRequest;
import com.windowsense.dto.ConnectPhysicalDeviceRequest;
import com.windowsense.dto.CreateRoomRequest;
import com.windowsense.dto.PairPhysicalDeviceRequest;
import com.windowsense.dto.ProvisionPhysicalEspRequest;
import com.windowsense.dto.ProvisionPhysicalEspResponse;
import com.windowsense.dto.RoomAutomationThresholdsResponse;
import com.windowsense.dto.RoomCommandResponse;
import com.windowsense.dto.RoomResponse;
import com.windowsense.dto.RoomSimulationResponse;
import com.windowsense.dto.RoomTelemetryResponse;
import com.windowsense.dto.UpdateRoomRequest;
import com.windowsense.dto.CommandRequest;
import com.windowsense.service.RoomService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
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

    @GetMapping("/{roomId}")
    public RoomResponse getRoom(@PathVariable UUID roomId) {
        return roomService.getRoom(roomId);
    }

    @PutMapping("/{roomId}")
    public RoomResponse updateRoom(@PathVariable UUID roomId, @Valid @RequestBody UpdateRoomRequest request) {
        return roomService.updateRoom(roomId, request);
    }

    @PostMapping("/{roomId}/devices/virtual")
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse addVirtualDevice(@PathVariable UUID roomId, @Valid @RequestBody AddVirtualDeviceRequest request) {
        return roomService.addVirtualDevice(roomId, request);
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

    @PostMapping("/{roomId}/devices/entity")
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse addPhysicalDeviceToEntity(@PathVariable UUID roomId, @Valid @RequestBody PairPhysicalDeviceRequest request) {
        return roomService.addPhysicalDeviceToEntity(roomId, request);
    }

    @PostMapping("/{roomId}/devices/token")
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse addPhysicalDeviceByToken(
            @PathVariable UUID roomId,
            @Valid @RequestBody AttachPhysicalDeviceTokenRequest request
    ) {
        return roomService.addPhysicalDeviceByToken(roomId, request);
    }

    @PostMapping("/{roomId}/devices/provision-physical")
    @ResponseStatus(HttpStatus.CREATED)
    public ProvisionPhysicalEspResponse provisionPhysicalEspDevice(
            @PathVariable UUID roomId,
            @Valid @RequestBody ProvisionPhysicalEspRequest request
    ) {
        return roomService.provisionPhysicalEspDevice(roomId, request);
    }

    @GetMapping("/{roomId}/telemetry/latest")
    public RoomTelemetryResponse latestTelemetry(@PathVariable UUID roomId) {
        return roomService.latestTelemetry(roomId);
    }

    @PostMapping("/{roomId}/commands")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RoomCommandResponse command(@PathVariable UUID roomId, @RequestBody CommandRequest request) {
        return roomService.sendRoomCommand(roomId, request);
    }

    @GetMapping("/{roomId}/simulation")
    public RoomSimulationResponse simulation(@PathVariable UUID roomId) {
        return roomService.simulation(roomId);
    }

    @PatchMapping("/{roomId}/simulation")
    public RoomSimulationResponse updateSimulation(@PathVariable UUID roomId, @RequestBody Map<String, Object> payload) {
        return roomService.updateSimulation(roomId, payload);
    }

    @PatchMapping("/{roomId}/simulation/mode")
    public RoomSimulationResponse updateSimulationMode(@PathVariable UUID roomId, @RequestBody Map<String, Object> payload) {
        return roomService.updateSimulationMode(roomId, payload);
    }

    @GetMapping("/{roomId}/automation/thresholds")
    public RoomAutomationThresholdsResponse automationThresholds(@PathVariable UUID roomId) {
        return roomService.automationThresholds(roomId);
    }

    @PutMapping("/{roomId}/automation/thresholds")
    public RoomAutomationThresholdsResponse updateAutomationThresholds(
            @PathVariable UUID roomId,
            @RequestBody Map<String, Object> payload
    ) {
        return roomService.updateAutomationThresholds(roomId, payload);
    }

    @DeleteMapping("/{roomId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRoom(@PathVariable UUID roomId) {
        roomService.deleteRoom(roomId);
    }

    @DeleteMapping("/{roomId}/devices/{deviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRoomDevice(@PathVariable UUID roomId, @PathVariable UUID deviceId) {
        roomService.deleteRoomDevice(roomId, deviceId);
    }
}
