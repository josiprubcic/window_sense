package com.windowsense.exception;

public class ThingsBoardProvisioningException extends RuntimeException {

    public ThingsBoardProvisioningException(String message) {
        super(message);
    }

    public ThingsBoardProvisioningException(String message, Throwable cause) {
        super(message, cause);
    }
}
