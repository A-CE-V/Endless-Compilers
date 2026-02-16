package com.endlessforge.javadecompilerapi.controller;

import com.endlessforge.javadecompilerapi.util.ModeDetector;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class InfoController {

    private final ModeDetector modeDetector;

    public InfoController(ModeDetector modeDetector) {
        this.modeDetector = modeDetector;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "ts", System.currentTimeMillis());
    }

    @GetMapping("/modes")
    public Map<String, Object> modes() {
        return Map.of(
                "modes", modeDetector.detectAllModes(),
                "java", modeDetector.isJavaAvailable()
        );
    }
}
