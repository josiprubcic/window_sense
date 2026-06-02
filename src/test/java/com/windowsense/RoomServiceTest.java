package com.windowsense;

import com.windowsense.auth.CurrentUserService;
import com.windowsense.common.ThingsBoardProvisioningException;
import com.windowsense.device.WindowDevice;
import com.windowsense.home.Home;
import com.windowsense.home.HomeRepository;
import com.windowsense.room.Room;
import com.windowsense.room.RoomMapper;
import com.windowsense.room.RoomRepository;
import com.windowsense.room.RoomService;
import com.windowsense.room.dto.CreateRoomRequest;
import com.windowsense.room.dto.RoomResponse;
import com.windowsense.thingsboard.ProvisionedRoomDevice;
import com.windowsense.thingsboard.ThingsBoardProvisioningService;
import com.windowsense.thingsboard.VirtualRoomDeprovisioningRequest;
import com.windowsense.thingsboard.VirtualRoomProvisioningRequest;
import com.windowsense.user.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private final ThingsBoardProvisioningService provisioningService = org.mockito.Mockito.mock(ThingsBoardProvisioningService.class);
    private final RoomService roomService = new RoomService(
            currentUserService,
            homeRepository,
            roomRepository,
            provisioningService,
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
                .thenReturn(new ProvisionedRoomDevice("tb-asset-real", "tb-device-real"));

        RoomResponse response = roomService.createRoom(new CreateRoomRequest("Kuhinja"));

        assertThat(response.id()).isEqualTo(roomId);
        assertThat(response.tbAssetId()).isEqualTo("tb-asset-real");
        assertThat(response.devices()).hasSize(1);
        assertThat(response.devices().getFirst().tbDeviceId()).isEqualTo("tb-device-real");

        ArgumentCaptor<VirtualRoomProvisioningRequest> requestCaptor = ArgumentCaptor.forClass(VirtualRoomProvisioningRequest.class);
        verify(provisioningService).provisionVirtualRoomDevice(requestCaptor.capture());
        assertThat(requestCaptor.getValue().roomId()).isEqualTo(roomId);
        assertThat(requestCaptor.getValue().roomName()).isEqualTo("Kuhinja");
        assertThat(requestCaptor.getValue().deviceName()).isEqualTo("WindowSense - Kuhinja");
        assertThat(requestCaptor.getValue().appUserId()).isEqualTo(userId);
        assertThat(requestCaptor.getValue().auth0Sub()).isEqualTo("auth0|window-user");
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
