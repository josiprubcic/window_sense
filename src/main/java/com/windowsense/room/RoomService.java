package com.windowsense.room;

import com.windowsense.auth.CurrentUserService;
import com.windowsense.common.ConflictException;
import com.windowsense.device.WindowDevice;
import com.windowsense.home.Home;
import com.windowsense.home.HomeRepository;
import com.windowsense.room.dto.CreateRoomRequest;
import com.windowsense.room.dto.RoomResponse;
import com.windowsense.thingsboard.ProvisionedRoomDevice;
import com.windowsense.thingsboard.ThingsBoardProvisioningService;
import com.windowsense.user.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoomService {

    private static final String DEFAULT_HOME_NAME = "Default Home";

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
        ProvisionedRoomDevice provisioned = thingsBoardProvisioningService.provisionVirtualRoomDevice(roomName, deviceName);
        Room room = new Room(home, roomName, provisioned.tbAssetId());
        room.addDevice(WindowDevice.virtualDevice(deviceName, provisioned.tbDeviceId()));

        return roomMapper.toResponse(roomRepository.save(room));
    }

    private Home findOrCreateDefaultHome(AppUser user) {
        return homeRepository.findByAppUserIdAndName(user.getId(), DEFAULT_HOME_NAME)
                .orElseGet(() -> homeRepository.save(new Home(user, DEFAULT_HOME_NAME)));
    }
}
