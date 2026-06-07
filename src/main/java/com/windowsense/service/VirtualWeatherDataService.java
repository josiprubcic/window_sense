package com.windowsense.service;

import com.windowsense.config.WindowSenseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class VirtualWeatherDataService {

    private static final Logger log = LoggerFactory.getLogger(VirtualWeatherDataService.class);

    private final WindowSenseProperties.VirtualSimulator properties;

    public VirtualWeatherDataService(WindowSenseProperties properties) {
        this.properties = properties.getVirtualSimulator();
    }

    public List<VirtualWeatherSample> samples() {
        Path path = Path.of(properties.getWeatherDataFilePath());
        if (!Files.exists(path)) {
            log.warn("Datoteka virtualne vremenske simulacije ne postoji: {}. Koristim fallback podatke.", path);
            return fallbackSamples();
        }

        try {
            List<String> lines = Files.readAllLines(path);
            List<VirtualWeatherSample> samples = new ArrayList<>();
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isBlank()) {
                    continue;
                }
                samples.add(parseLine(line));
            }
            return samples.isEmpty() ? fallbackSamples() : samples;
        } catch (IOException | IllegalArgumentException error) {
            log.warn("Datoteku virtualne vremenske simulacije nije moguce procitati. Koristim fallback podatke.");
            return fallbackSamples();
        }
    }

    private VirtualWeatherSample parseLine(String line) {
        String[] values = line.split(",");
        if (values.length != 8) {
            throw new IllegalArgumentException("Red nema ocekivanih 8 stupaca.");
        }

        return new VirtualWeatherSample(
                booleanValue(values[0]),
                intValue(values[1], 0, 100),
                intValue(values[2], 0, 100),
                intValue(values[3], 0, 120000),
                doubleValue(values[4], -20, 60),
                intValue(values[5], 0, 160),
                intValue(values[6], 0, 100),
                intValue(values[7], 0, 100)
        );
    }

    private boolean booleanValue(String raw) {
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "true", "1", "rain", "raining" -> true;
            case "false", "0", "dry", "no-rain" -> false;
            default -> throw new IllegalArgumentException("Neispravna boolean vrijednost.");
        };
    }

    private int intValue(String raw, int min, int max) {
        int value = Integer.parseInt(raw.trim());
        return Math.max(min, Math.min(max, value));
    }

    private double doubleValue(String raw, double min, double max) {
        double value = Double.parseDouble(raw.trim());
        return Math.max(min, Math.min(max, value));
    }

    private List<VirtualWeatherSample> fallbackSamples() {
        return List.of(
                new VirtualWeatherSample(false, 0, 12, 52000, 23.5, 8, 72, 20),
                new VirtualWeatherSample(false, 0, 22, 36000, 24.1, 14, 66, 35),
                new VirtualWeatherSample(true, 45, 78, 14500, 22.8, 26, 28, 82),
                new VirtualWeatherSample(true, 82, 96, 6200, 21.9, 38, 5, 100)
        );
    }
}
