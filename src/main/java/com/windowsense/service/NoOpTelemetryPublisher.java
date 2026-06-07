package com.windowsense.service;

import com.windowsense.entity.WindowDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "windowsense.virtual-simulator", name = "publish-to-things-board", havingValue = "false", matchIfMissing = true)
public class NoOpTelemetryPublisher implements TelemetryPublisher {

    private static final Logger log = LoggerFactory.getLogger(NoOpTelemetryPublisher.class);

    @Override
    public void publishTelemetry(WindowDevice device, Map<String, Object> payload) {
        log.debug("Virtualna telemetrija bi bila poslana za uredjaj {}.", device.getId());
    }
}
