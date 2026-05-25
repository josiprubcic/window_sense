package com.windowsense.model;

public record AckRequest(
        String commandId,
        String status
) {
}
