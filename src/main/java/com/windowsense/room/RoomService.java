package com.windowsense.room;

import com.windowsense.auth.CurrentUserService;
import com.windowsense.common.ConflictException;
import com.windowsense.common.ResourceNotFoundException;
import com.windowsense.device.WindowDevice;
import com.windowsense.home.Home;
import com.windowsense.home.HomeRepository;
import com.windowsense.room.dto.CreateRoomRequest;
import com.windowsense.room.dto.RoomResponse;
import com.windowsense.room.dto.UpdateRoomRequest;
import com.windowsense.thingsboard.ProvisionedRoomDevice;
import com.windowsense.thingsboard.ThingsBoardProvisioningService;
import com.windowsense.thingsboard.VirtualRoomProvisioningRequest;
import com.windowsense.user.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RoomService {

    private static final String DEFAULT_HOME_NAME = "Default Home";
    private static final String PENDING_THINGSBOARD_ID = "pending-thingsboard-provisioning";

    private final CurrentUserService currentUserService;
    private final HomeRepository homeRepository;
    private final RoomRepository roomRepository;
    private final ThingsBoardProvisioningService thingsBoardProvisioningService;
    private final RoomMapper roomMapper;

    public RoomService(
            CurrentUserService currentUserService,
            HomeRepository homeRepository,
            RoomRepository roomRepository,
            ThingsBoardProvisioningService thingsBoardProvisioningService,
            RoomMapper roomMapper
    ) {
        this.currentUserService = currentUserService;
        this.homeRepository = homeRepository;
        this.roomRepository = roomRepository;
        this.thingsBoardProvisioningService = thingsBoardProvisioningService;
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
    public void deleteRoom(UUID roomId) {
        AppUser user = currentUserService.getOrCreateCurrentUser();
        Room room = findOwnedRoom(roomId, user);
        room.getDevices().stream()
                .findFirst()
                .ifPresent(device -> thingsBoardProvisioningService.markRoomDeviceDeleted(
                        room.getTbAssetId(),
                        device.getTbDeviceId()
                ));
        roomRepository.delete(room);
    }

    private Home findOrCreateDefaultHome(AppUser user) {
        return homeRepository.findByAppUserIdAndName(user.getId(), DEFAULT_HOME_NAME)
                .orElseGet(() -> homeRepository.save(new Home(user, DEFAULT_HOME_NAME)));
    }

    private Room findOwnedRoom(UUID roomId, AppUser user) {
        return roomRepository.findByIdAndHomeAppUserId(roomId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Soba nije pronadjena."));
    }
}
