package com.windowsense.model;

public class CommandResult {
    public String target;
    public String action;
    public Double positionPercent;
    public WindowSenseState.Command queued;
    public String mode;

    public static CommandResult mode(String mode) {
        CommandResult result = new CommandResult();
        result.mode = mode;
        return result;
    }

    public static CommandResult command(String target, String action, Double positionPercent, WindowSenseState.Command queued) {
        CommandResult result = new CommandResult();
        result.target = target;
        result.action = action;
        result.positionPercent = positionPercent;
        result.queued = queued;
        return result;
    }
}
