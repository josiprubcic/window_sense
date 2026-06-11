package com.windowsense.service;

import com.windowsense.dto.AutomationThresholds;
import com.windowsense.config.WindowSenseProperties;
import com.windowsense.security.CurrentUserService;
import com.windowsense.exception.ConflictException;
import com.windowsense.exception.ResourceNotFoundException;
import com.windowsense.exception.ThingsBoardProvisioningException;
import com.windowsense.entity.DeviceCapabilities;
import com.windowsense.entity.DeviceCapability;
import com.windowsense.entity.DeviceStatus;
import com.windowsense.entity.DeviceType;
import com.windowsense.service.PhysicalDevicePairingCodeHasher;
import com.windowsense.entity.PhysicalDeviceRegistry;
import com.windowsense.repository.PhysicalDeviceRegistryRepository;
import com.windowsense.entity.PhysicalDeviceRegistryStatus;
import com.windowsense.service.PhysicalDeviceSecretHasher;
import com.windowsense.entity.SimulationMode;
import com.windowsense.entity.WindowDevice;
import com.windowsense.entity.Home;
import com.windowsense.repository.HomeRepository;
import com.windowsense.entity.Room;
import com.windowsense.mapper.RoomMapper;
import com.windowsense.repository.RoomRepository;
import com.windowsense.dto.AttachPhysicalDeviceTokenRequest;
import com.windowsense.dto.ConnectPhysicalDeviceRequest;
import com.windowsense.dto.AddVirtualDeviceRequest;
import com.windowsense.dto.CreateRoomRequest;
import com.windowsense.dto.PairPhysicalDeviceRequest;
import com.windowsense.dto.ProvisionPhysicalEspRequest;
import com.windowsense.dto.ProvisionPhysicalEspResponse;
import com.windowsense.dto.RoomAutomationThresholdsResponse;
import com.windowsense.dto.RoomCommandResponse;
import com.windowsense.dto.RoomDeviceTelemetryResponse;
import com.windowsense.dto.RoomResponse;
import com.windowsense.dto.RoomSimulationResponse;
import com.windowsense.dto.RoomTelemetryResponse;
import com.windowsense.dto.UpdateRoomRequest;
import com.windowsense.security.EncryptionService;
import com.windowsense.service.CommandService;
import com.windowsense.repository.WindowDeviceRepository;
import com.windowsense.service.CommandDeliveryPort;
import com.windowsense.mapper.TelemetryKeyMapper;
import com.windowsense.integration.thingsboard.ExistingPhysicalDeviceLinkRequest;
import com.windowsense.integration.thingsboard.PhysicalEspProvisioningRequest;
import com.windowsense.integration.thingsboard.ProvisionedRoomAsset;
import com.windowsense.integration.thingsboard.ProvisionedPhysicalDevice;
import com.windowsense.integration.thingsboard.ProvisionedRoomDevice;
import com.windowsense.integration.thingsboard.RoomAssetDeprovisioningRequest;
import com.windowsense.integration.thingsboard.RoomAssetProvisioningRequest;
import com.windowsense.integration.thingsboard.RoomAutomationAttributesRequest;
import com.windowsense.integration.thingsboard.RoomDeviceDeprovisioningRequest;
import com.windowsense.integration.thingsboard.ThingsBoardProvisioningService;
import com.windowsense.integration.thingsboard.ThingsBoardTelemetryQueryService;
import com.windowsense.integration.thingsboard.VirtualRoomProvisioningRequest;
import com.windowsense.entity.AppUser;
import com.windowsense.service.TelemetryPublisher;
import com.windowsense.service.VirtualThingsBoardRpcCommandDeliveryPort;
import com.windowsense.dto.CommandRequest;
import com.windowsense.service.CommandResult;
import com.windowsense.dto.Decision;
import com.windowsense.entity.RuntimeState;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RoomService {

    private static final Logger log = LoggerFactory.getLogger(RoomService.class);
    private static final String DEFAULT_HOME_NAME = "Default Home";
    private static final String PENDING_THINGSBOARD_ID = "pending-thingsboard-provisioning";
    private static final String MOCK_THINGSBOARD_ASSET_PREFIX = "tb-asset-";
    private static final String MOCK_THINGSBOARD_DEVICE_PREFIX = "tb-device-";
    private static final Duration DEVICE_BOOTSTRAP_SESSION_TTL = Duration.ofMinutes(15);

    private final CurrentUserService currentUserService;
    private final HomeRepository homeRepository;
    private final RoomRepository roomRepository;
    private final WindowDeviceRepository windowDeviceRepository;
    private final PhysicalDeviceRegistryRepository physicalDeviceRegistryRepository;
    private final ThingsBoardProvisioningService thingsBoardProvisioningService;
    private final ThingsBoardTelemetryQueryService thingsBoardTelemetryQueryService;
    private final EncryptionService encryptionService;
    private final CommandService commandService;
    private final CommandDeliveryPort commandDeliveryPort;
    private final TelemetryKeyMapper telemetryKeyMapper;
    private final RoomAutomationEvaluator roomAutomationEvaluator;
    private final RoomDeviceSelector roomDeviceSelector;
    private final RoomMapper roomMapper;
    private final TelemetryPublisher telemetryPublisher;
    private final VirtualThingsBoardRpcCommandDeliveryPort virtualRpcCommandDeliveryPort;
    private final WindowSenseProperties.Automation automationProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public RoomService(
            CurrentUserService currentUserService,
            HomeRepository homeRepository,
            RoomRepository roomRepository,
            WindowDeviceRepository windowDeviceRepository,
            PhysicalDeviceRegistryRepository physicalDeviceRegistryRepository,
            ThingsBoardProvisioningService thingsBoardProvisioningService,
            ThingsBoardTelemetryQueryService thingsBoardTelemetryQueryService,
            EncryptionService encryptionService,
            CommandService commandService,
            CommandDeliveryPort commandDeliveryPort,
            TelemetryKeyMapper telemetryKeyMapper,
            RoomAutomationEvaluator roomAutomationEvaluator,
            RoomDeviceSelector roomDeviceSelector,
            RoomMapper roomMapper,
            TelemetryPublisher telemetryPublisher,
            VirtualThingsBoardRpcCommandDeliveryPort virtualRpcCommandDeliveryPort,
            WindowSenseProperties properties
    ) {
        this.currentUserService = currentUserService;
        this.homeRepository = homeRepository;
        this.roomRepository = roomRepository;
        this.windowDeviceRepository = windowDeviceRepository;
        this.physicalDeviceRegistryRepository = physicalDeviceRegistryRepository;
        this.thingsBoardProvisioningService = thingsBoardProvisioningService;
        this.thingsBoardTelemetryQueryService = thingsBoardTelemetryQueryService;
        this.encryptionService = encryptionService;
        this.commandService = commandService;
        this.commandDeliveryPort = commandDeliveryPort;
        this.telemetryKeyMapper = telemetryKeyMapper;
        this.roomAutomationEvaluator = roomAutomationEvaluator;
        this.roomDeviceSelector = roomDeviceSelector;
        this.roomMapper = roomMapper;
        this.telemetryPublisher = telemetryPublisher;
        this.virtualRpcCommandDeliveryPort = virtualRpcCommandDeliveryPort;
        this.automationProperties = properties.getAutomation();
    }

    public RoomService(
            CurrentUserService currentUserService,
            HomeRepository homeRepository,
            RoomRepository roomRepository,
            WindowDeviceRepository windowDeviceRepository,
            PhysicalDeviceRegistryRepository physicalDeviceRegistryRepository,
            ThingsBoardProvisioningService thingsBoardProvisioningService,
            ThingsBoardTelemetryQueryService thingsBoardTelemetryQueryService,
            EncryptionService encryptionService,
            CommandService commandService,
            CommandDeliveryPort commandDeliveryPort,
            TelemetryKeyMapper telemetryKeyMapper,
            RoomAutomationEvaluator roomAutomationEvaluator,
            RoomDeviceSelector roomDeviceSelector,
            RoomMapper roomMapper,
            TelemetryPublisher telemetryPublisher,
            VirtualThingsBoardRpcCommandDeliveryPort virtualRpcCommandDeliveryPort
    ) {
        this(
                currentUserService,
                homeRepository,
                roomRepository,
                windowDeviceRepository,
                physicalDeviceRegistryRepository,
                thingsBoardProvisioningService,
                thingsBoardTelemetryQueryService,
                encryptionService,
                commandService,
                commandDeliveryPort,
                telemetryKeyMapper,
                roomAutomationEvaluator,
                roomDeviceSelector,
                roomMapper,
                telemetryPublisher,
                virtualRpcCommandDeliveryPort,
                new WindowSenseProperties()
        );
    }

    @Transactional
    public List<RoomResponse> listCurrentUserRooms() {
        AppUser user = currentUserService.getOrCreateCurrentUser();
        return roomRepository.findByHomeAppUserIdOrderByNameAsc(user.getId())
                .stream()
                .map(roomMapper::toResponse)
                .toList();
    }

    @Transactional
    public RoomResponse createRoom(CreateRoomRequest request) {
        AppUser user = currentUserService.getOrCreateCurrentUser();
        Home home = findOrCreateDefaultHome(user);
        String roomName = request.name().trim();

        if (roomRepository.existsByHomeIdAndNameIgnoreCase(home.getId(), roomName)) {
            throw new ConflictException("Soba s tim nazivom vec postoji u objektu.");
        }

        Room room = roomRepository.saveAndFlush(new Room(home, roomName));
        ProvisionedRoomAsset provisioned = thingsBoardProvisioningService.provisionRoomAsset(new RoomAssetProvisioningRequest(
                room.getId(),
                room.getName(),
                user.getId(),
                user.getAuth0Sub()
        ));
        room.updateThingsBoardAsset(provisioned.tbAssetId());
        roomRepository.saveAndFlush(room);
        return roomMapper.toResponse(room);
    }

    @Transactional(readOnly = true)
    public RoomResponse getRoom(UUID roomId) {
        AppUser user = currentUserService.getOrCreateCurrentUser();
        return roomMapper.toResponse(findOwnedRoom(roomId, user));
    }

    @Transactional
    public RoomResponse updateRoom(UUID roomId, UpdateRoomRequest request) {
        AppUser user = currentUserService.getOrCreateCurrentUser();
        Room room = findOwnedRoom(roomId, user);
        String roomName = request.name().trim();

        if (roomRepository.existsByHomeIdAndNameIgnoreCaseAndIdNot(room.getHome().getId(), roomName, room.getId())) {
            throw new ConflictException("Soba s tim nazivom vec postoji u objektu.");
        }

        room.rename(roomName);
        return roomMapper.toResponse(room);
    }

    @Transactional
    public RoomResponse connectPhysicalDevice(UUID roomId, ConnectPhysicalDeviceRequest request) {
        AppUser user = currentUserService.getOrCreateCurrentUser();
        Room room = findOwnedRoom(roomId, user);

        String deviceName = requiredTrimmed(request.name(), "Naziv uredjaja je obavezan.");
        String tbDeviceId = requiredTrimmed(request.tbDeviceId(), "ThingsBoard Device ID je obavezan.");
        String tbDeviceName = request.tbDeviceName() == null ? null : request.tbDeviceName().trim();
        Set<DeviceCapability> capabilities = requestedCapabilitiesOrDefault(request.capabilities());

        WindowDevice device = WindowDevice.physicalDevice(deviceName, tbDeviceId, null, capabilities);
        room.addDevice(device);
        roomRepository.saveAndFlush(room);

        thingsBoardProvisioningService.linkExistingPhysicalDevice(new ExistingPhysicalDeviceLinkRequest(
                room.getId(),
                room.getName(),
                room.getTbAssetId(),
                tbDeviceId,
                deviceName,
                tbDeviceName == null || tbDeviceName.isBlank() ? null : tbDeviceName,
                capabilityLabels(capabilities),
                user.getId(),
                user.getAuth0Sub()
        ));
        String accessToken = thingsBoardProvisioningService.fetchDeviceAccessToken(tbDeviceId);
        if (accessToken != null && !accessToken.isBlank()) {
            device.storeEncryptedThingsBoardDeviceToken(encryptionService.encrypt(accessToken));
        }
        syncAutomationAttributes(room);

        return roomMapper.toResponse(room);
    }

    @Transactional
    public RoomResponse addVirtualDevice(UUID roomId, AddVirtualDeviceRequest request) {
        AppUser user = currentUserService.getOrCreateCurrentUser();
        Room room = findOwnedRoom(roomId, user);

        String deviceName = requiredTrimmed(request.name(), "Naziv uredjaja je obavezan.");
        Set<DeviceCapability> capabilities = requestedCapabilitiesOrDefault(request.capabilities());
        ProvisionedRoomDevice provisioned = thingsBoardProvisioningService.provisionVirtualRoomDevice(
                new VirtualRoomProvisioningRequest(
                        room.getId(),
                        room.getName(),
                        room.getTbAssetId(),
                        deviceName,
                        capabilityLabels(capabilities),
                        user.getId(),
                        user.getAuth0Sub()
                )
        );

        WindowDevice device = WindowDevice.virtualDevice(deviceName, provisioned.tbDeviceId(), capabilities);
        if (provisioned.tbDeviceAccessToken() != null && !provisioned.tbDeviceAccessToken().isBlank()) {
            device.storeEncryptedThingsBoardDeviceToken(encryptionService.encrypt(provisioned.tbDeviceAccessToken()));
        }
        room.updateThingsBoardAsset(provisioned.tbAssetId());
        room.addDevice(device);
        roomRepository.saveAndFlush(room);
        syncAutomationAttributes(room);
        return roomMapper.toResponse(room);
    }

    @Transactional
    public RoomResponse pairPhysicalDevice(UUID roomId, PairPhysicalDeviceRequest request) {
        return pairRegisteredPhysicalDevice(roomId, request, false);
    }

    @Transactional
    public RoomResponse addPhysicalDeviceToEntity(UUID roomId, PairPhysicalDeviceRequest request) {
        return pairRegisteredPhysicalDevice(roomId, request, true);
    }

    @Transactional
    public RoomResponse addPhysicalDeviceByToken(UUID roomId, AttachPhysicalDeviceTokenRequest request) {
        AppUser user = currentUserService.getOrCreateCurrentUser();
        Room room = findOwnedRoom(roomId, user);

        String deviceName = requiredTrimmed(request.name(), "Naziv uredjaja je obavezan.");
        String accessTokenHash = PhysicalDeviceSecretHasher.hash(
                requiredTrimmed(request.thingsBoardAccessToken(), "ESP ThingsBoard token je obavezan.")
        );
        PhysicalDeviceRegistry registryDevice = physicalDeviceRegistryRepository.findByThingsBoardAccessTokenHash(accessTokenHash)
                .orElseThrow(() -> new ResourceNotFoundException("Uredjaj nije registriran za taj token."));

        return attachRegisteredPhysicalDevice(
                room,
                user,
                registryDevice,
                deviceName,
                true,
                registryDevice.getCapabilities(),
                request.thingsBoardAccessToken()
        );
    }

    private RoomResponse pairRegisteredPhysicalDevice(UUID roomId, PairPhysicalDeviceRequest request, boolean linkThingsBoardEntity) {
        AppUser user = currentUserService.getOrCreateCurrentUser();
        Room room = findOwnedRoom(roomId, user);

        String serialNumber = requiredTrimmed(request.serialNumber(), "Serijski broj uredjaja je obavezan.");
        String deviceName = request.name() == null || request.name().isBlank()
                ? "WindowSense " + serialNumber
                : request.name().trim();
        String pairingCodeHash = PhysicalDevicePairingCodeHasher.hash(
                requiredTrimmed(request.pairingCode(), "Kod za povezivanje je obavezan.")
        );
        PhysicalDeviceRegistry registryDevice = physicalDeviceRegistryRepository.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Uredjaj nije pronadjen."));

        if (!registryDevice.getPairingCodeHash().equalsIgnoreCase(pairingCodeHash)) {
            throw new ResourceNotFoundException("Kod za povezivanje nije valjan.");
        }
        validateClaimablePhysicalDevice(registryDevice);
        Set<DeviceCapability> capabilities = request.capabilities() == null || request.capabilities().isEmpty()
                ? registryDevice.getCapabilities()
                : requestedCapabilitiesOrDefault(request.capabilities());

        return attachRegisteredPhysicalDevice(room, user, registryDevice, deviceName, linkThingsBoardEntity, capabilities);
    }

    private RoomResponse attachRegisteredPhysicalDevice(
            Room room,
            AppUser user,
            PhysicalDeviceRegistry registryDevice,
            String deviceName,
            boolean linkThingsBoardEntity
    ) {
        return attachRegisteredPhysicalDevice(room, user, registryDevice, deviceName, linkThingsBoardEntity, registryDevice.getCapabilities());
    }

    private RoomResponse attachRegisteredPhysicalDevice(
            Room room,
            AppUser user,
            PhysicalDeviceRegistry registryDevice,
            String deviceName,
            boolean linkThingsBoardEntity,
            Set<DeviceCapability> capabilities
    ) {
        return attachRegisteredPhysicalDevice(
                room,
                user,
                registryDevice,
                deviceName,
                linkThingsBoardEntity,
                capabilities,
                null
        );
    }

    private RoomResponse attachRegisteredPhysicalDevice(
            Room room,
            AppUser user,
            PhysicalDeviceRegistry registryDevice,
            String deviceName,
            boolean linkThingsBoardEntity,
            Set<DeviceCapability> capabilities,
            String thingsBoardAccessToken
    ) {
        validateClaimablePhysicalDevice(registryDevice);
        if (capabilities == null || capabilities.isEmpty()) {
            capabilities = registryDevice.getCapabilities();
        }

        WindowDevice device = WindowDevice.physicalDevice(
                deviceName,
                registryDevice.getTbDeviceId(),
                registryDevice.getSerialNumber(),
                capabilities
        );
        if (thingsBoardAccessToken != null && !thingsBoardAccessToken.isBlank()) {
            device.storeEncryptedThingsBoardDeviceToken(encryptionService.encrypt(thingsBoardAccessToken));
        }
        room.addDevice(device);
        registryDevice.claim(user.getId(), room.getId());
        roomRepository.saveAndFlush(room);

        if (linkThingsBoardEntity) {
            thingsBoardProvisioningService.linkExistingPhysicalDevice(new ExistingPhysicalDeviceLinkRequest(
                    room.getId(),
                    room.getName(),
                    room.getTbAssetId(),
                    registryDevice.getTbDeviceId(),
                    deviceName,
                    null,
                    capabilityLabels(capabilities),
                    user.getId(),
                    user.getAuth0Sub()
            ));
        }
        syncAutomationAttributes(room);

        return roomMapper.toResponse(room);
    }

    private void validateClaimablePhysicalDevice(PhysicalDeviceRegistry registryDevice) {
        if (registryDevice.getStatus() == PhysicalDeviceRegistryStatus.CLAIMED
                || registryDevice.getPairingCodeConsumedAt() != null) {
            throw new ConflictException("DEVICE_ALREADY_CLAIMED");
        }
        if (registryDevice.getStatus() == PhysicalDeviceRegistryStatus.DISABLED) {
            throw new ConflictException("Uredjaj je deaktiviran.");
        }
        if (registryDevice.getStatus() != PhysicalDeviceRegistryStatus.CLAIMABLE) {
            throw new ConflictException("Uredjaj nije dostupan za povezivanje.");
        }
        if (registryDevice.getTbDeviceId() == null || registryDevice.getTbDeviceId().isBlank()) {
            throw new ConflictException("DEVICE_NOT_PROVISIONED");
        }
    }

    @Transactional
    public ProvisionPhysicalEspResponse provisionPhysicalEspDevice(UUID roomId, ProvisionPhysicalEspRequest request) {
        AppUser user = currentUserService.getOrCreateCurrentUser();
        Room room = findOwnedRoom(roomId, user);

        String deviceName = requiredTrimmed(request.name(), "Naziv uredjaja je obavezan.");
        String pairingCodeHash = PhysicalDevicePairingCodeHasher.hash(
                requiredTrimmed(request.pairingCode(), "Kod za povezivanje je obavezan.")
        );
        String espPairingCodeHash = requiredTrimmed(request.pairingCodeHash(), "Hash koda za povezivanje s ESP-a je obavezan.");
        if (!pairingCodeHash.equalsIgnoreCase(espPairingCodeHash)) {
            throw new ConflictException("Kod za povezivanje ne odgovara ESP uredjaju.");
        }

        String serialNumber = requiredTrimmed(request.serialNumber(), "Serijski broj ESP uredjaja je obavezan.");
        if (physicalDeviceRegistryRepository.existsBySerialNumber(serialNumber)) {
            throw new ConflictException("ESP uredjaj s tim serijskim brojem vec postoji.");
        }
        String hardwareId = request.hardwareId() == null ? null : request.hardwareId().trim();
        if (hardwareId != null && !hardwareId.isBlank() && physicalDeviceRegistryRepository.existsByHardwareId(hardwareId)) {
            throw new ConflictException("ESP uredjaj s tim hardware ID-em vec postoji.");
        }

        String deviceSecretHash = requiredTrimmed(request.deviceSecretHash(), "Hash tajnog kljuca uredjaja je obavezan.");
        Set<DeviceCapability> capabilities = DeviceCapabilities.fromLabels(request.capabilities());
        List<String> capabilityLabels = capabilities.stream().map(DeviceCapability::name).toList();

        ProvisionedPhysicalDevice provisioned = thingsBoardProvisioningService.provisionPhysicalEspDevice(
                new PhysicalEspProvisioningRequest(
                        room.getId(),
                        room.getName(),
                        room.getTbAssetId(),
                        deviceName,
                        serialNumber,
                        hardwareId,
                        request.firmwareVersion(),
                        capabilityLabels,
                        user.getId(),
                        user.getAuth0Sub()
                )
        );

        WindowDevice device = WindowDevice.physicalDevice(deviceName, provisioned.tbDeviceId(), serialNumber, capabilities);
        if (provisioned.tbDeviceAccessToken() != null && !provisioned.tbDeviceAccessToken().isBlank()) {
            device.storeEncryptedThingsBoardDeviceToken(encryptionService.encrypt(provisioned.tbDeviceAccessToken()));
        }
        room.addDevice(device);

        String provisioningSessionId = randomUrlToken();
        Instant provisioningSessionExpiresAt = Instant.now().plus(DEVICE_BOOTSTRAP_SESSION_TTL);
        PhysicalDeviceRegistry registryDevice = new PhysicalDeviceRegistry(
                serialNumber,
                pairingCodeHash,
                provisioned.tbDeviceId(),
                PhysicalDeviceRegistryStatus.CLAIMABLE
        );
        registryDevice.updateProvisioningMetadata(
                hardwareId,
                request.firmwareVersion(),
                String.join(",", capabilityLabels),
                deviceSecretHash,
                PhysicalDeviceSecretHasher.hash(provisioningSessionId),
                provisioningSessionExpiresAt
        );
        registryDevice.claim(user.getId(), room.getId());

        roomRepository.saveAndFlush(room);
        physicalDeviceRegistryRepository.save(registryDevice);
        syncAutomationAttributes(room);
        return new ProvisionPhysicalEspResponse(
                room.getId(),
                room.getName(),
                device.getId(),
                device.getTbDeviceId(),
                registryDevice.getSerialNumber(),
                registryDevice.getHardwareId(),
                "AWAITING_DEVICE_BOOTSTRAP",
                provisioningSessionId,
                provisioningSessionExpiresAt
        );
    }

    @Transactional
    public void deleteRoom(UUID roomId) {
        AppUser user = currentUserService.getOrCreateCurrentUser();
        Room room = findOwnedRoom(roomId, user);
        List<WindowDevice> devices = List.copyOf(room.getDevices());
        for (WindowDevice device : devices) {
            if (shouldDeprovisionDevice(device.getTbDeviceId())) {
                thingsBoardProvisioningService.deprovisionRoomDevice(new RoomDeviceDeprovisioningRequest(
                        room.getId(),
                        room.getName(),
                        room.getTbAssetId(),
                        device.getTbDeviceId()
                ));
            }
        }

        if (isRealThingsBoardId(room.getTbAssetId(), MOCK_THINGSBOARD_ASSET_PREFIX)) {
            thingsBoardProvisioningService.deprovisionRoomAsset(new RoomAssetDeprovisioningRequest(
                    room.getId(),
                    room.getName(),
                    room.getTbAssetId()
            ));
        }

        for (WindowDevice device : devices) {
            deletePhysicalRegistryIfPresent(device);
        }
        roomRepository.delete(room);
    }

    @Transactional
    public void deleteRoomDevice(UUID roomId, UUID deviceId) {
        AppUser user = currentUserService.getOrCreateCurrentUser();
        Room room = findOwnedRoom(roomId, user);
        WindowDevice device = room.getDevices().stream()
                .filter(candidate -> deviceId.equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Uredjaj nije pronadjen u sobi."));

        if (shouldDeprovisionDevice(device.getTbDeviceId())) {
            thingsBoardProvisioningService.deprovisionRoomDevice(new RoomDeviceDeprovisioningRequest(
                    room.getId(),
                    room.getName(),
                    room.getTbAssetId(),
                    device.getTbDeviceId()
            ));
        }

        deletePhysicalRegistryIfPresent(device);
        room.removeDevice(device);
        roomRepository.saveAndFlush(room);
    }

    @Transactional(readOnly = true)
    public RoomTelemetryResponse latestTelemetry(UUID roomId) {
        AppUser user = currentUserService.getOrCreateCurrentUser();
        Room room = findOwnedRoom(roomId, user);
        List<WindowDevice> activeDevices = room.getDevices().stream()
                .filter(device -> device.getStatus() == DeviceStatus.ACTIVE)
                .sorted((left, right) -> {
                    if (left.getDeviceType() == right.getDeviceType()) {
                        return left.getName().compareToIgnoreCase(right.getName());
                    }
                    return left.getDeviceType() == DeviceType.PHYSICAL ? -1 : 1;
                })
                .toList();
        if (activeDevices.isEmpty()) {
            return new RoomTelemetryResponse(
                    room.getId(),
                    room.getName(),
                    null,
                    null,
                    null,
                    false,
                    Map.of(),
                    null,
                    "UNAVAILABLE",
                    "NO_ACTIVE_DEVICE",
                    "Nema povezanog uredjaja.",
                    List.of(),
                    Map.of()
            );
        }

        List<RoomDeviceTelemetryResponse> deviceTelemetry = activeDevices.stream()
                .map(device -> telemetryForDevice(room, device))
                .toList();
        Map<String, Object> aggregated = new LinkedHashMap<>();
        for (RoomDeviceTelemetryResponse device : deviceTelemetry) {
            aggregated.putAll(device.telemetry());
        }

        RoomDeviceTelemetryResponse first = deviceTelemetry.getFirst();
        boolean topLevelHasTelemetry = !first.telemetry().isEmpty();
        return new RoomTelemetryResponse(
                room.getId(),
                room.getName(),
                first.deviceId(),
                first.deviceName(),
                first.deviceType(),
                first.isVirtual(),
                first.telemetry(),
                first.updatedAt(),
                topLevelHasTelemetry ? "AVAILABLE" : "UNAVAILABLE",
                topLevelHasTelemetry ? null : "NO_TELEMETRY",
                topLevelHasTelemetry ? null : DeviceType.PHYSICAL.name().equals(first.deviceType())
                        ? "Fizicki uredjaj jos ne salje telemetriju."
                        : "Aktivni uredjaji jos ne salju telemetriju.",
                deviceTelemetry,
                aggregated
        );
    }

    private RoomDeviceTelemetryResponse telemetryForDevice(Room room, WindowDevice device) {
        Map<String, Object> telemetry = Map.of();
        Instant updatedAt = null;
        if (device.getDeviceType() == DeviceType.VIRTUAL) {
            telemetry = roomAutomationEvaluator.virtualTelemetry(device);
            updatedAt = device.getSimLastUpdatedAt();
        } else {
            Optional<RoomTelemetryResponse> physicalTelemetry = latestPhysicalTelemetry(room, device);
            if (physicalTelemetry.isPresent()) {
                telemetry = physicalTelemetry.get().telemetry();
                updatedAt = physicalTelemetry.get().updatedAt();
            }
        }

        return new RoomDeviceTelemetryResponse(
                device.getId(),
                device.getName(),
                device.getPhysicalHardwareId(),
                device.getDeviceType().name(),
                device.isVirtual(),
                device.getStatus().name(),
                device.getCapabilities().stream()
                        .map(Enum::name)
                        .collect(Collectors.toSet()),
                telemetry,
                updatedAt
        );
    }

    @Transactional
    public RoomCommandResponse sendRoomCommand(UUID roomId, CommandRequest request) {
        AppUser user = currentUserService.getOrCreateCurrentUser();
        Room room = findOwnedRoom(roomId, user);
        DeviceCapability requiredCapability = DeviceCapabilities.requiredForCommand(request.target());
        WindowDevice device = roomDeviceSelector.commandTarget(room, requiredCapability, request.localDeviceId());

        // Delivery implementations use WindowDevice.tbDeviceId as command.deviceId.
        // This is a public ThingsBoard/device identifier, not an access token or secret.
        CommandRequest routedCommand = new CommandRequest(
                request.target(),
                request.action(),
                request.positionPercent(),
                request.source() == null || request.source().isBlank() ? "room-dashboard" : request.source(),
                request.localDeviceId()
        );
        if (device.getDeviceType() == DeviceType.PHYSICAL) {
            return commandDeliveryPort.deliver(room.getId(), device, routedCommand);
        }
        if (device.getDeviceType() == DeviceType.VIRTUAL && virtualRpcCommandDeliveryPort.isReady()) {
            return virtualRpcCommandDeliveryPort.deliver(room.getId(), device, routedCommand);
        }

        CommandResult result = commandService.enqueueDeviceCommand(device.getTbDeviceId(), routedCommand);
        RuntimeState.Command queued = result.queued;
        String status = "QUEUED";
        if (device.getDeviceType() == DeviceType.VIRTUAL) {
            applyVirtualCommand(device, queued);
            commandService.acknowledgeCommand(queued.id, device.getTbDeviceId(), "executed");
            publishVirtualTelemetry(room, device, List.of());
            status = "APPLIED";
        }
        return new RoomCommandResponse(
                queued.id,
                room.getId(),
                device.getId(),
                queued.deviceId,
                queued.deviceId,
                device.getDeviceType().name(),
                queued.target,
                queued.action,
                queued.positionPercent,
                status,
                queued.ts,
                "VIRTUAL_LOCAL",
                Map.of()
        );
    }

    @Transactional(readOnly = true)
    public RoomSimulationResponse simulation(UUID roomId) {
        AppUser user = currentUserService.getOrCreateCurrentUser();
        Room room = findOwnedRoom(roomId, user);
        WindowDevice device = roomDeviceSelector.activeSimulationDevice(room);
        return simulationResponse(room, device, List.of());
    }

    @Transactional
    public RoomSimulationResponse updateSimulation(UUID roomId, Map<String, Object> payload) {
        AppUser user = currentUserService.getOrCreateCurrentUser();
        Room room = findOwnedRoom(roomId, user);
        List<WindowDevice> devices = activeVirtualDevices(room);
        if (devices.isEmpty()) {
            throw new ResourceNotFoundException("Soba nema aktivni virtualni uredjaj.");
        }

        RoomAutomationEvaluation firstEvaluation = null;
        for (WindowDevice device : devices) {
            double windowOpenPercent = device.getSimWindowOpenPercent();
            if (device.hasCapability(DeviceCapability.WINDOW_CONTROL) && payload.containsKey("windowOpenPercent")) {
                windowOpenPercent = numberValue(payload, "windowOpenPercent", windowOpenPercent, 0, 100);
            }
            double blindClosedPercent = device.getSimBlindClosedPercent();
            if (device.hasCapability(DeviceCapability.BLINDS_CONTROL) && payload.containsKey("blindClosedPercent")) {
                blindClosedPercent = numberValue(payload, "blindClosedPercent", blindClosedPercent, 0, 100);
            }
            device.updateSimulationMode(SimulationMode.MANUAL);
            device.updateSimulationTelemetry(
                    booleanValue(payload, "rainDetected", device.isSimRainDetected()),
                    numberValue(payload, "rainIntensity", device.getSimRainIntensity(), 0, 100),
                    numberValue(payload, "rainRiskPercent", device.getSimRainRiskPercent(), 0, 100),
                    numberValue(payload, "lux", device.getSimLux(), 0, 120000),
                    numberValue(payload, "indoorTempC", device.getSimIndoorTempC(), -30, 80),
                    numberValue(payload, "windKmh", device.getSimWindKmh(), 0, 250),
                    windowOpenPercent,
                    blindClosedPercent,
                    (int) numberValue(payload, "day", device.getSimDay(), 0, 1)
            );
            RoomAutomationEvaluation evaluation;
            if (automationProperties.isThingsBoardRuleChainEngine()) {
                evaluation = new RoomAutomationEvaluation(roomAutomationEvaluator.virtualTelemetry(device), List.of());
                publishVirtualTelemetry(room, device, List.of());
            } else {
                evaluation = roomAutomationEvaluator.evaluateAndApply(
                        room,
                        device,
                        thresholds(room),
                        roomAutomationEvaluator.virtualTelemetry(device)
                );
                publishVirtualTelemetry(room, device, evaluation.decisions());
            }
            if (firstEvaluation == null) {
                firstEvaluation = evaluation;
            }
        }
        return simulationResponse(room, devices.getFirst(), firstEvaluation == null ? List.of() : firstEvaluation.decisions());
    }

    @Transactional
    public RoomSimulationResponse updateSimulationMode(UUID roomId, Map<String, Object> payload) {
        AppUser user = currentUserService.getOrCreateCurrentUser();
        Room room = findOwnedRoom(roomId, user);
        List<WindowDevice> devices = activeVirtualDevices(room);
        if (devices.isEmpty()) {
            throw new ResourceNotFoundException("Soba nema aktivni virtualni uredjaj.");
        }
        Object rawMode = payload.get("mode");
        if (rawMode == null || rawMode.toString().isBlank()) {
            throw new IllegalArgumentException("Simulation mode je obavezan.");
        }
        SimulationMode mode = SimulationMode.valueOf(rawMode.toString().trim().toUpperCase());
        for (WindowDevice device : devices) {
            device.updateSimulationMode(mode);
        }
        return simulationResponse(room, devices.getFirst(), List.of());
    }

    @Transactional(readOnly = true)
    public RoomAutomationThresholdsResponse automationThresholds(UUID roomId) {
        AppUser user = currentUserService.getOrCreateCurrentUser();
        Room room = findOwnedRoom(roomId, user);
        WindowDevice device = roomDeviceSelector.activeControllableDevice(room).orElse(null);
        Map<String, Object> telemetry = device == null ? Map.of() : currentTelemetryForAutomation(room, device);
        return new RoomAutomationThresholdsResponse(room.getId(), thresholds(room), telemetry, List.of());
    }

    @Transactional
    public RoomAutomationThresholdsResponse updateAutomationThresholds(UUID roomId, Map<String, Object> payload) {
        AppUser user = currentUserService.getOrCreateCurrentUser();
        Room room = findOwnedRoom(roomId, user);
        room.updateThresholds(
                numberValue(payload, "rainIntensityClose", room.getThresholdRainIntensityClose(), 0, 100),
                numberValue(payload, "rainProbabilityClose", room.getThresholdRainProbabilityClose(), 0, 100),
                numberValue(payload, "windKphClose", room.getThresholdWindKphClose(), 0, 250),
                room.getThresholdLightLuxShade(),
                room.getThresholdLightLuxRelease(),
                room.getThresholdIndoorTempShadeC(),
                numberValue(payload, "blindsShadePosition", room.getThresholdBlindsShadePosition(), 0, 100),
                numberValue(payload, "blindsReleasePosition", room.getThresholdBlindsReleasePosition(), 0, 100)
        );
        syncAutomationAttributes(room);
        if (automationProperties.isThingsBoardRuleChainEngine()) {
            for (WindowDevice device : activeVirtualDevices(room)) {
                if (device.getDeviceType() == DeviceType.VIRTUAL) {
                    publishVirtualTelemetry(room, device, List.of());
                }
            }
        }
        Optional<WindowDevice> activeDevice = roomDeviceSelector.activeControllableDevice(room);
        if (activeDevice.isEmpty()) {
            return new RoomAutomationThresholdsResponse(room.getId(), thresholds(room), Map.of(), List.of());
        }
        WindowDevice device = activeDevice.get();
        if (automationProperties.isThingsBoardRuleChainEngine()) {
            Map<String, Object> telemetry = currentTelemetryForAutomation(room, device);
            return new RoomAutomationThresholdsResponse(room.getId(), thresholds(room), telemetry, List.of());
        }
        RoomAutomationEvaluation evaluation = roomAutomationEvaluator.evaluateAndApply(
                room,
                device,
                thresholds(room),
                currentTelemetryForAutomation(room, device)
        );
        if (device.getDeviceType() == DeviceType.VIRTUAL) {
            publishVirtualTelemetry(room, device, evaluation.decisions());
        }
        return new RoomAutomationThresholdsResponse(
                room.getId(),
                thresholds(room),
                evaluation.telemetry(),
                evaluation.decisions()
        );
    }

    private Optional<RoomTelemetryResponse> latestPhysicalTelemetry(Room room, WindowDevice device) {
        if (!isRealThingsBoardId(device.getTbDeviceId(), MOCK_THINGSBOARD_DEVICE_PREFIX)) {
            return Optional.empty();
        }

        try {
            ThingsBoardTelemetryQueryService.LatestTelemetry latest = thingsBoardTelemetryQueryService.latestDeviceTelemetry(device.getTbDeviceId());
            if (latest.telemetry().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new RoomTelemetryResponse(
                    room.getId(),
                    room.getName(),
                    device.getId(),
                    device.getName(),
                    device.getDeviceType().name(),
                    device.isVirtual(),
                    telemetryKeyMapper.normalizeRoomTelemetry(latest.telemetry(), latest.updatedAt()),
                    latest.updatedAt(),
                    "AVAILABLE",
                    null,
                    null
            ));
        } catch (ThingsBoardProvisioningException error) {
            return Optional.empty();
        }
    }

    private Map<String, Object> currentTelemetryForAutomation(Room room, WindowDevice device) {
        if (device.getDeviceType() == DeviceType.VIRTUAL) {
            return roomAutomationEvaluator.virtualTelemetry(device);
        }
        return latestPhysicalTelemetry(room, device)
                .map(RoomTelemetryResponse::telemetry)
                .orElseGet(Map::of);
    }

    private RoomSimulationResponse simulationResponse(Room room, WindowDevice device, List<Decision> decisions) {
        return new RoomSimulationResponse(
                room.getId(),
                room.getName(),
                device.getId(),
                device.getTbDeviceId(),
                device.getDeviceType().name(),
                device.getSimulationMode().name(),
                roomAutomationEvaluator.virtualTelemetry(device),
                decisions,
                device.getSimLastUpdatedAt()
        );
    }

    private void publishVirtualTelemetry(Room room, WindowDevice device, List<Decision> decisions) {
        if (device.getDeviceType() != DeviceType.VIRTUAL) {
            return;
        }

        Map<String, Object> payload = virtualThingsBoardPayload(room, device, decisions);
        syncRuleChainRuntimeSharedAttributes(device, payload);
        telemetryPublisher.publishTelemetry(device, payload);
    }

    private void syncRuleChainRuntimeSharedAttributes(WindowDevice device, Map<String, Object> payload) {
        if (!automationProperties.isThingsBoardRuleChainEngine()
                || !isRealThingsBoardId(device.getTbDeviceId(), MOCK_THINGSBOARD_DEVICE_PREFIX)) {
            return;
        }
        Map<String, Object> attributes = ruleChainRuntimeSharedAttributes(payload);
        if (attributes.isEmpty()) {
            return;
        }
        syncRoomRuntimeSharedAttributes(device.getRoom(), attributes);
    }

    private Map<String, Object> ruleChainRuntimeSharedAttributes(Map<String, Object> payload) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        Object rainProbability = payload.get("rainProbability");
        if (rainProbability == null) {
            rainProbability = payload.get("rainRiskPercent");
        }
        if (rainProbability != null) {
            attributes.put("rainProbability", rainProbability);
        }
        Object day = payload.get("day");
        if (day != null) {
            attributes.put("day", day);
        }
        return attributes;
    }

    private void syncRoomRuntimeSharedAttributes(Room room, Map<String, Object> attributes) {
        for (WindowDevice roomDevice : room.getDevices()) {
            if (roomDevice.getStatus() != DeviceStatus.ACTIVE
                    || !isRealThingsBoardId(roomDevice.getTbDeviceId(), MOCK_THINGSBOARD_DEVICE_PREFIX)) {
                continue;
            }
            thingsBoardProvisioningService.syncDeviceSharedAttributes(
                    roomDevice.getTbDeviceId(),
                    attributes
            );
        }
    }

    private Map<String, Object> virtualThingsBoardPayload(Room room, WindowDevice device, List<Decision> decisions) {
        Map<String, Object> fullTelemetry = roomAutomationEvaluator.virtualTelemetry(device);
        Map<String, Object> payload = new LinkedHashMap<>();
        copyIfPresent(fullTelemetry, payload, "rainDetected");
        copyIfPresent(fullTelemetry, payload, "rainIntensity");
        copyIfPresent(fullTelemetry, payload, "rainRiskPercent");
        copyIfPresent(fullTelemetry, payload, "rainProbability");
        copyIfPresent(fullTelemetry, payload, "windKmh");
        copyIfPresent(fullTelemetry, payload, "day");
        copyIfPresent(fullTelemetry, payload, "lastUpdatedAt");
        if (device.hasCapability(DeviceCapability.WINDOW_CONTROL)) {
            copyIfPresent(fullTelemetry, payload, "windowOpenPercent");
        }
        if (device.hasCapability(DeviceCapability.BLINDS_CONTROL)) {
            copyIfPresent(fullTelemetry, payload, "blindClosedPercent");
        }
        payload.put("roomId", room.getId().toString());
        payload.put("roomName", room.getName());
        payload.put("deviceId", device.getId().toString());
        payload.put("isVirtual", true);
        payload.put("simulationMode", device.getSimulationMode().name());
        return payload;
    }

    private List<WindowDevice> activeVirtualDevices(Room room) {
        return room.getDevices().stream()
                .filter(device -> device.getDeviceType() == DeviceType.VIRTUAL)
                .filter(device -> device.getStatus() == DeviceStatus.ACTIVE)
                .toList();
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    private void applyVirtualCommand(WindowDevice device, RuntimeState.Command command) {
        double windowOpen = device.getSimWindowOpenPercent();
        double blindClosed = device.getSimBlindClosedPercent();
        if ("window".equals(command.target)) {
            windowOpen = switch (command.action) {
                case "open" -> 100;
                case "close" -> 0;
                case "setPosition" -> command.positionPercent == null ? windowOpen : command.positionPercent;
                default -> windowOpen;
            };
        } else if ("blinds".equals(command.target)) {
            blindClosed = switch (command.action) {
                case "open" -> 0;
                case "close" -> 100;
                case "setPosition" -> command.positionPercent == null ? blindClosed : command.positionPercent;
                default -> blindClosed;
            };
        }
        device.updateSimulationMode(SimulationMode.MANUAL);
        device.updateSimulationTelemetry(
                device.isSimRainDetected(),
                device.getSimRainIntensity(),
                device.getSimRainRiskPercent(),
                device.getSimLux(),
                device.getSimIndoorTempC(),
                device.getSimWindKmh(),
                windowOpen,
                blindClosed
        );
    }

    private AutomationThresholds thresholds(Room room) {
        AutomationThresholds thresholds = new AutomationThresholds();
        thresholds.rainIntensityClose = room.getThresholdRainIntensityClose();
        thresholds.rainProbabilityClose = room.getThresholdRainProbabilityClose();
        thresholds.windKphClose = room.getThresholdWindKphClose();
        thresholds.blindsShadePosition = room.getThresholdBlindsShadePosition();
        thresholds.blindsReleasePosition = room.getThresholdBlindsReleasePosition();
        return thresholds;
    }

    private boolean booleanValue(Map<String, Object> payload, String key, boolean fallback) {
        Object value = payload.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() == 1;
        }
        if (value instanceof String text) {
            if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
                return true;
            }
            if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
                return false;
            }
        }
        return fallback;
    }

    private double numberValue(Map<String, Object> payload, String key, double fallback, double min, double max) {
        if (!payload.containsKey(key)) {
            return fallback;
        }
        Object value = payload.get(key);
        if (value == null || "".equals(value)) {
            return fallback;
        }
        double parsed;
        if (value instanceof Number number) {
            parsed = number.doubleValue();
        } else {
            try {
                parsed = Double.parseDouble(value.toString());
            } catch (NumberFormatException error) {
                parsed = fallback;
            }
        }
        return Math.min(max, Math.max(min, parsed));
    }

    private Home findOrCreateDefaultHome(AppUser user) {
        return homeRepository.findByAppUserIdAndName(user.getId(), DEFAULT_HOME_NAME)
                .orElseGet(() -> homeRepository.save(new Home(user, DEFAULT_HOME_NAME)));
    }

    private Room findOwnedRoom(UUID roomId, AppUser user) {
        return roomRepository.findByIdAndHomeAppUserId(roomId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Soba nije pronadjena."));
    }

    private boolean shouldDeprovisionDevice(String tbDeviceId) {
        return isRealThingsBoardId(tbDeviceId, MOCK_THINGSBOARD_DEVICE_PREFIX);
    }

    private void deletePhysicalRegistryIfPresent(WindowDevice device) {
        if (device.getPhysicalHardwareId() == null || device.getPhysicalHardwareId().isBlank()) {
            return;
        }
        physicalDeviceRegistryRepository.findBySerialNumber(device.getPhysicalHardwareId())
                .ifPresent(physicalDeviceRegistryRepository::delete);
    }

    @PostConstruct
    void syncMissingPhysicalDeviceTokens() {
        List<WindowDevice> devices = windowDeviceRepository.findAll();
        for (WindowDevice device : devices) {
            if (device.getDeviceType() != DeviceType.PHYSICAL || device.getTbDeviceTokenEncrypted() != null) {
                continue;
            }
            if (!isRealThingsBoardId(device.getTbDeviceId(), MOCK_THINGSBOARD_DEVICE_PREFIX)) {
                continue;
            }
            try {
                String accessToken = thingsBoardProvisioningService.fetchDeviceAccessToken(device.getTbDeviceId());
                if (accessToken != null && !accessToken.isBlank()) {
                    device.storeEncryptedThingsBoardDeviceToken(encryptionService.encrypt(accessToken));
                    windowDeviceRepository.save(device);
                    log.info("Dohvacen i spremljen ThingsBoard access token za fizicki uredjaj {}", device.getId());
                }
            } catch (Exception error) {
                log.warn("Neuspjelo dohvacanje tokena za fizicki uredjaj {}: {}", device.getId(), error.getMessage());
            }
        }
    }

    private void syncAutomationAttributes(Room room) {
        if (!automationProperties.isThingsBoardRuleChainEngine()) {
            return;
        }
        for (WindowDevice device : room.getDevices()) {
            if (device.getStatus() != DeviceStatus.ACTIVE || !isRealThingsBoardId(device.getTbDeviceId(), MOCK_THINGSBOARD_DEVICE_PREFIX)) {
                continue;
            }
            thingsBoardProvisioningService.syncRoomAutomationAttributes(new RoomAutomationAttributesRequest(
                    room.getId(),
                    room.getName(),
                    device.getTbDeviceId(),
                    room.getThresholdRainProbabilityClose()
            ));
        }
    }

    private boolean isRealThingsBoardId(String value, String mockPrefix) {
        return value != null && !value.isBlank()
                && !PENDING_THINGSBOARD_ID.equals(value)
                && !value.startsWith(mockPrefix);
    }

    private String requiredTrimmed(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private Set<DeviceCapability> requestedCapabilitiesOrDefault(List<String> capabilityLabels) {
        return DeviceCapabilities.fromLabels(capabilityLabels);
    }

    private List<String> capabilityLabels(Set<DeviceCapability> capabilities) {
        return capabilities == null ? List.of() : capabilities.stream()
                .map(DeviceCapability::name)
                .toList();
    }

    private String randomUrlToken() {
        byte[] token = new byte[24];
        secureRandom.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }
}
