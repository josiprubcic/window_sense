package com.windowsense.service;

import com.windowsense.entity.RuntimeState;
import com.windowsense.repository.RuntimeStateRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RuntimeStateService {

    private final RuntimeStateRepository repository;

    public RuntimeStateService(RuntimeStateRepository repository) {
        this.repository = repository;
    }

    public String currentTime() {
        return RuntimeState.now();
    }

    public List<RuntimeState.Event> events() {
        return repository.getState().events;
    }
}
