package com.windowsense.service;

import com.windowsense.config.WindowSenseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@Service
public class RainFileService {

    private static final Logger log = LoggerFactory.getLogger(RainFileService.class);

    private final WindowSenseProperties.VirtualSimulator properties;

    public RainFileService(WindowSenseProperties properties) {
        this.properties = properties.getVirtualSimulator();
    }

    public boolean isRainDetected() {
        Path path = Path.of(properties.getRainStateFilePath());
        if (!Files.exists(path)) {
            return false;
        }

        try {
            String value = Files.readString(path).trim().toLowerCase(Locale.ROOT);
            return switch (value) {
                case "true", "1", "rain", "raining" -> true;
                case "false", "0", "no-rain", "dry" -> false;
                default -> {
                    log.warn("Nepoznata vrijednost u datoteci stanja kise: '{}'. Koristim rainDetected=false.", value);
                    yield false;
                }
            };
        } catch (IOException error) {
            log.warn("Datoteku stanja kise nije moguce procitati. Koristim rainDetected=false.");
            return false;
        }
    }
}
