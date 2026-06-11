package com.windowsense.service;

import com.windowsense.security.CurrentUserService;
import com.windowsense.service.AutomationService;
import com.windowsense.exception.ThingsBoardProvisioningException;
import com.windowsense.entity.DeviceCapability;
import com.windowsense.entity.DeviceStatus;
import com.windowsense.entity.DeviceType;
import com.windowsense.service.PhysicalDevicePairingCodeHasher;
import com.windowsense.entity.PhysicalDeviceRegistry;
import com.windowsense.repository.PhysicalDeviceRegistryRepository;
import com.windowsense.repository.WindowDeviceRepository;
import com.windowsense.entity.PhysicalDeviceRegistryStatus;
import com.windowsense.service.PhysicalDeviceSecretHasher;
import com.windowsense.entity.WindowDevice;
import com.windowsense.entity.Home;
import com.windowsense.repository.HomeRepository;
import com.windowsense.entity.Room;
import com.windowsense.mapper.RoomMapper;
import com.windowsense.service.RoomAutomationEvaluator;
import com.windowsense.service.RoomDeviceSelector;
import com.windowsense.repository.RoomRepository;
import com.windowsense.service.RoomService;
import com.windowsense.dto.AddVirtualDeviceRequest;
import com.windowsense.dto.AttachPhysicalDeviceTokenRequest;
import com.windowsense.dto.ConnectPhysicalDeviceRequest;
import com.windowsense.dto.CreateRoomRequest;
import com.windowsense.dto.PairPhysicalDeviceRequest;
import com.windowsense.dto.RoomResponse;
import com.windowsense.dto.RoomCommandResponse;
import com.windowsense.dto.WindowDeviceResponse;
import com.windowsense.security.EncryptionService;
import com.windowsense.service.CommandService;
import com.windowsense.service.CommandDeliveryPort;
import com.windowsense.mapper.TelemetryKeyMapper;
import com.windowsense.integration.thingsboard.ExistingPhysicalDeviceLinkRequest;
import com.windowsense.integration.thingsboard.ProvisionedRoomAsset;
import com.windowsense.integration.thingsboard.ProvisionedRoomDevice;
import com.windowsense.integration.thingsboard.RoomAssetProvisioningRequest;
import com.windowsense.integration.thingsboard.RoomAssetDeprovisioningRequest;
import com.windowsense.integration.thingsboard.RoomDeviceDeprovisioningRequest;
import com.windowsense.integration.thingsboard.ThingsBoardProvisioningService;
import com.windowsense.integration.thingsboard.ThingsBoardTelemetryQueryService;
import com.windowsense.integration.thingsboard.VirtualRoomProvisioningRequest;
import com.windowsense.entity.AppUser;
import com.windowsense.service.TelemetryPublisher;
import com.windowsense.service.VirtualThingsBoardRpcCommandDeliveryPort;
import com.windowsense.dto.CommandRequest;
import com.windowsense.service.CommandResult;
import com.windowsense.entity.RuntimeState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
    private final PhysicalDeviceRegistryRepository physicalDeviceRegistryRepository = org.mockito.Mockito.mock(PhysicalDeviceRegistryRepository.class);
    private final ThingsBoardProvisioningService provisioningService = org.mockito.Mockito.mock(ThingsBoardProvisioningService.class);
    private final ThingsBoardTelemetryQueryService telemetryQueryService = org.mockito.Mockito.mock(ThingsBoardTelemetryQueryService.class);
    private final EncryptionService encryptionService = org.mockito.Mockito.mock(EncryptionService.class);
    private final CommandService commandService = org.mockito.Mockito.mock(CommandService.class);
    private final CommandDeliveryPort commandDeliveryPort = org.mockito.Mockito.mock(CommandDeliveryPort.class);
    private final TelemetryPublisher telemetryPublisher = org.mockito.Mockito.mock(TelemetryPublisher.class);
    private final VirtualThingsBoardRpcCommandDeliveryPort virtualRpcCommandDeliveryPort = org.mockito.Mockito.mock(VirtualThingsBoardRpcCommandDeliveryPort.class);
    private final WindowDeviceRepository windowDeviceRepository = org.mockito.Mockito.mock(WindowDeviceRepository.class);
    private final RoomService roomService = new RoomService(
            currentUserService,
            homeRepository,
            roomRepository,
            windowDeviceRepository,
            physicalDeviceRegistryRepository,
            provisioningService,
            telemetryQueryService,
            encryptionService,
            commandService,
            commandDeliveryPort,
            new TelemetryKeyMapper(),
            new RoomAutomationEvaluator(new AutomationService(), commandService),
            new RoomDeviceSelector(),
            new RoomMapper(),
            telemetryPublisher,
            virtualRpcCommandDeliveryPort
    );

    @AfterEach
    void resetAutomationEngine() {
        automationProperties().setEngine(com.windowsense.config.WindowSenseProperties.AutomationEngine.BACKEND);
    }

    @Test
    void createRoomCreatesOnlyRoomWithoutProvisioningDevice() {
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
        when(provisioningService.provisionRoomAsset(any(RoomAssetProvisioningRequest.class)))
                .thenReturn(new ProvisionedRoomAsset("tb-asset-room"));
        RoomResponse response = roomService.createRoom(new CreateRoomRequest("Kuhinja"));

        assertThat(response.id()).isEqualTo(roomId);
        assertThat(response.tbAssetId()).isEqualTo("tb-asset-room");
        assertThat(response.activeDevice()).isNull();
        assertThat(response.devices()).isEmpty();
        ArgumentCaptor<Room> roomCaptor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository, org.mockito.Mockito.times(2)).saveAndFlush(roomCaptor.capture());
        assertThat(roomCaptor.getValue().getDevices()).isEmpty();
        ArgumentCaptor<RoomAssetProvisioningRequest> assetRequestCaptor = ArgumentCaptor.forClass(RoomAssetProvisioningRequest.class);
        verify(provisioningService).provisionRoomAsset(assetRequestCaptor.capture());
        assertThat(assetRequestCaptor.getValue().roomId()).isEqualTo(roomId);
        assertThat(assetRequestCaptor.getValue().roomName()).isEqualTo("Kuhinja");
        assertThat(assetRequestCaptor.getValue().appUserId()).isEqualTo(userId);
        verify(provisioningService, never()).provisionVirtualRoomDevice(any(VirtualRoomProvisioningRequest.class));
        verify(encryptionService, never()).encrypt(any());
    }

    @Test
    void addVirtualDeviceCallsProvisioningAndPersistsEncryptedToken() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        AppUser user = user(userId);
        Room room = emptyRoom(roomId, "Dnevni boravak", "tb-asset-room");

        when(currentUserService.getOrCreateCurrentUser()).thenReturn(user);
        when(roomRepository.findByIdAndHomeAppUserId(roomId, userId)).thenReturn(Optional.of(room));
        when(provisioningService.provisionVirtualRoomDevice(any(VirtualRoomProvisioningRequest.class)))
                .thenReturn(new ProvisionedRoomDevice("tb-asset-real", "tb-device-real", "plain-device-token"));
        when(encryptionService.encrypt("plain-device-token")).thenReturn("encrypted-device-token");
        when(roomRepository.saveAndFlush(room)).thenReturn(room);

        RoomResponse response = roomService.addVirtualDevice(
                roomId,
                new AddVirtualDeviceRequest("Virtualni uredjaj", List.of("window"))
        );

        assertThat(response.id()).isEqualTo(roomId);
        assertThat(response.tbAssetId()).isEqualTo("tb-asset-real");
        assertThat(response.activeDevice()).isNotNull();
        assertThat(response.devices().getFirst().tbDeviceId()).isEqualTo("tb-device-real");
        assertThat(response.devices().getFirst().capabilities()).contains(DeviceCapability.WINDOW_CONTROL.name());
        assertThat(response.devices().getFirst().capabilities()).doesNotContain(DeviceCapability.BLINDS_CONTROL.name());
        assertThat(Arrays.stream(WindowDeviceResponse.class.getRecordComponents()).map(RecordComponent::getName))
                .doesNotContain("tbDeviceAccessToken", "tbDeviceToken", "tbDeviceTokenEncrypted");

        ArgumentCaptor<VirtualRoomProvisioningRequest> requestCaptor = ArgumentCaptor.forClass(VirtualRoomProvisioningRequest.class);
        verify(provisioningService).provisionVirtualRoomDevice(requestCaptor.capture());
        assertThat(requestCaptor.getValue().roomId()).isEqualTo(roomId);
        assertThat(requestCaptor.getValue().roomName()).isEqualTo("Dnevni boravak");
        assertThat(requestCaptor.getValue().tbAssetId()).isEqualTo("tb-asset-room");
        assertThat(requestCaptor.getValue().deviceName()).isEqualTo("Virtualni uredjaj");
        assertThat(requestCaptor.getValue().appUserId()).isEqualTo(userId);
        assertThat(requestCaptor.getValue().auth0Sub()).isEqualTo("auth0|window-user");
        verify(encryptionService).encrypt("plain-device-token");
        assertThat(room.getDevices().getFirst().getTbDeviceTokenEncrypted()).isEqualTo("encrypted-device-token");
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

        verify(provisioningService, never()).deprovisionRoomDevice(any(RoomDeviceDeprovisioningRequest.class));
        verify(provisioningService, never()).deprovisionRoomAsset(any(RoomAssetDeprovisioningRequest.class));
        verify(roomRepository).delete(room);
    }

    @Test
    void deleteRoomWithRealThingsBoardIdsDeletesDevicesThenAssetBeforeLocalDelete() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        AppUser user = user(userId);
        Room room = room(roomId, "Kuhinja", "real-asset-id", "real-device-id");
        room.addDevice(WindowDevice.physicalDevice("ESP32 - Kuhinja", "real-physical-device-id", "WS-SN-DEL-1", com.windowsense.entity.DeviceCapabilities.combinedDevice()));
        PhysicalDeviceRegistry registry = new PhysicalDeviceRegistry(
                "WS-SN-DEL-1",
                PhysicalDevicePairingCodeHasher.hash("WS-DEMO-DEL-1"),
                "real-physical-device-id",
                PhysicalDeviceRegistryStatus.CLAIMED
        );

        when(currentUserService.getOrCreateCurrentUser()).thenReturn(user);
        when(roomRepository.findByIdAndHomeAppUserId(roomId, userId)).thenReturn(Optional.of(room));
        when(physicalDeviceRegistryRepository.findBySerialNumber("WS-SN-DEL-1")).thenReturn(Optional.of(registry));

        roomService.deleteRoom(roomId);

        ArgumentCaptor<RoomDeviceDeprovisioningRequest> deviceCaptor = ArgumentCaptor.forClass(RoomDeviceDeprovisioningRequest.class);
        ArgumentCaptor<RoomAssetDeprovisioningRequest> assetCaptor = ArgumentCaptor.forClass(RoomAssetDeprovisioningRequest.class);
        var ordered = inOrder(provisioningService, physicalDeviceRegistryRepository, roomRepository);
        ordered.verify(provisioningService, org.mockito.Mockito.times(2)).deprovisionRoomDevice(deviceCaptor.capture());
        ordered.verify(provisioningService).deprovisionRoomAsset(assetCaptor.capture());
        ordered.verify(physicalDeviceRegistryRepository).delete(registry);
        ordered.verify(roomRepository).delete(room);

        assertThat(deviceCaptor.getAllValues()).extracting(RoomDeviceDeprovisioningRequest::tbDeviceId)
                .containsExactly("real-device-id", "real-physical-device-id");
        assertThat(deviceCaptor.getAllValues()).allSatisfy(request -> {
            assertThat(request.roomId()).isEqualTo(roomId);
            assertThat(request.roomName()).isEqualTo("Kuhinja");
            assertThat(request.tbAssetId()).isEqualTo("real-asset-id");
        });
        assertThat(assetCaptor.getValue().roomId()).isEqualTo(roomId);
        assertThat(assetCaptor.getValue().tbAssetId()).isEqualTo("real-asset-id");
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
                .deprovisionRoomDevice(any(RoomDeviceDeprovisioningRequest.class));

        assertThatThrownBy(() -> roomService.deleteRoom(roomId))
                .isInstanceOf(ThingsBoardProvisioningException.class)
                .hasMessage("ThingsBoard deprovisioning nije uspio.");

        verify(provisioningService, never()).deprovisionRoomAsset(any(RoomAssetDeprovisioningRequest.class));
        verify(roomRepository, never()).delete(any(Room.class));
    }

    @Test
    void connectPhysicalDeviceCreatesActivePhysicalDeviceWithoutToken() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        AppUser user = user(userId);
        Room room = emptyRoom(roomId, "Kuhinja");

        when(currentUserService.getOrCreateCurrentUser()).thenReturn(user);
        when(roomRepository.findByIdAndHomeAppUserId(roomId, userId)).thenReturn(Optional.of(room));
        when(roomRepository.saveAndFlush(room)).thenReturn(room);

        RoomResponse response = roomService.connectPhysicalDevice(
                roomId,
                new ConnectPhysicalDeviceRequest("ESP32 - Fizicki prototip", "existing-physical-device-id", null, List.of("blinds"))
        );

        assertThat(response.devices()).hasSize(1);
        WindowDevice physical = room.getDevices().getFirst();
        assertThat(physical.getName()).isEqualTo("ESP32 - Fizicki prototip");
        assertThat(physical.getDeviceType()).isEqualTo(DeviceType.PHYSICAL);
        assertThat(physical.isVirtual()).isFalse();
        assertThat(physical.getStatus()).isEqualTo(DeviceStatus.ACTIVE);
        assertThat(physical.getTbDeviceId()).isEqualTo("existing-physical-device-id");
        assertThat(physical.getTbDeviceTokenEncrypted()).isNull();
        assertThat(physical.getCapabilities()).contains(DeviceCapability.BLINDS_CONTROL);
        assertThat(physical.getCapabilities()).doesNotContain(DeviceCapability.WINDOW_CONTROL);
        verify(encryptionService, never()).encrypt(any());

        ArgumentCaptor<ExistingPhysicalDeviceLinkRequest> requestCaptor = ArgumentCaptor.forClass(ExistingPhysicalDeviceLinkRequest.class);
        verify(provisioningService).linkExistingPhysicalDevice(requestCaptor.capture());
        assertThat(requestCaptor.getValue().roomId()).isEqualTo(roomId);
        assertThat(requestCaptor.getValue().tbAssetId()).isNull();
        assertThat(requestCaptor.getValue().tbDeviceId()).isEqualTo("existing-physical-device-id");
        assertThat(requestCaptor.getValue().appUserId()).isEqualTo(userId);
    }

    @Test
    void connectPhysicalDeviceAllowsMultipleActiveDevicesInSameRoom() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        AppUser user = user(userId);
        Room room = room(roomId, "Kuhinja", "real-asset-id", "real-virtual-device-id");

        when(currentUserService.getOrCreateCurrentUser()).thenReturn(user);
        when(roomRepository.findByIdAndHomeAppUserId(roomId, userId)).thenReturn(Optional.of(room));
        when(roomRepository.saveAndFlush(room)).thenReturn(room);

        RoomResponse response = roomService.connectPhysicalDevice(
                roomId,
                new ConnectPhysicalDeviceRequest("ESP32", "existing-physical-device-id", null)
        );

        assertThat(response.devices()).hasSize(2);
        assertThat(response.devices()).extracting(WindowDeviceResponse::tbDeviceId)
                .contains("real-virtual-device-id", "existing-physical-device-id");
        verify(roomRepository).saveAndFlush(room);
        verify(provisioningService).linkExistingPhysicalDevice(any(ExistingPhysicalDeviceLinkRequest.class));
    }

    @Test
    void pairPhysicalDeviceClaimsRegistryDeviceWithoutThingsBoardTokenOrFallbackLink() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        AppUser user = user(userId);
        Room room = emptyRoom(roomId, "Dnevni boravak");
        PhysicalDeviceRegistry registryDevice = new PhysicalDeviceRegistry(
                "WS-SN-1001",
                PhysicalDevicePairingCodeHasher.hash("WS-DEMO-1001"),
                "tb-device-physical-1001",
                PhysicalDeviceRegistryStatus.CLAIMABLE
        );

        when(currentUserService.getOrCreateCurrentUser()).thenReturn(user);
        when(roomRepository.findByIdAndHomeAppUserId(roomId, userId)).thenReturn(Optional.of(room));
        when(physicalDeviceRegistryRepository.findBySerialNumber("WS-SN-1001"))
                .thenReturn(Optional.of(registryDevice));
        when(roomRepository.saveAndFlush(room)).thenReturn(room);

        RoomResponse response = roomService.pairPhysicalDevice(
                roomId,
                new PairPhysicalDeviceRequest("ESP32 - Dnevni boravak", "WS-SN-1001", " ws-demo-1001 ")
        );

        assertThat(response.devices()).hasSize(1);
        WindowDevice physical = room.getDevices().getFirst();
        assertThat(physical.getName()).isEqualTo("ESP32 - Dnevni boravak");
        assertThat(physical.getDeviceType()).isEqualTo(DeviceType.PHYSICAL);
        assertThat(physical.isVirtual()).isFalse();
        assertThat(physical.getStatus()).isEqualTo(DeviceStatus.ACTIVE);
        assertThat(physical.getTbDeviceId()).isEqualTo("tb-device-physical-1001");
        assertThat(physical.getPhysicalHardwareId()).isEqualTo("WS-SN-1001");
        assertThat(physical.getTbDeviceTokenEncrypted()).isNull();
        assertThat(registryDevice.getStatus()).isEqualTo(PhysicalDeviceRegistryStatus.CLAIMED);
        assertThat(registryDevice.getClaimedByUserId()).isEqualTo(userId);
        assertThat(registryDevice.getClaimedRoomId()).isEqualTo(roomId);
        assertThat(registryDevice.getClaimedAt()).isNotNull();
        assertThat(registryDevice.getPairingCodeConsumedAt()).isNotNull();
        verify(encryptionService, never()).encrypt(any());
        verify(provisioningService, never()).linkExistingPhysicalDevice(any(ExistingPhysicalDeviceLinkRequest.class));
    }

    @Test
    void addPhysicalDeviceToEntityClaimsRegistryDeviceAndCreatesThingsBoardRelation() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        AppUser user = user(userId);
        Room room = emptyRoom(roomId, "Dnevni boravak");
        PhysicalDeviceRegistry registryDevice = new PhysicalDeviceRegistry(
                "WS-SN-1001",
                PhysicalDevicePairingCodeHasher.hash("WS-DEMO-1001"),
                "tb-device-physical-1001",
                PhysicalDeviceRegistryStatus.CLAIMABLE
        );

        when(currentUserService.getOrCreateCurrentUser()).thenReturn(user);
        when(roomRepository.findByIdAndHomeAppUserId(roomId, userId)).thenReturn(Optional.of(room));
        when(physicalDeviceRegistryRepository.findBySerialNumber("WS-SN-1001"))
                .thenReturn(Optional.of(registryDevice));
        when(roomRepository.saveAndFlush(room)).thenReturn(room);

        RoomResponse response = roomService.addPhysicalDeviceToEntity(
                roomId,
                new PairPhysicalDeviceRequest("ESP32 - Dnevni boravak", "WS-SN-1001", "WS-DEMO-1001")
        );

        assertThat(response.devices()).hasSize(1);
        assertThat(registryDevice.getStatus()).isEqualTo(PhysicalDeviceRegistryStatus.CLAIMED);
        assertThat(registryDevice.getClaimedByUserId()).isEqualTo(userId);
        assertThat(registryDevice.getClaimedRoomId()).isEqualTo(roomId);

        ArgumentCaptor<ExistingPhysicalDeviceLinkRequest> requestCaptor = ArgumentCaptor.forClass(ExistingPhysicalDeviceLinkRequest.class);
        verify(provisioningService).linkExistingPhysicalDevice(requestCaptor.capture());
        assertThat(requestCaptor.getValue().roomId()).isEqualTo(roomId);
        assertThat(requestCaptor.getValue().tbAssetId()).isNull();
        assertThat(requestCaptor.getValue().tbDeviceId()).isEqualTo("tb-device-physical-1001");
        assertThat(requestCaptor.getValue().deviceName()).isEqualTo("ESP32 - Dnevni boravak");
        assertThat(requestCaptor.getValue().appUserId()).isEqualTo(userId);
    }

    @Test
    void addPhysicalDeviceByTokenUsesRegistrySerialAndCapabilities() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        AppUser user = user(userId);
        Room room = emptyRoom(roomId, "Dnevni boravak");
        PhysicalDeviceRegistry registryDevice = new PhysicalDeviceRegistry(
                "WS-BLINDS-0001",
                PhysicalDevicePairingCodeHasher.hash("WS-DEMO-BLINDS"),
                "tb-device-blinds-0001",
                PhysicalDeviceRegistryStatus.CLAIMABLE
        );
        registryDevice.setThingsBoardAccessTokenHash(PhysicalDeviceSecretHasher.hash("roleta-demo-token"));
        registryDevice.updateRegistrationMetadata(
                "esp32-blinds-chip",
                "demo-1.0.0",
                "BLINDS_CONTROL",
                List.of(DeviceCapability.BLINDS_CONTROL)
        );

        when(currentUserService.getOrCreateCurrentUser()).thenReturn(user);
        when(roomRepository.findByIdAndHomeAppUserId(roomId, userId)).thenReturn(Optional.of(room));
        when(physicalDeviceRegistryRepository.findByThingsBoardAccessTokenHash(PhysicalDeviceSecretHasher.hash("roleta-demo-token")))
                .thenReturn(Optional.of(registryDevice));
        when(roomRepository.saveAndFlush(room)).thenReturn(room);

        RoomResponse response = roomService.addPhysicalDeviceByToken(
                roomId,
                new AttachPhysicalDeviceTokenRequest("Roleta dnevna", " roleta-demo-token ")
        );

        assertThat(response.devices()).hasSize(1);
        WindowDevice physical = room.getDevices().getFirst();
        assertThat(physical.getName()).isEqualTo("Roleta dnevna");
        assertThat(physical.getTbDeviceId()).isEqualTo("tb-device-blinds-0001");
        assertThat(physical.getPhysicalHardwareId()).isEqualTo("WS-BLINDS-0001");
        assertThat(physical.getCapabilities()).containsExactly(DeviceCapability.BLINDS_CONTROL);
        assertThat(registryDevice.getStatus()).isEqualTo(PhysicalDeviceRegistryStatus.CLAIMED);
        assertThat(registryDevice.getClaimedByUserId()).isEqualTo(userId);
        assertThat(registryDevice.getClaimedRoomId()).isEqualTo(roomId);

        ArgumentCaptor<ExistingPhysicalDeviceLinkRequest> requestCaptor = ArgumentCaptor.forClass(ExistingPhysicalDeviceLinkRequest.class);
        verify(provisioningService).linkExistingPhysicalDevice(requestCaptor.capture());
        assertThat(requestCaptor.getValue().tbDeviceId()).isEqualTo("tb-device-blinds-0001");
        assertThat(requestCaptor.getValue().deviceName()).isEqualTo("Roleta dnevna");
    }

    @Test
    void latestTelemetryForMockDeviceReturnsVirtualSimulationStateWithoutThingsBoardCall() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        AppUser user = user(userId);
        Room room = room(roomId, "Kuhinja", "tb-asset-mock", "tb-device-mock");

        when(currentUserService.getOrCreateCurrentUser()).thenReturn(user);
        when(roomRepository.findByIdAndHomeAppUserId(roomId, userId)).thenReturn(Optional.of(room));

        var response = roomService.latestTelemetry(roomId);

        assertThat(response.roomId()).isEqualTo(roomId);
        assertThat(response.roomName()).isEqualTo("Kuhinja");
        assertThat(response.deviceType()).isEqualTo(DeviceType.VIRTUAL.name());
        assertThat(response.isVirtual()).isTrue();
        assertThat(response.telemetry())
                .containsEntry("rainDetected", false)
                .containsEntry("rainRiskPercent", 12.0);
        assertThat(response.message()).isNull();
        verify(telemetryQueryService, never()).latestDeviceTelemetry(any());
    }

    @Test
    void latestTelemetryForVirtualDeviceReturnsLocalSimulationState() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        AppUser user = user(userId);
        Room room = room(roomId, "Kuhinja", "real-asset-id", "real-device-id");

        when(currentUserService.getOrCreateCurrentUser()).thenReturn(user);
        when(roomRepository.findByIdAndHomeAppUserId(roomId, userId)).thenReturn(Optional.of(room));

        var response = roomService.latestTelemetry(roomId);

        assertThat(response.deviceType()).isEqualTo(DeviceType.VIRTUAL.name());
        assertThat(response.isVirtual()).isTrue();
        assertThat(response.telemetry()).containsEntry("rainRiskPercent", 12.0);
        assertThat(response.updatedAt()).isNotNull();
        assertThat(response.message()).isNull();
        verify(telemetryQueryService, never()).latestDeviceTelemetry("real-device-id");
    }

    @Test
    @Tag("core")
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
                        java.util.Map.of("rainDetected", false),
                        updatedAt
                ));

        var response = roomService.latestTelemetry(roomId);

        assertThat(response.deviceName()).isEqualTo("ESP32 - Fizicki prototip");
        assertThat(response.deviceType()).isEqualTo(DeviceType.PHYSICAL.name());
        assertThat(response.isVirtual()).isFalse();
        assertThat(response.telemetry()).containsEntry("rainDetected", false);
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
        verify(telemetryQueryService).latestDeviceTelemetry("existing-physical-device-id");
        verify(telemetryQueryService, never()).latestDeviceTelemetry("real-virtual-device-id");
    }

    @Test
    void latestTelemetryForPhysicalDeviceWithoutTelemetryDoesNotFallBackToVirtualSimulation() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        AppUser user = user(userId);
        Room room = room(roomId, "Kuhinja", "real-asset-id", "real-virtual-device-id");
        room.addDevice(WindowDevice.physicalDevice("ESP32 - Fizicki prototip", "existing-physical-device-id"));

        when(currentUserService.getOrCreateCurrentUser()).thenReturn(user);
        when(roomRepository.findByIdAndHomeAppUserId(roomId, userId)).thenReturn(Optional.of(room));
        when(telemetryQueryService.latestDeviceTelemetry("existing-physical-device-id"))
                .thenReturn(new ThingsBoardTelemetryQueryService.LatestTelemetry(java.util.Map.of(), null));

        var response = roomService.latestTelemetry(roomId);

        assertThat(response.deviceName()).isEqualTo("ESP32 - Fizicki prototip");
        assertThat(response.deviceType()).isEqualTo(DeviceType.PHYSICAL.name());
        assertThat(response.isVirtual()).isFalse();
        assertThat(response.telemetry()).isEmpty();
        assertThat(response.updatedAt()).isNull();
        assertThat(response.message()).contains("Fizicki uredjaj jos ne salje telemetriju");
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
                .isInstanceOf(com.windowsense.exception.ResourceNotFoundException.class)
                .hasMessage("Soba nije pronadjena.");

        verify(telemetryQueryService, never()).latestDeviceTelemetry(any());
    }

    @Test
    void updateSimulationPublishesVirtualTelemetryToThingsBoardPublisher() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        AppUser user = user(userId);
        Room room = room(roomId, "Kuhinja", "real-asset-id", "real-device-id");
        WindowDevice device = room.getDevices().getFirst();
        ReflectionTestUtils.setField(device, "id", UUID.randomUUID());

        when(currentUserService.getOrCreateCurrentUser()).thenReturn(user);
        when(roomRepository.findByIdAndHomeAppUserId(roomId, userId)).thenReturn(Optional.of(room));

        roomService.updateSimulation(roomId, Map.of(
                "rainDetected", true,
                "rainIntensity", 47,
                "rainProbability", 79,
                "lux", 12300,
                "indoorTempC", 22.7,
                "windKmh", 31,
                "windowOpenPercent", 26,
                "blindClosedPercent", 86
        ));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(telemetryPublisher).publishTelemetry(org.mockito.Mockito.eq(device), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue())
                .containsEntry("rainDetected", true)
                .containsEntry("rainIntensity", 47.0)
                .containsEntry("rainRiskPercent", 79.0)
                .containsEntry("windowOpenPercent", 0.0)
                .containsEntry("simulationMode", "MANUAL");
    }

    @Test
    void updateSimulationPublishesCapabilityScopedTelemetryForSeparateVirtualDevices() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        AppUser user = user(userId);
        Room room = emptyRoom(roomId, "Dnevni boravak", "real-asset-id");
        WindowDevice window = WindowDevice.virtualDevice(
                "Virtualni prozor",
                "tb-window-id",
                java.util.Set.of(DeviceCapability.WINDOW_CONTROL)
        );
        WindowDevice blinds = WindowDevice.virtualDevice(
                "Virtualni roleta",
                "tb-blinds-id",
                java.util.Set.of(DeviceCapability.BLINDS_CONTROL)
        );
        ReflectionTestUtils.setField(window, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(blinds, "id", UUID.randomUUID());
        room.addDevice(window);
        room.addDevice(blinds);

        when(currentUserService.getOrCreateCurrentUser()).thenReturn(user);
        when(roomRepository.findByIdAndHomeAppUserId(roomId, userId)).thenReturn(Optional.of(room));

        roomService.updateSimulation(roomId, Map.of(
                "rainDetected", false,
                "rainIntensity", 0,
                "rainProbability", 31,
                "lux", 29000,
                "indoorTempC", 22.5,
                "windKmh", 16,
                "windowOpenPercent", 72,
                "blindClosedPercent", 20
        ));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<WindowDevice> deviceCaptor = ArgumentCaptor.forClass(WindowDevice.class);
        verify(telemetryPublisher, org.mockito.Mockito.times(2)).publishTelemetry(deviceCaptor.capture(), payloadCaptor.capture());

        Map<WindowDevice, Map<String, Object>> payloads = new java.util.LinkedHashMap<>();
        for (int index = 0; index < deviceCaptor.getAllValues().size(); index++) {
            payloads.put(deviceCaptor.getAllValues().get(index), payloadCaptor.getAllValues().get(index));
        }

        assertThat(payloads.get(window))
                .containsEntry("windowOpenPercent", 72.0)
                .doesNotContainKey("blindClosedPercent");
        assertThat(payloads.get(blinds))
                .containsEntry("blindClosedPercent", 85.0)
                .doesNotContainKey("windowOpenPercent");
    }

    @Test
    @Tag("core")
    void updateSimulationWithWeatherOnlyPayloadPreservesSeparateVirtualDevicePositions() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        AppUser user = user(userId);
        automationProperties().setEngine(com.windowsense.config.WindowSenseProperties.AutomationEngine.THINGSBOARD_RULE_CHAIN);
        Room room = emptyRoom(roomId, "Dnevni boravak", "real-asset-id");
        WindowDevice window = WindowDevice.virtualDevice(
                "Virtualni prozor",
                "tb-window-id",
                java.util.Set.of(DeviceCapability.WINDOW_CONTROL)
        );
        WindowDevice blinds = WindowDevice.virtualDevice(
                "Virtualni roleta",
                "tb-blinds-id",
                java.util.Set.of(DeviceCapability.BLINDS_CONTROL)
        );
        ReflectionTestUtils.setField(window, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(blinds, "id", UUID.randomUUID());
        window.updateSimulationTelemetry(false, 0, 10, 52000, 23.5, 8, 64, 20, 1);
        blinds.updateSimulationTelemetry(false, 0, 10, 52000, 23.5, 8, 72, 85, 1);
        room.addDevice(window);
        room.addDevice(blinds);

        when(currentUserService.getOrCreateCurrentUser()).thenReturn(user);
        when(roomRepository.findByIdAndHomeAppUserId(roomId, userId)).thenReturn(Optional.of(room));

        roomService.updateSimulation(roomId, Map.of(
                "rainDetected", true,
                "rainIntensity", 70,
                "rainProbability", 80,
                "day", 0
        ));

        assertThat(window.getSimWindowOpenPercent()).isEqualTo(64.0);
        assertThat(blinds.getSimBlindClosedPercent()).isEqualTo(85.0);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<WindowDevice> deviceCaptor = ArgumentCaptor.forClass(WindowDevice.class);
        verify(telemetryPublisher, org.mockito.Mockito.times(2)).publishTelemetry(deviceCaptor.capture(), payloadCaptor.capture());

        Map<WindowDevice, Map<String, Object>> payloads = new java.util.LinkedHashMap<>();
        for (int index = 0; index < deviceCaptor.getAllValues().size(); index++) {
            payloads.put(deviceCaptor.getAllValues().get(index), payloadCaptor.getAllValues().get(index));
        }

        assertThat(payloads.get(window))
                .containsEntry("windowOpenPercent", 64.0)
                .doesNotContainKey("blindClosedPercent");
        assertThat(payloads.get(blinds))
                .containsEntry("blindClosedPercent", 85.0)
                .doesNotContainKey("windowOpenPercent");
    }

    @Test
    void updateSimulationModeAppliesToAllVirtualDevicesInRoom() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        AppUser user = user(userId);
        Room room = emptyRoom(roomId, "Dnevni boravak", "real-asset-id");
        WindowDevice window = WindowDevice.virtualDevice("Virtualni prozor", "tb-window-id", java.util.Set.of(DeviceCapability.WINDOW_CONTROL));
        WindowDevice blinds = WindowDevice.virtualDevice("Virtualni roleta", "tb-blinds-id", java.util.Set.of(DeviceCapability.BLINDS_CONTROL));
        window.updateSimulationMode(com.windowsense.entity.SimulationMode.MANUAL);
        blinds.updateSimulationMode(com.windowsense.entity.SimulationMode.MANUAL);
        room.addDevice(window);
        room.addDevice(blinds);

        when(currentUserService.getOrCreateCurrentUser()).thenReturn(user);
        when(roomRepository.findByIdAndHomeAppUserId(roomId, userId)).thenReturn(Optional.of(room));

        roomService.updateSimulationMode(roomId, Map.of("mode", "AUTO"));

        assertThat(window.getSimulationMode()).isEqualTo(com.windowsense.entity.SimulationMode.AUTO);
        assertThat(blinds.getSimulationMode()).isEqualTo(com.windowsense.entity.SimulationMode.AUTO);
    }

    @Test
    @Tag("core")
    void virtualRoomCommandUsesThingsBoardRpcWhenVirtualRpcIsReady() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        AppUser user = user(userId);
        Room room = emptyRoom(roomId, "Dnevni boravak", "real-asset-id");
        WindowDevice blinds = WindowDevice.virtualDevice(
                "Virtualni roleta",
                "tb-blinds-id",
                java.util.Set.of(DeviceCapability.BLINDS_CONTROL)
        );
        ReflectionTestUtils.setField(blinds, "id", deviceId);
        room.addDevice(blinds);
        RoomCommandResponse rpcResponse = new RoomCommandResponse(
                "cmd-rpc",
                roomId,
                deviceId,
                "tb-blinds-id",
                "tb-blinds-id",
                DeviceType.VIRTUAL.name(),
                "blinds",
                "setPosition",
                85.0,
                "EXECUTED",
                "now",
                "THINGSBOARD_RPC",
                Map.of("status", "EXECUTED")
        );

        when(currentUserService.getOrCreateCurrentUser()).thenReturn(user);
        when(roomRepository.findByIdAndHomeAppUserId(roomId, userId)).thenReturn(Optional.of(room));
        when(virtualRpcCommandDeliveryPort.isReady()).thenReturn(true);
        when(virtualRpcCommandDeliveryPort.deliver(org.mockito.Mockito.eq(roomId), org.mockito.Mockito.eq(blinds), any(CommandRequest.class)))
                .thenReturn(rpcResponse);

        RoomCommandResponse response = roomService.sendRoomCommand(
                roomId,
                new CommandRequest("blinds", "setPosition", 85.0, "room-dashboard", deviceId)
        );

        assertThat(response.delivery()).isEqualTo("THINGSBOARD_RPC");
        assertThat(response.status()).isEqualTo("EXECUTED");
        verify(virtualRpcCommandDeliveryPort).deliver(org.mockito.Mockito.eq(roomId), org.mockito.Mockito.eq(blinds), any(CommandRequest.class));
        verify(commandService, never()).enqueueDeviceCommand(any(), any());
        verify(telemetryPublisher, never()).publishTelemetry(any(), any());
    }

    @Test
    @Tag("core")
    void virtualRoomCommandFallsBackToLocalSimulationWhenVirtualRpcIsNotReady() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        AppUser user = user(userId);
        Room room = emptyRoom(roomId, "Dnevni boravak", "real-asset-id");
        WindowDevice blinds = WindowDevice.virtualDevice(
                "Virtualni roleta",
                "tb-blinds-id",
                java.util.Set.of(DeviceCapability.BLINDS_CONTROL)
        );
        ReflectionTestUtils.setField(blinds, "id", deviceId);
        room.addDevice(blinds);
        RuntimeState.Command queued = new RuntimeState.Command("tb-blinds-id", "blinds", "setPosition", 85.0, "room-dashboard");

        when(currentUserService.getOrCreateCurrentUser()).thenReturn(user);
        when(roomRepository.findByIdAndHomeAppUserId(roomId, userId)).thenReturn(Optional.of(room));
        when(virtualRpcCommandDeliveryPort.isReady()).thenReturn(false);
        when(commandService.enqueueDeviceCommand(org.mockito.Mockito.eq("tb-blinds-id"), any(CommandRequest.class)))
                .thenReturn(CommandResult.command("blinds", "setPosition", 85.0, queued));

        RoomCommandResponse response = roomService.sendRoomCommand(
                roomId,
                new CommandRequest("blinds", "setPosition", 85.0, "room-dashboard", deviceId)
        );

        assertThat(response.delivery()).isEqualTo("VIRTUAL_LOCAL");
        assertThat(response.status()).isEqualTo("APPLIED");
        assertThat(blinds.getSimBlindClosedPercent()).isEqualTo(85.0);
        verify(commandService).acknowledgeCommand(queued.id, "tb-blinds-id", "executed");
        verify(telemetryPublisher).publishTelemetry(org.mockito.Mockito.eq(blinds), any());
    }

    @Test
    @Tag("core")
    void updateAutomationModePersistsAndSyncsManualModeToRoomDevices() {
        automationProperties().setEngine(com.windowsense.config.WindowSenseProperties.AutomationEngine.THINGSBOARD_RULE_CHAIN);
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        AppUser user = user(userId);
        Room room = room(roomId, "Kuhinja", "real-asset-id", "real-device-id");

        when(currentUserService.getOrCreateCurrentUser()).thenReturn(user);
        when(roomRepository.findByIdAndHomeAppUserId(roomId, userId)).thenReturn(Optional.of(room));

        RoomResponse response = roomService.updateAutomationMode(roomId, Map.of("mode", "MANUAL"));

        assertThat(response.manualMode()).isTrue();
        assertThat(room.isManualMode()).isTrue();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> attributesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(provisioningService).syncDeviceSharedAttributes(
                org.mockito.Mockito.eq("real-device-id"),
                attributesCaptor.capture()
        );
        assertThat(attributesCaptor.getValue()).containsEntry("manualMode", true);
    }

    @Test
    @Tag("core")
    void updateDeviceAutomationAnglesPersistsAndSyncsSelectedDeviceAngles() {
        automationProperties().setEngine(com.windowsense.config.WindowSenseProperties.AutomationEngine.THINGSBOARD_RULE_CHAIN);
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        AppUser user = user(userId);
        Room room = room(roomId, "Kuhinja", "real-asset-id", "real-device-id");
        WindowDevice device = room.getDevices().getFirst();
        ReflectionTestUtils.setField(device, "id", deviceId);

        when(currentUserService.getOrCreateCurrentUser()).thenReturn(user);
        when(roomRepository.findByIdAndHomeAppUserId(roomId, userId)).thenReturn(Optional.of(room));

        RoomResponse response = roomService.updateDeviceAutomationAngles(roomId, deviceId, Map.of(
                "desiredAngleDay", 80,
                "desiredAngleNight", 5,
                "desiredAngleRain", 30
        ));

        WindowDeviceResponse updatedDevice = response.devices().getFirst();
        assertThat(updatedDevice.desiredAngleDay()).isEqualTo(80.0);
        assertThat(updatedDevice.desiredAngleNight()).isEqualTo(5.0);
        assertThat(updatedDevice.desiredAngleRain()).isEqualTo(30.0);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> attributesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(provisioningService).syncDeviceSharedAttributes(
                org.mockito.Mockito.eq("real-device-id"),
                attributesCaptor.capture()
        );
        assertThat(attributesCaptor.getValue())
                .containsEntry("desiredAngleDay", 80)
                .containsEntry("desiredAngleNight", 5)
                .containsEntry("desiredAngleRain", 30);
    }

    private static AppUser user(UUID userId) {
        AppUser user = new AppUser("auth0|window-user", "user@example.com", "Window User");
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private static Room room(UUID roomId, String roomName, String tbAssetId, String tbDeviceId) {
        Room room = emptyRoom(roomId, roomName, tbAssetId);
        room.addDevice(WindowDevice.virtualDevice("WindowSense - " + roomName, tbDeviceId));
        return room;
    }

    private static Room emptyRoom(UUID roomId, String roomName) {
        return emptyRoom(roomId, roomName, null);
    }

    private static Room emptyRoom(UUID roomId, String roomName, String tbAssetId) {
        Room room = new Room(new Home(user(UUID.randomUUID()), "Default Home"), roomName, tbAssetId);
        ReflectionTestUtils.setField(room, "id", roomId);
        return room;
    }

    private com.windowsense.config.WindowSenseProperties.Automation automationProperties() {
        return (com.windowsense.config.WindowSenseProperties.Automation) ReflectionTestUtils.getField(
                roomService,
                "automationProperties"
        );
    }
}
