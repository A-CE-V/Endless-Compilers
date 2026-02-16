package com.endlessforge.javadecompilerapi.util;

import org.springframework.stereotype.Component;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@Component
public class ModeDetector {

    public boolean isJavaAvailable() {
        try {
            Process p = new ProcessBuilder("java", "-version").start();
            int c = p.waitFor();
            return c == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public Map<String, Object> detectAllModes() {
        Map<String, Object> m = new HashMap<>();
        m.put("cfr", classExists("org.benf.cfr.reader.api.CfrDriver"));
        m.put("procyon", classExists("com.strobel.decompiler.Decompiler"));
        m.put("jadx", classExists("jadx.api.JadxDecompiler"));
        // jdcore/fernflower might be available as libraries or via tools/
        m.put("jdcore", classExists("org.jd.core.Decompiler") || new File("tools/jd-cli.jar").exists());
        m.put("fernflower", classExists("org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler") || new File("tools/fernflower.jar").exists() || new File("tools/forgeflower.jar").exists());
        m.put("jad", new File("tools/jad").exists());
        m.put("java", isJavaAvailable());
        return m;
    }

    public boolean isModeAvailable(String mode) {
        return Boolean.TRUE.equals(detectAllModes().getOrDefault(mode.toLowerCase(), false));
    }

    private boolean classExists(String fqcn) {
        try {
            Class.forName(fqcn);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
