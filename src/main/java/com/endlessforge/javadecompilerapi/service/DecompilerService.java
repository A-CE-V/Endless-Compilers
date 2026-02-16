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
        Path tmp = Files.createTempDirectory("decompile-jar-");
        Path jarPath = tmp.resolve(file.getOriginalFilename() != null ? file.getOriginalFilename() : "uploaded.jar");

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, jarPath, StandardCopyOption.REPLACE_EXISTING);
        }


        String normalizedMode = mode.toLowerCase(Locale.ROOT);
        Path outDir = Files.createTempDirectory("decompile-out-");

        switch (normalizedMode) {
            case "cfr":
                cfr.decompileJar(jarPath.toFile(), outDir.toFile(), targetClass);
                break;
            case "procyon":
                procyon.decompileJar(jarPath.toFile(), outDir.toFile(), targetClass);
                break;
            case "jadx":
                jadx.decompileJar(jarPath.toFile(), outDir.toFile(), targetClass);
                break;
            default:
                externalAdapter.decompileJarWithExternalTool(jarPath.toFile(), outDir.toFile(), normalizedMode, targetClass);
                break;
        }

        // zip outDir -> archive
        File zipFile = tmp.resolve(file.getOriginalFilename() + "-" + normalizedMode + "-decompiled.zip").toFile();
        zipDirectory(outDir.toFile(), zipFile);
        // cleanup outDir (not deleting tmp yet)
        return zipFile;
    }

    private void zipDirectory(File sourceDir, File outFile) throws IOException {
        try (ZipOutputStream zs = new ZipOutputStream(new FileOutputStream(outFile))) {
            Path pp = sourceDir.toPath();
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

    private File runJarTool(String toolJarName, File inputFile) throws Exception {
        File outputDir = Files.createTempDirectory("ext-decompile").toFile();

        List<String> command = new ArrayList<>();
        command.add("java");
        command.add("-jar");
        command.add("tools/" + toolJarName);

        if (toolJarName.contains("fernflower")) {
            command.add(inputFile.getAbsolutePath());
            command.add(outputDir.getAbsolutePath());
        } else if (toolJarName.contains("jd-cli")) {
            command.add(inputFile.getAbsolutePath());
            command.add("-od");
            command.add(outputDir.getAbsolutePath());
        } else {
            throw new IllegalArgumentException("Unsupported external tool: " + toolJarName);
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("External tool failed with exit code: " + exitCode);
        }

        return outputDir;
    }

}
