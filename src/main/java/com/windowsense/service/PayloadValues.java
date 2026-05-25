package com.windowsense.service;

import java.util.Map;

final class PayloadValues {

    private PayloadValues() {
    }

    static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return value;
    }

    static String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    static boolean booleanValue(Map<String, Object> payload, String key, boolean fallback) {
        Object value = payload.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }

        if (value instanceof Number number) {
            return number.intValue() == 1;
        }

        if (value instanceof String text) {
            if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
                return true;
            }

            if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
                return false;
            }
        }

        return fallback;
    }

    static double threshold(Map<String, Object> payload, String key, double fallback, double min, double max) {
        if (!payload.containsKey(key)) {
            return fallback;
        }

        return numberValue(payload, key, fallback, min, max);
    }

    static double numberValue(Map<String, Object> payload, String key, double fallback, double min, double max) {
        Object value = payload.get(key);
        if (value == null || "".equals(value)) {
            return fallback;
        }

        double parsed;
        if (value instanceof Number number) {
            parsed = number.doubleValue();
        } else {
            try {
                parsed = Double.parseDouble(value.toString());
            } catch (NumberFormatException error) {
                parsed = fallback;
            }
        }

        return clamp(parsed, min, max);
    }

    static double clamp(double value, double min, double max) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return min;
        }

        return Math.min(max, Math.max(min, value));
    }
}
