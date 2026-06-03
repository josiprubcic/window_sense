package com.windowsense.room;

import com.windowsense.auth.CurrentUserService;
import com.windowsense.common.ConflictException;
import com.windowsense.common.ResourceNotFoundException;
import com.windowsense.device.DeviceStatus;
import com.windowsense.device.DeviceType;
import com.windowsense.device.PhysicalDevicePairingCodeHasher;
import com.windowsense.device.PhysicalDeviceRegistry;
import com.windowsense.device.PhysicalDeviceRegistryRepository;
import com.windowsense.device.PhysicalDeviceRegistryStatus;
import com.windowsense.device.WindowDevice;
import com.windowsense.device.WindowDeviceRepository;
import com.windowsense.home.Home;
import com.windowsense.home.HomeRepository;
import com.windowsense.room.dto.ConnectPhysicalDeviceRequest;
import com.windowsense.room.dto.CreateRoomRequest;
import com.windowsense.room.dto.PairPhysicalDeviceRequest;
import com.windowsense.room.dto.RoomResponse;
import com.windowsense.room.dto.RoomTelemetryResponse;
import com.windowsense.room.dto.UpdateRoomRequest;
import com.windowsense.security.EncryptionService;
import com.windowsense.thingsboard.ExistingPhysicalDeviceLinkRequest;
import com.windowsense.thingsboard.ProvisionedRoomDevice;
import com.windowsense.thingsboard.ThingsBoardProvisioningService;
import com.windowsense.thingsboard.ThingsBoardTelemetryQueryService;
import com.windowsense.thingsboard.VirtualRoomDeprovisioningRequest;
import com.windowsense.thingsboard.VirtualRoomProvisioningRequest;
import com.windowsense.user.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RoomService {

    private static final String DEFAULT_HOME_NAME = "Default Home";
    private static final String PENDING_THINGSBOARD_ID = "pending-thingsboard-provisioning";
    private static final String MOCK_THINGSBOARD_ASSET_PREFIX = "tb-asset-";
    private static final String MOCK_THINGSBOARD_DEVICE_PREFIX = "tb-device-";

    private final CurrentUserService currentUserService;
    private final HomeRepository homeRepository;
    private final RoomRepository roomRepository;
    private final WindowDeviceRepository windowDeviceRepository;
    private final PhysicalDeviceRegistryRepository physicalDeviceRegistryRepository;
    private final ThingsBoardProvisioningService thingsBoardProvisioningService;
    private final ThingsBoardTelemetryQueryService thingsBoardTelemetryQueryService;
    private final EncryptionService encryptionService;
    private final RoomMapper roomMapper;

    public RoomService(
            CurrentUserService currentUserService,
            HomeRepository homeRepository,
            RoomRepository roomRepository,
            WindowDeviceRepository windowDeviceRepository,
            PhysicalDeviceRegistryRepository physicalDeviceRegistryRepository,
            ThingsBoardProvisioningService thingsBoardProvisioningService,
            ThingsBoardTelemetryQueryService thingsBoardTelemetryQueryService,
            EncryptionService encryptionService,
            RoomMapper roomMapper
    ) {
        this.currentUserService = currentUserService;
        this.homeRepository = homeRepository;
        this.roomRepository = roomRepository;
        this.windowDeviceRepository = windowDeviceRepository;
        this.physicalDeviceRegistryRepository = physicalDeviceRegistryRepository;
        this.thingsBoardProvisioningService = thingsBoardProvisioningService;
        this.thingsBoardTelemetryQueryService = thingsBoardTelemetryQueryService;
        this.encryptionService = encryptionService;
        this.roomMapper = roomMapper;
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

        String deviceName = "WindowSense - " + roomName;
        Room room = new Room(home, roomName, PENDING_THINGSBOARD_ID);
        WindowDevice device = WindowDevice.virtualDevice(deviceName, PENDING_THINGSBOARD_ID);
        room.addDevice(device);
        roomRepository.saveAndFlush(room);

        ProvisionedRoomDevice provisioned = thingsBoardProvisioningService.provisionVirtualRoomDevice(
                new VirtualRoomProvisioningRequest(
                        room.getId(),
                        roomName,
                        deviceName,
                        user.getId(),
                        user.getAuth0Sub()
                )
        );
        room.updateThingsBoardAsset(provisioned.tbAssetId());
        device.updateThingsBoardDevice(provisioned.tbDeviceId());
        if (provisioned.tbDeviceAccessToken() != null && !provisioned.tbDeviceAccessToken().isBlank()) {
            device.storeEncryptedThingsBoardDeviceToken(encryptionService.encrypt(provisioned.tbDeviceAccessToken()));
        }

        return roomMapper.toResponse(room);
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
        rejectIfRoomHasActivePhysicalDevice(room);

        String deviceName = requiredTrimmed(request.name(), "Naziv uredjaja je obavezan.");
        String tbDeviceId = requiredTrimmed(request.tbDeviceId(), "ThingsBoard Device ID je obavezan.");
        String tbDeviceName = request.tbDeviceName() == null ? null : request.tbDeviceName().trim();

        WindowDevice device = WindowDevice.physicalDevice(deviceName, tbDeviceId);
        room.addDevice(device);
        roomRepository.saveAndFlush(room);

        thingsBoardProvisioningService.linkExistingPhysicalDevice(new ExistingPhysicalDeviceLinkRequest(
                room.getId(),
                room.getName(),
                room.getTbAssetId(),
                tbDeviceId,
                deviceName,
                tbDeviceName == null || tbDeviceName.isBlank() ? null : tbDeviceName,
                user.getId(),
                user.getAuth0Sub()
        ));

        return roomMapper.toResponse(room);
    }

    @Transactional
    public RoomResponse pairPhysicalDevice(UUID roomId, PairPhysicalDeviceRequest request) {
        AppUser user = currentUserService.getOrCreateCurrentUser();
        Room room = findOwnedRoom(roomId, user);
        rejectIfRoomHasActivePhysicalDevice(room);

        String deviceName = requiredTrimmed(request.name(), "Naziv uredjaja je obavezan.");
        String pairingCodeHash = PhysicalDevicePairingCodeHasher.hash(
                requiredTrimmed(request.pairingCode(), "Kod za povezivanje je obavezan.")
        );
        PhysicalDeviceRegistry registryDevice = physicalDeviceRegistryRepository.findByPairingCodeHash(pairingCodeHash)
                .orElseThrow(() -> new ResourceNotFoundException("Kod za povezivanje nije valjan."));

        if (registryDevice.getStatus() == PhysicalDeviceRegistryStatus.CLAIMED) {
            throw new ConflictException("Uredjaj je vec povezan.");
        }
        if (registryDevice.getStatus() == PhysicalDeviceRegistryStatus.DISABLED) {
            throw new ConflictException("Uredjaj je deaktiviran.");
        }
        if (registryDevice.getStatus() != PhysicalDeviceRegistryStatus.AVAILABLE) {
            throw new ConflictException("Uredjaj nije dostupan za povezivanje.");
        }

        WindowDevice device = WindowDevice.physicalDevice(deviceName, registryDevice.getTbDeviceId());
        room.addDevice(device);
        registryDevice.claim(user.getId(), room.getId());
        roomRepository.saveAndFlush(room);

        thingsBoardProvisioningService.linkExistingPhysicalDevice(new ExistingPhysicalDeviceLinkRequest(
                room.getId(),
                room.getName(),
                room.getTbAssetId(),
                registryDevice.getTbDeviceId(),
                deviceName,
                registryDevice.getSerialNumber(),
                user.getId(),
                user.getAuth0Sub()
        ));

        return roomMapper.toResponse(room);
    }

    @Transactional
    public void deleteRoom(UUID roomId) {
        AppUser user = currentUserService.getOrCreateCurrentUser();
        Room room = findOwnedRoom(roomId, user);
        room.getDevices().stream()
                .filter(WindowDevice::isVirtual)
                .findFirst()
                .filter(device -> shouldDeprovision(room.getTbAssetId(), device.getTbDeviceId()))
                .ifPresent(device -> thingsBoardProvisioningService.deprovisionVirtualRoom(
                        new VirtualRoomDeprovisioningRequest(
                                room.getId(),
                                room.getName(),
                                room.getTbAssetId(),
                                device.getTbDeviceId()
                        )
                ));
        roomRepository.delete(room);
    }

    @Transactional(readOnly = true)
    public RoomTelemetryResponse latestTelemetry(UUID roomId) {
        AppUser user = currentUserService.getOrCreateCurrentUser();
        Room room = findOwnedRoom(roomId, user);
        WindowDevice device = telemetryDevice(room);

        if (!isRealThingsBoardId(device.getTbDeviceId(), MOCK_THINGSBOARD_DEVICE_PREFIX)) {
            return new RoomTelemetryResponse(
                    room.getId(),
                    room.getName(),
                    device.getId(),
                    device.getName(),
                    Map.of(),
                    null,
                    "Telemetrija jos nije dostupna za mock ThingsBoard uredjaj."
            );
        }

        ThingsBoardTelemetryQueryService.LatestTelemetry latest = thingsBoardTelemetryQueryService.latestDeviceTelemetry(device.getTbDeviceId());
        return new RoomTelemetryResponse(
                room.getId(),
                room.getName(),
                device.getId(),
                device.getName(),
                latest.telemetry(),
                latest.updatedAt(),
                latest.telemetry().isEmpty() ? "Telemetrija jos nije dostupna." : null
        );
    }

    private WindowDevice telemetryDevice(Room room) {
        return room.getDevices().stream()
                .filter(device -> device.getDeviceType() == DeviceType.PHYSICAL)
                .filter(device -> device.getStatus() == DeviceStatus.ACTIVE)
                .findFirst()
                .or(() -> room.getDevices().stream()
                        .filter(device -> device.getDeviceType() == DeviceType.VIRTUAL)
                        .filter(device -> device.getStatus() == DeviceStatus.ACTIVE)
                        .findFirst())
                .orElseThrow(() -> new ResourceNotFoundException("Aktivni uredjaj za sobu nije pronadjen."));
    }

    private void rejectIfRoomHasActivePhysicalDevice(Room room) {
        if (windowDeviceRepository.existsByRoomIdAndDeviceTypeAndStatus(room.getId(), DeviceType.PHYSICAL, DeviceStatus.ACTIVE)) {
            throw new ConflictException("Soba vec ima aktivni fizicki uredjaj.");
        }
    }

    private Home findOrCreateDefaultHome(AppUser user) {
        return homeRepository.findByAppUserIdAndName(user.getId(), DEFAULT_HOME_NAME)
                .orElseGet(() -> homeRepository.save(new Home(user, DEFAULT_HOME_NAME)));
    }

    private Room findOwnedRoom(UUID roomId, AppUser user) {
        return roomRepository.findByIdAndHomeAppUserId(roomId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Soba nije pronadjena."));
    }

    private boolean shouldDeprovision(String tbAssetId, String tbDeviceId) {
        return isRealThingsBoardId(tbAssetId, MOCK_THINGSBOARD_ASSET_PREFIX)
                && isRealThingsBoardId(tbDeviceId, MOCK_THINGSBOARD_DEVICE_PREFIX);
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
}
