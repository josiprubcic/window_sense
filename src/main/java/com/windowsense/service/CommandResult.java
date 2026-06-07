package com.windowsense.service;

import com.windowsense.entity.RuntimeState;

public class CommandResult {
    public String target;
    public String action;
    public Double positionPercent;
    public RuntimeState.Command queued;

    public static CommandResult command(String target, String action, Double positionPercent, RuntimeState.Command queued) {
        CommandResult result = new CommandResult();
        result.target = target;
        result.action = action;
        result.positionPercent = positionPercent;
        result.queued = queued;
        return result;
    }
}
