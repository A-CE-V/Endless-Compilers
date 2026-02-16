package com.endlessforge.javadecompilerapi.service.procyon;

import com.strobel.decompiler.Decompiler;
import com.strobel.decompiler.PlainTextOutput;
import com.strobel.decompiler.DecompilerSettings;
import com.strobel.assembler.metadata.ArrayTypeLoader;
import com.strobel.assembler.metadata.JarTypeLoader;
import com.strobel.assembler.metadata.CompositeTypeLoader;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

@Component
public class ProcyonAdapter {

    public String decompileClass(byte[] classBytes, String providedClassName) throws IOException {
        // We can use ArrayTypeLoader when we only have class bytes
        StringWriter writer = new StringWriter();
        PlainTextOutput output = new PlainTextOutput(writer);

        DecompilerSettings settings = DecompilerSettings.javaDefaults();
        ArrayTypeLoader arrayLoader = new ArrayTypeLoader();
        String internalName = providedClassName;
        if (internalName == null || internalName.isBlank()) {
            // write bytes to temp and use path
            File tmp = Files.createTempFile("procyon-class-", ".class").toFile();
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(classBytes);
            }
            Decompiler.decompile(tmp.getAbsolutePath(), output, settings);
            tmp.delete();
        } else {
            // try decompiling by internal name using array loader
            // ArrayTypeLoader expects a map: not public helper, so easiest is write file and call decompile with path
            File tmp = Files.createTempFile("procyon-class-", ".class").toFile();
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(classBytes);
            }
            Decompiler.decompile(tmp.getAbsolutePath(), output, settings);
            tmp.delete();
        }
        return writer.toString();
    }

    public void decompileJar(File jarFile, File outDir, String targetClass) {
        try {
            DecompilerSettings settings = DecompilerSettings.javaDefaults();
            // use JarTypeLoader so Procyon can resolve types inside the jar
            JarTypeLoader jarLoader = new JarTypeLoader(jarFile.getAbsolutePath());
            CompositeTypeLoader composite = new CompositeTypeLoader();
            composite.addTypeLoader(jarLoader);
            settings.setTypeLoader(composite);

            // if targetClass set, decompile only it; otherwise loop entries
            if (targetClass != null && !targetClass.isBlank()) {
                String internal = targetClass.replace('.', '/');
                StringWriter writer = new StringWriter();
                PlainTextOutput output = new PlainTextOutput(writer);
                Decompiler.decompile(internal, output, settings);
                // write writer to file
                File out = new File(outDir, internal + ".java");
                out.getParentFile().mkdirs();
                try (FileWriter fw = new FileWriter(out)) { fw.write(writer.toString()); }
            } else {
                // iterate classes in jar and decompile each
                try (java.util.jar.JarFile jf = new java.util.jar.JarFile(jarFile)) {
                    Enumeration<java.util.jar.JarEntry> en = jf.entries();
                    while (en.hasMoreElements()) {
                        java.util.jar.JarEntry entry = en.nextElement();
                        if (entry.isDirectory()) continue;
                        if (!entry.getName().endsWith(".class")) continue;
                        String internal = entry.getName().replaceAll("\\.class$", "");
                        StringWriter writer = new StringWriter();
                        PlainTextOutput output = new PlainTextOutput(writer);
                        try {
                            Decompiler.decompile(internal, output, settings);
                            File out = new File(outDir, internal + ".java");
                            out.getParentFile().mkdirs();
                            try (FileWriter fw = new FileWriter(out)) { fw.write(writer.toString()); }
                        } catch (Throwable t) {
                            File out = new File(outDir, internal + ".java");
                            out.getParentFile().mkdirs();
                            try (FileWriter fw = new FileWriter(out)) {
                                fw.write("/* decompilation failed: " + t.getMessage() + " */");
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            // bubble up or log in real app
        }
    }
}
