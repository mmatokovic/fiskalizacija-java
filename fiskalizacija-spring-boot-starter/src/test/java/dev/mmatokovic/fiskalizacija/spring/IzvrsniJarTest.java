package dev.mmatokovic.fiskalizacija.spring;

import static org.assertj.core.api.Assertions.assertThat;

import dev.mmatokovic.fiskalizacija.b2b.B2bClient;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.loader.net.protocol.Handlers;

/** Spring Boot izvršni jar daje resurse kao {@code jar:nested:} URL-ove. */
class IzvrsniJarTest {

    private static final String XSD = "dev.mmatokovic.fiskalizacija.internal.xml.Xsd";

    @Test
    void xsdSeUcitavaIzUgnijezdenogJara(@TempDir Path dir) throws Exception {
        var app = dir.resolve("app.jar");
        try (var out = new JarOutputStream(Files.newOutputStream(app))) {
            byte[] core = core();
            var crc = new CRC32();
            crc.update(core);
            var entry = new ZipEntry("BOOT-INF/lib/fiskalizacija-core.jar");
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(core.length);
            entry.setCrc(crc.getValue());
            out.putNextEntry(entry);
            out.write(core);
        }
        Handlers.register();
        var url = new URL("jar:nested:" + app.toUri().getRawPath() + "/!BOOT-INF/lib/fiskalizacija-core.jar!/");

        try (var loader = new URLClassLoader(new URL[]{url}, ClassLoader.getPlatformClassLoader())) {
            var xsd = Class.forName(XSD, true, loader);
            assertThat(xsd.getResource("xsd/eFiskalizacijaSchema.xsd")).hasProtocol("jar");
            assertThat(xsd.getResource("xsd/eFiskalizacijaSchema.xsd").getPath()).startsWith("nested:");
        }
    }

    private static byte[] core() throws Exception {
        var izvor = Path.of(B2bClient.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var bajtovi = new ByteArrayOutputStream();
        try (var out = new JarOutputStream(bajtovi)) {
            if (Files.isDirectory(izvor)) {
                try (Stream<Path> datoteke = Files.walk(izvor)) {
                    for (var d : datoteke.filter(Files::isRegularFile).toList()) {
                        dodaj(out, izvor.relativize(d).toString().replace('\\', '/'), Files.readAllBytes(d));
                    }
                }
            } else {
                try (var jar = new JarFile(izvor.toFile())) {
                    for (var e : jar.stream().filter(e -> !e.isDirectory()).toList()) {
                        try (var in = jar.getInputStream(e)) {
                            dodaj(out, e.getName(), in.readAllBytes());
                        }
                    }
                }
            }
        }
        return bajtovi.toByteArray();
    }

    private static void dodaj(JarOutputStream out, String ime, byte[] sadrzaj) throws IOException {
        out.putNextEntry(new ZipEntry(ime));
        out.write(sadrzaj);
        out.closeEntry();
    }
}
