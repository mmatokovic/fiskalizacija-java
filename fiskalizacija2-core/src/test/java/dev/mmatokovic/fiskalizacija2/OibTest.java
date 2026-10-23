package dev.mmatokovic.fiskalizacija2;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OibTest {

    @Test
    void ispravniOibi() {
        assertTrue(Oib.ispravan("12345678903"));
        assertTrue(Oib.ispravan("11111111119"));
        assertTrue(Oib.ispravan("69435151530"));
    }

    @Test
    void neispravniOibi() {
        assertFalse(Oib.ispravan("12345678901"), "kriva kontrolna znamenka");
        assertFalse(Oib.ispravan("1234567890"), "10 znamenki");
        assertFalse(Oib.ispravan("1234567890a"));
        assertFalse(Oib.ispravan(null));
        assertFalse(Oib.ispravan(""));
        assertFalse(Oib.ispravan("123"));
        assertFalse(Oib.ispravan(" 01234567890 "));
        assertFalse(Oib.ispravan("           "));
        assertFalse(Oib.ispravan("X1234567890"));
        assertFalse(Oib.ispravan("12345678900"));
        assertFalse(Oib.ispravan("١٢٣٤٥٦٧٨٩٠٣"), "arapsko-indijske znamenke nisu OIB");
    }

    @Test
    void neispravanOibProdavateljaUUblJeGreska() {
        String xml = new String(Primjeri.eRacun("eRacun_PDV25.xml"), StandardCharsets.UTF_8)
                .replace(Primjeri.OIB, "12345678901");

        var e = assertThrows(UblException.class, () -> UblParser.racun(xml.getBytes(StandardCharsets.UTF_8)));

        assertTrue(e.greske().stream().anyMatch(g -> g.contains("BT-31") || g.contains("BT-34")), e.greske().toString());
        assertTrue(e.greske().stream().anyMatch(g -> g.contains("HR-BT-5")), e.greske().toString());
    }

    @Test
    void velikiERacunSPrilogomSeCita() {
        String prilog = "<cac:AdditionalDocumentReference><cbc:ID>prilog</cbc:ID><cac:Attachment>"
                + "<cbc:EmbeddedDocumentBinaryObject mimeCode=\"application/pdf\" filename=\"racun.pdf\">"
                + "A".repeat(30 * 1024 * 1024) + "</cbc:EmbeddedDocumentBinaryObject></cac:Attachment>"
                + "</cac:AdditionalDocumentReference>";
        String xml = new String(Primjeri.eRacun("eRacun_PDV25.xml"), StandardCharsets.UTF_8)
                .replaceFirst("<cac:AccountingSupplierParty>", prilog + "<cac:AccountingSupplierParty>");

        assertEquals("1-P1-1", UblParser.racun(xml.getBytes(StandardCharsets.UTF_8)).brojDokumenta());
    }
}
