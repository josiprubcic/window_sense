package com.windowsense.service;

import com.windowsense.config.WindowSenseProperties;
import com.windowsense.service.RainFileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RainFileServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void readsTrue() throws Exception {
        assertThat(serviceWithValue("true").isRainDetected()).isTrue();
    }

    @Test
    void readsFalse() throws Exception {
        assertThat(serviceWithValue("false").isRainDetected()).isFalse();
    }

    @Test
    void readsOne() throws Exception {
        assertThat(serviceWithValue("1").isRainDetected()).isTrue();
    }

    @Test
    void readsZero() throws Exception {
        assertThat(serviceWithValue("0").isRainDetected()).isFalse();
    }

    @Test
    void missingFileReturnsFalse() {
        WindowSenseProperties properties = new WindowSenseProperties();
        properties.getVirtualSimulator().setRainStateFilePath(tempDir.resolve("missing.txt").toString());

        assertThat(new RainFileService(properties).isRainDetected()).isFalse();
    }

    private RainFileService serviceWithValue(String value) throws Exception {
        Path file = tempDir.resolve("stanje_kise.txt");
        Files.writeString(file, value);
        WindowSenseProperties properties = new WindowSenseProperties();
        properties.getVirtualSimulator().setRainStateFilePath(file.toString());
        return new RainFileService(properties);
    }
}
