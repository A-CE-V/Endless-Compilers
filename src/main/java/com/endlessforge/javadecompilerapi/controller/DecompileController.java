package com.endlessforge.javadecompilerapi.controller;

import com.endlessforge.javadecompilerapi.service.DecompilerService;
import com.endlessforge.javadecompilerapi.util.ModeDetector;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.Map;

@RestController
@RequestMapping("/decompile")
public class DecompileController {

    private final DecompilerService decompilerService;
    private final ModeDetector modeDetector;

    public DecompileController(DecompilerService decompilerService, ModeDetector modeDetector) {
        this.decompilerService = decompilerService;
        this.modeDetector = modeDetector;
    }

    @PostMapping("/class")
    public ResponseEntity<?> decompileClass(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "mode", defaultValue = "cfr") String mode,
            @RequestParam(value = "className", required = false) String className
    ) throws IOException {
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "file required"));

        // validate mode availability
        if (!modeDetector.isModeAvailable(mode)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "mode not available", "mode", mode, "advice", "place tool JARs in ./tools or use mode=cfr"));
        }

        Map<String, Object> result = decompilerService.decompileSingleClass(file, mode, className);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/jar")
    public ResponseEntity<?> decompileJar(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "mode", defaultValue = "cfr") String mode,
            @RequestParam(value = "className", required = false) String targetClass
    ) throws IOException {

        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "file required"));

        if (!modeDetector.isModeAvailable(mode)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "mode not available", "mode", mode, "advice", "place tool JARs in ./tools or use mode=cfr"));
        }

        // For jar we may return a zip; service returns File path if zip created
        File zip = decompilerService.decompileJar(file, mode, targetClass);
        if (zip == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "decompilation failed"));
        }

        InputStreamResource resource = new InputStreamResource(new FileInputStream(zip));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment().filename(zip.getName()).build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
    }
}
