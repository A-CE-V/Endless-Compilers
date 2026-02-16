package com.endlessforge.javadecompilerapi.service.cfr;

import org.benf.cfr.reader.api.*;
import org.benf.cfr.reader.util.getout.SinkReturns;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CFR adapter using the embedded CFR library.
 */
@Component
public class CfrAdapter {

    public String decompileClass(byte[] classBytes, String providedClassName) throws IOException {
        // Write bytes to temp file (CFR expects a file path)
        File tmp = File.createTempFile("cfr-class-", ".class");
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            fos.write(classBytes);
            fos.flush();
        }

        final StringBuilder sb = new StringBuilder();

        OutputSinkFactory mySink = new OutputSinkFactory() {
            @Override
            public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> available) {
                if (sinkType == SinkType.JAVA) return Arrays.asList(SinkClass.DECOMPILED, SinkClass.STRING);
                return Collections.singletonList(SinkClass.STRING);
            }

            @Override
            public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
                if (sinkType == SinkType.JAVA && sinkClass == SinkClass.DECOMPILED) {
                    return (Sink<T>) ((SinkReturns.Decompiled d) -> {
                        sb.append("/* Package: ").append(d.getPackageName()).append(" Class: ").append(d.getClassName()).append(" */\n");
                        sb.append(d.getJava()).append("\n");
                    });
                }
                return t -> {
                    // ignore
                };
            }
        };

        CfrDriver driver = new CfrDriver.Builder().withOutputSink(mySink).build();
        driver.analyse(Collections.singletonList(tmp.getAbsolutePath()));
        tmp.delete();
        return sb.toString();
    }

    public void decompileJar(File jarFile, File outDir, String targetClass) {
        Map<String, String> options = new HashMap<>();
        // you can set specific options if you want
        OutputSinkFactory sinkFactory = OutputSinkFactory.createSinkFactory(new SinkFactoryToDir(outDir));
        CfrDriver driver = new CfrDriver.Builder().withOutputSink(sinkFactory).withOptions(options).build();

        List<String> toAnalyse = new ArrayList<>();
        if (targetClass != null && !targetClass.isBlank()) {
            // CFR can take class names or jar paths; passing jar path will decompile all
            // We'll pass the jar file path; the sink will output files to outDir
            toAnalyse.add(jarFile.getAbsolutePath() + (targetClass.startsWith("/") ? "" : ""));
        } else {
            toAnalyse.add(jarFile.getAbsolutePath());
        }
        driver.analyse(toAnalyse);
    }

    /**
     * Helper sink factory that writes decompiled files into outDir
     * (a minimal convenience wrapper; not a complete implementation)
     */
    static class SinkFactoryToDir implements OutputSinkFactory {
        private final File outDir;
        SinkFactoryToDir(File outDir) { this.outDir = outDir; }

        @Override public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> available) {
            if (sinkType == SinkType.JAVA) return Arrays.asList(SinkClass.DECOMPILED, SinkClass.STRING);
            return Collections.singletonList(SinkClass.STRING);
        }

        @Override public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
            if (sinkType == SinkType.JAVA && sinkClass == SinkClass.DECOMPILED) {
                return (Sink<T>) (d -> {
                    try {
                        SinkReturns.Decompiled dd = (SinkReturns.Decompiled) d;
                        String pkg = dd.getPackageName();
                        String cls = dd.getClassName();
                        String java = dd.getJava();
                        File pkgDir = new File(outDir, pkg.replace('.', File.separatorChar));
                        pkgDir.mkdirs();
                        File out = new File(pkgDir, cls + ".java");
                        try (FileOutputStream fos = new FileOutputStream(out);
                             OutputStreamWriter w = new OutputStreamWriter(fos)) {
                            w.write(java);
                        }
                    } catch (IOException ex) {
                        // ignore
                    }
                });
            }
            return t -> {};
        }
    }
}
