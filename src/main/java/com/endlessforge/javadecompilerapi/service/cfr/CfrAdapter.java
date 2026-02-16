package com.endlessforge.javadecompilerapi.service.cfr;

import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.api.SinkReturns;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

    public void decompileJarToZip(File jarFile, ZipOutputStream zos, String targetClass) {
        OutputSinkFactory sinkFactory = new SinkFactoryToZip(zos);

        // Options to reduce memory/cpu usage
        Map<String, String> options = new HashMap<>();
        options.put("clobber", "true"); // Don't check for file existence

        CfrDriver driver = new CfrDriver.Builder()
                .withOutputSink(sinkFactory)
                .withOptions(options)
                .build();

        List<String> toAnalyse = Collections.singletonList(jarFile.getAbsolutePath());
        driver.analyse(toAnalyse);
    }



    static class SinkFactoryToZip implements OutputSinkFactory {
        private final ZipOutputStream zos;

        SinkFactoryToZip(ZipOutputStream zos) { this.zos = zos; }

        @Override public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> available) {
            // We only care about the decompiled source
            if (sinkType == SinkType.JAVA) return Collections.singletonList(SinkClass.DECOMPILED);
            return Collections.emptyList();
        }

        @Override public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
            if (sinkType == SinkType.JAVA && sinkClass == SinkClass.DECOMPILED) {
                return x -> {
                    if (x instanceof SinkReturns.Decompiled) {
                        SinkReturns.Decompiled dd = (SinkReturns.Decompiled) x;
                        String path = dd.getPackageName().replace('.', '/') + "/" + dd.getClassName() + ".java";

                        // ZOS is not thread-safe, and CFR might be multi-threaded
                        synchronized (zos) {
                            try {
                                zos.putNextEntry(new ZipEntry(path));
                                zos.write(dd.getJava().getBytes(StandardCharsets.UTF_8));
                                zos.closeEntry();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                };
            }
            return t -> {};
        }
    }
}