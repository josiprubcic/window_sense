package com.windowsense;

import com.windowsense.auth.CurrentUserService;
import com.windowsense.common.ConflictException;
import com.windowsense.common.ThingsBoardProvisioningException;
import com.windowsense.device.DeviceStatus;
import com.windowsense.device.DeviceType;
import com.windowsense.device.PhysicalDeviceRegistryRepository;
import com.windowsense.device.WindowDevice;
import com.windowsense.device.WindowDeviceRepository;
import com.windowsense.home.Home;
import com.windowsense.home.HomeRepository;
import com.windowsense.room.Room;
import com.windowsense.room.RoomMapper;
import com.windowsense.room.RoomRepository;
import com.windowsense.room.RoomService;
import com.windowsense.room.dto.ConnectPhysicalDeviceRequest;
import com.windowsense.room.dto.CreateRoomRequest;
import com.windowsense.room.dto.RoomResponse;
import com.windowsense.room.dto.WindowDeviceResponse;
import com.windowsense.security.EncryptionService;
import com.windowsense.thingsboard.ExistingPhysicalDeviceLinkRequest;
import com.windowsense.thingsboard.ProvisionedRoomDevice;
import com.windowsense.thingsboard.ThingsBoardProvisioningService;
import com.windowsense.thingsboard.ThingsBoardTelemetryQueryService;
import com.windowsense.thingsboard.VirtualRoomDeprovisioningRequest;
import com.windowsense.thingsboard.VirtualRoomProvisioningRequest;
import com.windowsense.user.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    private final CurrentUserService currentUserService = org.mockito.Mockito.mock(CurrentUserService.class);
    private final HomeRepository homeRepository = org.mockito.Mockito.mock(HomeRepository.class);
    private final RoomRepository roomRepository = org.mockito.Mockito.mock(RoomRepository.class);
    private final WindowDeviceRepository windowDeviceRepository = org.mockito.Mockito.mock(WindowDeviceRepository.class);
    private final PhysicalDeviceRegistryRepository physicalDeviceRegistryRepository = org.mockito.Mockito.mock(PhysicalDeviceRegistryRepository.class);
    private final ThingsBoardProvisioningService provisioningService = org.mockito.Mockito.mock(ThingsBoardProvisioningService.class);
    private final ThingsBoardTelemetryQueryService telemetryQueryService = org.mockito.Mockito.mock(ThingsBoardTelemetryQueryService.class);
    private final EncryptionService encryptionService = org.mockito.Mockito.mock(EncryptionService.class);
    private final RoomService roomService = new RoomService(
            currentUserService,
            homeRepository,
            roomRepository,
            windowDeviceRepository,
            physicalDeviceRegistryRepository,
            provisioningService,
            telemetryQueryService,
            encryptionService,
            new RoomMapper()
    );

    @Test
    void createRoomCallsProvisioningAndPersistsThingsBoardIds() {
        UUID userId = UUID.randomUUID();
        UUID homeId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        AppUser user = new AppUser("auth0|window-user", "user@example.com", "Window User");
        ReflectionTestUtils.setField(user, "id", userId);
        Home home = new Home(user, "Default Home");
        ReflectionTestUtils.setField(home, "id", homeId);

        when(currentUserService.getOrCreateCurrentUser()).thenReturn(user);
        when(homeRepository.findByAppUserIdAndName(userId, "Default Home")).thenReturn(Optional.of(home));
        when(roomRepository.existsByHomeIdAndNameIgnoreCase(homeId, "Kuhinja")).thenReturn(false);
        when(roomRepository.saveAndFlush(any(Room.class))).thenAnswer(invocation -> {
            Room room = invocation.getArgument(0);
            ReflectionTestUtils.setField(room, "id", roomId);
            return room;
        });
        when(provisioningService.provisionVirtualRoomDevice(any(VirtualRoomProvisioningRequest.class)))
                .thenReturn(new ProvisionedRoomDevice("tb-asset-real", "tb-device-real", "plain-device-token"));
        when(encryptionService.encrypt("plain-device-token")).thenReturn("encrypted-device-token");

        RoomResponse response = roomService.createRoom(new CreateRoomRequest("Kuhinja"));

        assertThat(response.id()).isEqualTo(roomId);
        assertThat(response.tbAssetId()).isEqualTo("tb-asset-real");
        assertThat(response.devices()).hasSize(1);
        assertThat(response.devices().getFirst().tbDeviceId()).isEqualTo("tb-device-real");
        assertThat(Arrays.stream(WindowDeviceResponse.class.getRecordComponents()).map(RecordComponent::getName))
                .doesNotContain("tbDeviceAccessToken", "tbDeviceToken", "tbDeviceTokenEncrypted");

        ArgumentCaptor<VirtualRoomProvisioningRequest> requestCaptor = ArgumentCaptor.forClass(VirtualRoomProvisioningRequest.class);
        verify(provisioningService).provisionVirtualRoomDevice(requestCaptor.capture());
        assertThat(requestCaptor.getValue().roomId()).isEqualTo(roomId);
        assertThat(requestCaptor.getValue().roomName()).isEqualTo("Kuhinja");
        assertThat(requestCaptor.getValue().deviceName()).isEqualTo("WindowSense - Kuhinja");
        assertThat(requestCaptor.getValue().appUserId()).isEqualTo(userId);
        assertThat(requestCaptor.getValue().auth0Sub()).isEqualTo("auth0|window-user");
        verify(encryptionService).encrypt("plain-device-token");
        ArgumentCaptor<Room> roomCaptor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).saveAndFlush(roomCaptor.capture());
        assertThat(roomCaptor.getValue().getDevices().getFirst().getTbDeviceTokenEncrypted()).isEqualTo("encrypted-device-token");
    }

    @Test
    void createRoomWithNoOpProvisioningTokenDoesNotRequireEncryptionKey() {
        UUID userId = UUID.randomUUID();
        UUID homeId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        AppUser user = new AppUser("auth0|window-user", "user@example.com", "Window User");
        ReflectionTestUtils.setField(user, "id", userId);
        Home home = new Home(user, "Default Home");
        ReflectionTestUtils.setField(home, "id", homeId);

        when(currentUserService.getOrCreateCurrentUser()).thenReturn(user);
        when(homeRepository.findByAppUserIdAndName(userId, "Default Home")).thenReturn(Optional.of(home));
        when(roomRepository.existsByHomeIdAndNameIgnoreCase(homeId, "Dnevni boravak")).thenReturn(false);
        when(roomRepository.saveAndFlush(any(Room.class))).thenAnswer(invocation -> {
            Room room = invocation.getArgument(0);
            ReflectionTestUtils.setField(room, "id", roomId);
            return room;
        });
        when(provisioningService.provisionVirtualRoomDevice(any(VirtualRoomProvisioningRequest.class)))
                .thenReturn(new ProvisionedRoomDevice("tb-asset-mock", "tb-device-mock", null));

        RoomResponse response = roomService.createRoom(new CreateRoomRequest("Dnevni boravak"));

        assertThat(response.id()).isEqualTo(roomId);
        assertThat(response.devices().getFirst().tbDeviceId()).isEqualTo("tb-device-mock");
        verify(encryptionService, never()).encrypt(any());
        ArgumentCaptor<Room> roomCaptor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).saveAndFlush(roomCaptor.capture());
        assertThat(roomCaptor.getValue().getDevices().getFirst().getTbDeviceTokenEncrypted()).isNull();
    }

    @Test
    void deleteRoomWithMockThingsBoardIdsDeletesLocalRoomWithoutDeprovisioning() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        AppUser user = user(userId);
        Room room = room(roomId, "Spavaca soba", "tb-asset-mock", "tb-device-mock");

        when(currentUserService.getOrCreateCurrentUser()).thenReturn(user);
        when(roomRepository.findByIdAndHomeAppUserId(roomId, userId)).thenReturn(Optional.of(room));

        roomService.deleteRoom(roomId);

        verify(provisioningService, never()).deprovisionVirtualRoom(any(VirtualRoomDeprovisioningRequest.class));
        verify(roomRepository).delete(room);
    }

    @Test
    void deleteRoomWithRealThingsBoardIdsCallsDeprovisioningBeforeLocalDelete() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        AppUser user = user(userId);
        Room room = room(roomId, "Kuhinja", "real-asset-id", "real-device-id");

        when(currentUserService.getOrCreateCurrentUser()).thenReturn(user);
        when(roomRepository.findByIdAndHomeAppUserId(roomId, userId)).thenReturn(Optional.of(room));

        roomService.deleteRoom(roomId);

        ArgumentCaptor<VirtualRoomDeprovisioningRequest> requestCaptor = ArgumentCaptor.forClass(VirtualRoomDeprovisioningRequest.class);
        var ordered = inOrder(provisioningService, roomRepository);
        ordered.verify(provisioningService).deprovisionVirtualRoom(requestCaptor.capture());
        ordered.verify(roomRepository).delete(room);

        assertThat(requestCaptor.getValue().roomId()).isEqualTo(roomId);
        assertThat(requestCaptor.getValue().roomName()).isEqualTo("Kuhinja");
        assertThat(requestCaptor.getValue().tbAssetId()).isEqualTo("real-asset-id");
        assertThat(requestCaptor.getValue().tbDeviceId()).isEqualTo("real-device-id");
    }

    @Test
    void deleteRoomKeepsLocalRoomWhenDeprovisioningFails() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        AppUser user = user(userId);
        Room room = room(roomId, "Kuhinja", "real-asset-id", "real-device-id");

        when(currentUserService.getOrCreateCurrentUser()).thenReturn(user);
        when(roomRepository.findByIdAndHomeAppUserId(roomId, userId)).thenReturn(Optional.of(room));
        doThrow(new ThingsBoardProvisioningException("ThingsBoard deprovisioning nije uspio."))
                .when(provisioningService)
                .deprovisionVirtualRoom(any(VirtualRoomDeprovisioningRequest.class));

        assertThatThrownBy(() -> roomService.deleteRoom(roomId))
                .isInstanceOf(ThingsBoardProvisioningException.class)
                .hasMessage("ThingsBoard deprovisioning nije uspio.");

        verify(roomRepository, never()).delete(any(Room.class));
    }

    @Test
    void connectPhysicalDeviceCreatesActivePhysicalDeviceWithoutToken() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        AppUser user = user(userId);
        Room room = room(roomId, "Kuhinja", "real-asset-id", "real-virtual-device-id");

        when(currentUserService.getOrCreateCurrentUser()).thenReturn(user);
        when(roomRepository.findByIdAndHomeAppUserId(roomId, userId)).thenReturn(Optional.of(room));
        when(windowDeviceRepository.existsByRoomIdAndDeviceTypeAndStatus(roomId, DeviceType.PHYSICAL, DeviceStatus.ACTIVE))
                .thenReturn(false);
        when(roomRepository.saveAndFlush(room)).thenReturn(room);

        RoomResponse response = roomService.connectPhysicalDevice(
                roomId,
                new ConnectPhysicalDeviceRequest("ESP32 - Fizicki prototip", "existing-physical-device-id", null)
        );

        assertThat(response.devices()).hasSize(2);
        WindowDevice physical = room.getDevices().get(1);
        assertThat(physical.getName()).isEqualTo("ESP32 - Fizicki prototip");
        assertThat(physical.getDeviceType()).isEqualTo(DeviceType.PHYSICAL);
        assertThat(physical.isVirtual()).isFalse();
        assertThat(physical.getStatus()).isEqualTo(DeviceStatus.ACTIVE);
        assertThat(physical.getTbDeviceId()).isEqualTo("existing-physical-device-id");
        assertThat(physical.getTbDeviceTokenEncrypted()).isNull();
        verify(encryptionService, never()).encrypt(any());

        ArgumentCaptor<ExistingPhysicalDeviceLinkRequest> requestCaptor = ArgumentCaptor.forClass(ExistingPhysicalDeviceLinkRequest.class);
        verify(provisioningService).linkExistingPhysicalDevice(requestCaptor.capture());
        assertThat(requestCaptor.getValue().roomId()).isEqualTo(roomId);
        assertThat(requestCaptor.getValue().tbAssetId()).isEqualTo("real-asset-id");
        assertThat(requestCaptor.getValue().tbDeviceId()).isEqualTo("existing-physical-device-id");
        assertThat(requestCaptor.getValue().appUserId()).isEqualTo(userId);
    }

    @Test
    void connectPhysicalDeviceReturnsConflictWhenActivePhysicalDeviceAlreadyExists() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        AppUser user = user(userId);
        Room room = room(roomId, "Kuhinja", "real-asset-id", "real-virtual-device-id");

        when(currentUserService.getOrCreateCurrentUser()).thenReturn(user);
        when(roomRepository.findByIdAndHomeAppUserId(roomId, userId)).thenReturn(Optional.of(room));
        when(windowDeviceRepository.existsByRoomIdAndDeviceTypeAndStatus(roomId, DeviceType.PHYSICAL, DeviceStatus.ACTIVE))
                .thenReturn(true);

        assertThatThrownBy(() -> roomService.connectPhysicalDevice(
                roomId,
                new ConnectPhysicalDeviceRequest("ESP32", "existing-physical-device-id", null)
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Soba vec ima aktivni fizicki uredjaj.");

        verify(roomRepository, never()).saveAndFlush(any(Room.class));
        verify(provisioningService, never()).linkExistingPhysicalDevice(any(ExistingPhysicalDeviceLinkRequest.class));
    }

    @Test
    void latestTelemetryForMockDeviceReturnsEmptyResponseWithoutThingsBoardCall() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        AppUser user = user(userId);
        Room room = room(roomId, "Kuhinja", "tb-asset-mock", "tb-device-mock");

        when(currentUserService.getOrCreateCurrentUser()).thenReturn(user);
        when(roomRepository.findByIdAndHomeAppUserId(roomId, userId)).thenReturn(Optional.of(room));

        var response = roomService.latestTelemetry(roomId);

        assertThat(response.roomId()).isEqualTo(roomId);
        assertThat(response.roomName()).isEqualTo("Kuhinja");
        assertThat(response.telemetry()).isEmpty();
        assertThat(response.message()).contains("mock ThingsBoard");
        verify(telemetryQueryService, never()).latestDeviceTelemetry(any());
    }

    @Test
    void latestTelemetryForRealDeviceCallsThingsBoardQueryService() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        Instant updatedAt = Instant.parse("2026-06-03T10:15:30Z");
        AppUser user = user(userId);
        Room room = room(roomId, "Kuhinja", "real-asset-id", "real-device-id");

        when(currentUserService.getOrCreateCurrentUser()).thenReturn(user);
        when(roomRepository.findByIdAndHomeAppUserId(roomId, userId)).thenReturn(Optional.of(room));
        when(telemetryQueryService.latestDeviceTelemetry("real-device-id"))
                .thenReturn(new ThingsBoardTelemetryQueryService.LatestTelemetry(
                        java.util.Map.of("rainDetected", true, "lux", 42000),
                        updatedAt
                ));

        var response = roomService.latestTelemetry(roomId);

        assertThat(response.telemetry()).containsEntry("rainDetected", true);
        assertThat(response.telemetry()).containsEntry("lux", 42000);
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
        assertThat(response.message()).isNull();
        verify(telemetryQueryService).latestDeviceTelemetry("real-device-id");
    }

    @Test
    void latestTelemetryPrefersActivePhysicalDeviceWhenRoomHasOne() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        Instant updatedAt = Instant.parse("2026-06-03T10:15:30Z");
        AppUser user = user(userId);
        Room room = room(roomId, "Kuhinja", "real-asset-id", "real-virtual-device-id");
        room.addDevice(WindowDevice.physicalDevice("ESP32 - Fizicki prototip", "existing-physical-device-id"));

        when(currentUserService.getOrCreateCurrentUser()).thenReturn(user);
        when(roomRepository.findByIdAndHomeAppUserId(roomId, userId)).thenReturn(Optional.of(room));
        when(telemetryQueryService.latestDeviceTelemetry("existing-physical-device-id"))
                .thenReturn(new ThingsBoardTelemetryQueryService.LatestTelemetry(
                        java.util.Map.of("rainDetected", false, "lux", 1200),
                        updatedAt
                ));

        var response = roomService.latestTelemetry(roomId);

        assertThat(response.deviceName()).isEqualTo("ESP32 - Fizicki prototip");
        assertThat(response.telemetry()).containsEntry("rainDetected", false);
        assertThat(response.telemetry()).containsEntry("lux", 1200);
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
        verify(telemetryQueryService).latestDeviceTelemetry("existing-physical-device-id");
        verify(telemetryQueryService, never()).latestDeviceTelemetry("real-virtual-device-id");
    }

    @Test
    void latestTelemetryReturnsNotFoundForForeignRoom() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        AppUser user = user(userId);

        when(currentUserService.getOrCreateCurrentUser()).thenReturn(user);
        when(roomRepository.findByIdAndHomeAppUserId(roomId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.latestTelemetry(roomId))
                .isInstanceOf(com.windowsense.common.ResourceNotFoundException.class)
                .hasMessage("Soba nije pronadjena.");

        verify(telemetryQueryService, never()).latestDeviceTelemetry(any());
    }

    private static AppUser user(UUID userId) {
        AppUser user = new AppUser("auth0|window-user", "user@example.com", "Window User");
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private static Room room(UUID roomId, String roomName, String tbAssetId, String tbDeviceId) {
        Room room = new Room(new Home(user(UUID.randomUUID()), "Default Home"), roomName, tbAssetId);
        ReflectionTestUtils.setField(room, "id", roomId);
        room.addDevice(WindowDevice.virtualDevice("WindowSense - " + roomName, tbDeviceId));
        return room;
    }
}
