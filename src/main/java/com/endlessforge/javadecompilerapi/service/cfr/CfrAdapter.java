package com.endlessforge.javadecompilerapi.service.cfr;

import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.api.SinkReturns;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;

@Component
public class CfrAdapter {

    public String decompileClass(byte[] classBytes, String providedClassName) throws IOException {
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
                    // Fix: Explicitly return the Sink and cast the input object manually
                    return x -> {
                        if (x instanceof SinkReturns.Decompiled) {
                            SinkReturns.Decompiled d = (SinkReturns.Decompiled) x;
                            sb.append("/* Package: ").append(d.getPackageName())
                                    .append(" Class: ").append(d.getClassName()).append(" */\n");
                            sb.append(d.getJava()).append("\n");
                        }
                    };
                }
                return t -> {};
            }
        };

        CfrDriver driver = new CfrDriver.Builder().withOutputSink(mySink).build();
        driver.analyse(Collections.singletonList(tmp.getAbsolutePath()));
        tmp.delete();
        return sb.toString();
    }

    public void decompileJar(File jarFile, File outDir, String targetClass) {
        // Fix: Removed the non-existent createSinkFactory call
        OutputSinkFactory sinkFactory = new SinkFactoryToDir(outDir);
        CfrDriver driver = new CfrDriver.Builder().withOutputSink(sinkFactory).build();

        List<String> toAnalyse = new ArrayList<>();
        toAnalyse.add(jarFile.getAbsolutePath());
        driver.analyse(toAnalyse);
    }

    static class SinkFactoryToDir implements OutputSinkFactory {
        private final File outDir;
        SinkFactoryToDir(File outDir) { this.outDir = outDir; }

        @Override public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> available) {
            if (sinkType == SinkType.JAVA) return Arrays.asList(SinkClass.DECOMPILED, SinkClass.STRING);
            return Collections.singletonList(SinkClass.STRING);
        }

        @Override public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
            if (sinkType == SinkType.JAVA && sinkClass == SinkClass.DECOMPILED) {
                return x -> {
                    if (x instanceof SinkReturns.Decompiled) {
                        try {
                            SinkReturns.Decompiled dd = (SinkReturns.Decompiled) x;
                            String pkg = dd.getPackageName();
                            String cls = dd.getClassName();
                            String java = dd.getJava();
                            File pkgDir = new File(outDir, pkg.replace('.', File.separatorChar));
                            pkgDir.mkdirs();
                            File out = new File(pkgDir, cls + ".java");
                            try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(out))) {
                                w.write(java);
                            }
                        } catch (IOException ignored) {}
                    }
                };
            }
            return t -> {};
        }
    }
}