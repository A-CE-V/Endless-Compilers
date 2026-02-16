package com.endlessforge.javadecompilerapi.service.procyon;

import com.strobel.decompiler.Decompiler;
import com.strobel.decompiler.PlainTextOutput;
import com.strobel.decompiler.DecompilerSettings;
import com.strobel.assembler.metadata.ArrayTypeLoader;
import com.strobel.assembler.metadata.JarTypeLoader;
import com.strobel.assembler.metadata.CompositeTypeLoader;
import com.strobel.assembler.metadata.ITypeLoader;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class ProcyonAdapter {

    public String decompileClass(byte[] classBytes, String providedClassName) throws IOException {
        StringWriter writer = new StringWriter();
        PlainTextOutput output = new PlainTextOutput(writer);
        DecompilerSettings settings = DecompilerSettings.javaDefaults();

        // Fix 1: ArrayTypeLoader requires the bytes in the constructor in 0.6.0
        ArrayTypeLoader arrayLoader = new ArrayTypeLoader(classBytes);
        settings.setTypeLoader(arrayLoader);

        String internalName = providedClassName;
        if (internalName == null || internalName.isBlank()) {
            File tmp = Files.createTempFile("procyon-class-", ".class").toFile();
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(classBytes);
            }
            Decompiler.decompile(tmp.getAbsolutePath(), output, settings);
            tmp.delete();
        } else {
            Decompiler.decompile(internalName.replace('.', '/'), output, settings);
        }

        return writer.toString();
    }

    public void decompileJarToZip(File jarFile, ZipOutputStream zos, String targetClass) {
        try {
            DecompilerSettings settings = DecompilerSettings.javaDefaults();
            JarTypeLoader jarLoader = new JarTypeLoader(new JarFile(jarFile));
            settings.setTypeLoader(new CompositeTypeLoader(jarLoader));

            try (JarFile jf = new JarFile(jarFile)) {
                Enumeration<java.util.jar.JarEntry> en = jf.entries();
                while (en.hasMoreElements()) {
                    java.util.jar.JarEntry entry = en.nextElement();
                    if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;

                    String internal = entry.getName().replaceAll("\\.class$", "");

                    if (targetClass != null && !targetClass.isBlank()) {
                        if (!internal.replace('/', '.').equals(targetClass)) continue;
                    }

                    try {
                        StringWriter writer = new StringWriter();
                        Decompiler.decompile(internal, new PlainTextOutput(writer), settings);

                        String path = internal + ".java";
                        synchronized (zos) {
                            zos.putNextEntry(new ZipEntry(path));
                            zos.write(writer.toString().getBytes(StandardCharsets.UTF_8));
                            zos.closeEntry();
                        }
                    } catch (Throwable t) {
                        // Log error
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void decompileJar(File jarFile, File outDir, String targetClass) {
        try {
            DecompilerSettings settings = DecompilerSettings.javaDefaults();

            // Fix 2: JarTypeLoader requires a JarFile object
            JarTypeLoader jarLoader = new JarTypeLoader(new JarFile(jarFile));

            // Fix 3: Proper initialization of CompositeTypeLoader
            ITypeLoader composite = new CompositeTypeLoader(jarLoader);
            settings.setTypeLoader(composite);

            try (JarFile jf = new JarFile(jarFile)) {
                Enumeration<java.util.jar.JarEntry> en = jf.entries();
                while (en.hasMoreElements()) {
                    java.util.jar.JarEntry entry = en.nextElement();
                    if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;

                    String internal = entry.getName().replaceAll("\\.class$", "");

                    // If a specific class is requested, skip others
                    if (targetClass != null && !targetClass.isBlank()) {
                        if (!internal.replace('/', '.').equals(targetClass)) continue;
                    }

                    StringWriter writer = new StringWriter();
                    PlainTextOutput output = new PlainTextOutput(writer);

                    try {
                        Decompiler.decompile(internal, output, settings);
                        File out = new File(outDir, internal + ".java");
                        out.getParentFile().mkdirs();
                        try (FileWriter fw = new FileWriter(out)) {
                            fw.write(writer.toString());
                        }
                    } catch (Throwable t) {
                        // Log failure inside the output file
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}