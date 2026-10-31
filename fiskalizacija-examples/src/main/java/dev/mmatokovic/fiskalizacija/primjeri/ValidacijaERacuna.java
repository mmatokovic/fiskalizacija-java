package dev.mmatokovic.fiskalizacija.primjeri;

import dev.mmatokovic.fiskalizacija.eracun.ERacunValidator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Validira eRačun prema UBL XSD-u, EN 16931 i HR CIUS pravilima. Ne treba certifikat. */
public final class ValidacijaERacuna {

    public static void main(String[] args) throws Exception {
        byte[] xml = args.length > 0
                ? Files.readAllBytes(Path.of(args[0]))
                : TestnoOkruzenje.primjer().getBytes(StandardCharsets.UTF_8);

        var rezultat = ERacunValidator.validiraj(xml);

        rezultat.nalazi().forEach(n -> System.out.println(n.razina() + " " + n.pravilo() + ": " + n.poruka()));
        System.out.println(rezultat.ispravan() ? "eRačun je ispravan" : "eRačun nije ispravan");
    }
}
