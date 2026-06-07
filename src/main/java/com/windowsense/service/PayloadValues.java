package com.windowsense.service;

final class PayloadValues {

    private PayloadValues() {
    }

    static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return value;
    }

    static double clamp(double value, double min, double max) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return min;
        }

        return Math.min(max, Math.max(min, value));
    }
}
