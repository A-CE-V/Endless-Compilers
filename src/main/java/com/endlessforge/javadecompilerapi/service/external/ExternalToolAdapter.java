package com.endlessforge.javadecompilerapi.service.external;

import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fallback adapter: if a library is not available, try running a tool jar in ./tools/
 * Tools expected:
 *  - fernflower.jar
 *  - forgeflower.jar
 *  - procyon-decompiler.jar (if library not embedded)
 *  - jd-cli.jar
 *  - jad binary (native)
 *
 * This runs a process; since Spring Boot runs inside one JVM, occasional process invocation is acceptable,
 * but for heavy workloads prefer embedding libraries.
 */
@Component
public class ExternalToolAdapter {

    private final File toolsDir = new File("tools");

    public String decompileClassWithExternalTool(byte[] classBytes, String mode, String className) throws IOException {
        // writes class to temp file and runs jar tool with appropriate args, tries common jar names
        File tmp = Files.createTempFile("external-class-", ".class").toFile();
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            fos.write(classBytes);
        }

        File toolJar = findToolJarForMode(mode);
        if (toolJar == null) {
            return "external tool not installed for mode=" + mode;
        }

        // Example default: java -jar tool.jar <class-file> -o <outdir>  (actual args vary per tool)
        File outDir = Files.createTempDirectory("ext-out-").toFile();
        ProcessBuilder pb = new ProcessBuilder("java", "-jar", toolJar.getAbsolutePath(), tmp.getAbsolutePath(), outDir.getAbsolutePath());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = readStream(p.getInputStream());
        try {
            int code = p.waitFor();
            if (code != 0) {
                return "tool failed: " + output;
            }
            // try to find .java file in outDir
            File[] files = FileUtils.listFiles(outDir, new String[]{"java"}, true).toArray(new File[0]);
            if (files.length > 0) {
                return FileUtils.readFileToString(files[0], "UTF-8");
            } else {
                return "no java output from tool";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "interrupted";
        } finally {
            tmp.delete();
        }
    }

    public void decompileJarWithExternalTool(File jarFile, File outDir, String mode, String targetClass) throws IOException {
        File toolJar = findToolJarForMode(mode);
        if (toolJar == null) throw new IOException("tool jar not found for mode " + mode);

        // generic invocation (most tools accept input jar and output dir)
        ProcessBuilder pb = new ProcessBuilder("java", "-jar", toolJar.getAbsolutePath(), jarFile.getAbsolutePath(), outDir.getAbsolutePath());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (InputStream is = p.getInputStream()) { readStream(is); }
        try {
            p.waitFor();
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
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

    private File findToolJarForMode(String mode) {
        String nm = mode.toLowerCase(Locale.ROOT);
        if ("fernflower".equals(nm)) return fileIfExists("fernflower.jar");
        if ("forgeflower".equals(nm)) return fileIfExists("forgeflower.jar");
        if ("procyon".equals(nm)) return fileIfExists("procyon-decompiler.jar");
        if ("jdcore".equals(nm) || "jd".equals(nm)) return fileIfExists("jd-cli.jar");
        if ("jad".equals(nm)) return fileIfExists("jad"); // native
        return null;
    }

    private File fileIfExists(String name) {
        File f = new File(toolsDir, name);
        return f.exists() ? f : null;
    }

    private String readStream(InputStream is) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            is.transferTo(baos);
            return baos.toString("UTF-8");
        }
    }
}
