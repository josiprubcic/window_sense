package com.windowsense.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class PhysicalDevicePairingCodeHasher {

    private PhysicalDevicePairingCodeHasher() {
    }

    public static String hash(String pairingCode) {
        return sha256Hex(normalize(pairingCode));
    }

    public static String normalize(String pairingCode) {
        if (pairingCode == null) {
            return "";
        }
        return pairingCode.trim().toUpperCase().replaceAll("\\s+", "");
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 nije dostupan.", error);
        }
    }
}
