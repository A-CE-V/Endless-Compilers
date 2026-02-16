package com.endlessforge.javadecompilerapi.service;

import com.endlessforge.javadecompilerapi.service.cfr.CfrAdapter;
import com.endlessforge.javadecompilerapi.service.external.ExternalToolAdapter;
import com.endlessforge.javadecompilerapi.service.jadx.JadxAdapter;
import com.endlessforge.javadecompilerapi.service.procyon.ProcyonAdapter;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.nio.file.StandardCopyOption;

@Service
public class DecompilerService {

    private final CfrAdapter cfr;
    private final ProcyonAdapter procyon;
    private final JadxAdapter jadx;
    private final ExternalToolAdapter externalAdapter;

    public DecompilerService(CfrAdapter cfr, ProcyonAdapter procyon, JadxAdapter jadx, ExternalToolAdapter externalAdapter) {
        this.cfr = cfr;
        this.procyon = procyon;
        this.jadx = jadx;
        this.externalAdapter = externalAdapter;
    }

    /**
     * Decompile single .class multipart file.
     * returns a map with keys: mode, className, source
     */
    public Map<String,Object> decompileSingleClass(MultipartFile file, String mode, String className) throws IOException {
        byte[] bytes = file.getBytes();
        String normalizedMode = mode.toLowerCase(Locale.ROOT);
        String source = null;
        String usedClassName = className;

        switch (normalizedMode) {
            case "cfr":
                source = cfr.decompileClass(bytes, className);
                break;
            case "procyon":
                source = procyon.decompileClass(bytes, className);
                break;
            case "jadx":
                source = jadx.decompileClass(bytes, className);
                break;
            default:
                // fallback to external tool invocation (jars in ./tools)
                source = externalAdapter.decompileClassWithExternalTool(bytes, normalizedMode, className);
                break;
        }

        return Map.of(
                "ok", source != null,
                "mode", normalizedMode,
                "className", usedClassName,
                "source", source == null ? "": source
        );
    }

    /**
     * Decompile a JAR. If targetClass is null, returns a zip file containing all sources.
     * If targetClass present, returns zip with a single file or the single source content file (but for simplicity we zip).
     */
    public File decompileJar(MultipartFile file, String mode, String targetClass) throws IOException {
        Path tmpDir = Files.createTempDirectory("decompile-jar-");
        Path jarPath = tmpDir.resolve(file.getOriginalFilename() != null ? file.getOriginalFilename() : "uploaded.jar");

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, jarPath, StandardCopyOption.REPLACE_EXISTING);
        }

        String normalizedMode = mode.toLowerCase(Locale.ROOT);
        File zipFile = tmpDir.resolve("decompiled-" + normalizedMode + ".zip").toFile();

        // OPTIMIZATION: Stream directly to Zip, bypassing intermediate files
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(fos))) {

            // Set compression level to STORE (0) or FAST (1) to save CPU?
            // Actually, default is fine, but if CPU is the bottleneck, reducing compression helps.
            zos.setLevel(1);

            switch (normalizedMode) {
                case "cfr":
                    cfr.decompileJarToZip(jarPath.toFile(), zos, targetClass);
                    break;
                case "procyon":
                    procyon.decompileJarToZip(jarPath.toFile(), zos, targetClass);
                    break;
                case "jadx":
                    jadx.decompileJarToZip(jarPath.toFile(), zos, targetClass);
                    break;
                default:
                    // External adapters still need disk I/O because they are CLI tools
                    // This will remain slow, but that's unavoidable for "external" mode.
                    File outDir = Files.createTempDirectory("ext-out-").toFile();
                    externalAdapter.decompileJarWithExternalTool(jarPath.toFile(), outDir, normalizedMode, targetClass);
                    zipDirectory(outDir, zos); // Helper to zip the folder if external was used
                    break;
            }
        }

        return zipFile;
    }

    // Helper strictly for the External adapter fallback
    private void zipDirectory(File sourceDir, ZipOutputStream zs) throws IOException {
        Path pp = sourceDir.toPath();
        if (!Files.exists(pp)) return;
        Files.walk(pp).filter(p -> !Files.isDirectory(p)).forEach(path -> {
            ZipEntry zipEntry = new ZipEntry(pp.relativize(path).toString());
            try {
                zs.putNextEntry(zipEntry);
                Files.copy(path, zs);
                zs.closeEntry();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }



}
