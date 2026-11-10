package com.novacorp.inmonode.inmonodebackend.shared.infrastructure.documents;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PdfBoxGeneratorTest {
    @Test void all360InstallmentsRemainReadableAcrossPagesAndEditingIsForbidden() throws Exception {
        var lines = new ArrayList<String>();
        lines.add("Vigente hasta: 2026-10-16 | Inicial: 9000.00 PEN | TEA: 12.0000 %");
        for (int quota = 1; quota <= 360; quota++) lines.add("Cuota " + quota + " | Capital | Interés | Saldo");
        var bytes = new PdfBoxGenerator().generateReadOnly("Cotización de financiamiento", lines);
        assertTrue(bytes.length > 1000);
        try (var pdf = Loader.loadPDF(bytes)) {
            assertTrue(pdf.getNumberOfPages() >= 8);
            var text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("Cuota 1 |"));
            assertTrue(text.contains("Cuota 360 |"));
            assertTrue(text.contains("9000.00 PEN"));
            assertTrue(text.contains("Vigente hasta"));
            assertTrue(pdf.isEncrypted());
            assertFalse(pdf.getCurrentAccessPermission().canModify());
            assertFalse(pdf.getCurrentAccessPermission().canModifyAnnotations());
            assertFalse(pdf.getCurrentAccessPermission().canAssembleDocument());
        }
    }

    @Test void longTextWrapsInsteadOfLosingTheLastCharacters() throws Exception {
        var bytes = new PdfBoxGenerator().generateReadOnly("Cotización", List.of("Precio ".repeat(100) + "FIN"));
        try (var pdf = Loader.loadPDF(bytes)) { assertTrue(new PDFTextStripper().getText(pdf).contains("FIN")); }
    }

    @Test void longTextWrapsBetweenWords() throws Exception {
        var bytes = new PdfBoxGenerator().generateReadOnly("Constancia", List.of("constancia ".repeat(60) + "FIN"));
        try (var pdf = Loader.loadPDF(bytes)) {
            var body = new PDFTextStripper().getText(pdf).lines().filter(line -> line.contains("constancia")).toList();
            assertTrue(body.size() > 1, "the text needs several lines");
            for (var line : body) {
                assertTrue(line.strip().matches("constancia( constancia)*( FIN)?"), "a word was split: " + line);
            }
        }
    }

    @Test void aWordLongerThanALineIsStillSplitWithoutLosingCharacters() throws Exception {
        var bytes = new PdfBoxGenerator().generateReadOnly("Constancia", List.of("x".repeat(200) + "FIN"));
        try (var pdf = Loader.loadPDF(bytes)) {
            var body = new PDFTextStripper().getText(pdf).lines().map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.equals("Constancia") && !line.startsWith("InmoNode"))
                    .toList();
            assertTrue(body.size() > 1, "the word needs several lines");
            assertEquals("x".repeat(200) + "FIN", String.join("", body));
        }
    }
}
