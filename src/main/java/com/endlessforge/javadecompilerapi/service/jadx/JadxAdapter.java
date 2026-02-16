package com.endlessforge.javadecompilerapi.service.jadx;

import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class JadxAdapter {

    public String decompileClass(byte[] classBytes, String providedName) throws IOException {
        // easiest approach: write jar or class to temp file and run jadx in-memory
        File tmp = java.nio.file.Files.createTempFile("jadx-in-", ".class").toFile();
        java.nio.file.Files.write(tmp.toPath(), classBytes);

        JadxArgs args = new JadxArgs();
        args.getInputFiles().add(tmp);
        args.setSkipResources(true);
        args.setOutDirSrc(null); // we don't need to write to disk
        try (JadxDecompiler jadx = new JadxDecompiler(args)) {
            jadx.load();
            List<JavaClass> classes = jadx.getClasses();
            StringBuilder sb = new StringBuilder();
            for (JavaClass c : classes) {
                sb.append("// Class: ").append(c.getFullName()).append("\n");
                sb.append(c.getCode()).append("\n\n");
            }
            tmp.delete();
            return sb.toString();
        } catch (Exception e) {
            tmp.delete();
            throw new IOException("jadx failure: " + e.getMessage(), e);
        }
    }

    public void decompileJarToZip(File jarFile, ZipOutputStream zos, String targetClass) {
        JadxArgs args = new JadxArgs();
        args.getInputFiles().add(jarFile);
        args.setSkipResources(true);
        // Reduce thread count to 1 for 0.1 CPU environments to prevent context switching overload
        args.setThreadsCount(1);

        try (JadxDecompiler jadx = new JadxDecompiler(args)) {
            jadx.load();

            for (JavaClass cls : jadx.getClasses()) {
                if (targetClass != null && !targetClass.isBlank()) {
                    if (!cls.getFullName().equals(targetClass)) continue;
                }

                String code = cls.getCode(); // Decompile happens here
                String path = cls.getFullName().replace('.', '/') + ".java";

                synchronized (zos) {
                    try {
                        zos.putNextEntry(new ZipEntry(path));
                        zos.write(code.getBytes(StandardCharsets.UTF_8));
                        zos.closeEntry();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void decompileJar(File jarFile, File outDir, String targetClass) {
        JadxArgs args = new JadxArgs();
        args.getInputFiles().add(jarFile);
        args.setOutDir(new File(outDir, "jadx-out"));
        args.setSkipResources(true);
        args.setThreadsCount(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
        try (JadxDecompiler jadx = new JadxDecompiler(args)) {
            jadx.load();
            if (targetClass != null && !targetClass.isBlank()) {
                for (JavaClass c : jadx.getClasses()) {
                    if (c.getFullName().endsWith(targetClass) || c.getFullName().equals(targetClass)) {
                        File out = new File(outDir, c.getFullName().replace('.', File.separatorChar) + ".java");
                        out.getParentFile().mkdirs();
                        try (java.io.FileWriter fw = new java.io.FileWriter(out)) {
                            fw.write(c.getCode());
                        }
                    }
                }
            } else {
                jadx.save(); // writes to args.outDir
                // move jadx output (if any) to outDir root
                // jadx saved to outDir/jadx-out -> copy to outDir
            }
        } catch (Exception e) {
            // log
        }
    }
}
