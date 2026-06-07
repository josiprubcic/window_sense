package com.windowsense.service;

import com.windowsense.entity.WindowDevice;
import com.windowsense.dto.CommandRequest;
import com.windowsense.dto.RoomCommandResponse;

import java.util.UUID;

public interface CommandDeliveryPort {

    RoomCommandResponse deliver(UUID roomId, WindowDevice targetDevice, CommandRequest command);
}
