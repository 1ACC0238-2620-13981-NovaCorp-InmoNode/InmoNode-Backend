package com.novacorp.inmonode.inmonodebackend.shared.infrastructure.documents;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
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

    @Test void tabSeparatedLinesBecomeATableWithAlignedColumnsAndItsHeaderOnEveryPage() throws Exception {
        var lines = new ArrayList<String>();
        lines.add("Cronograma");
        lines.add("N°\tVencimiento\tCuota (S/)");
        for (int quota = 1; quota <= 60; quota++) {
            lines.add(quota + "\t12/%02d/2027\t%s".formatted(quota % 12 + 1, quota == 7 ? "1,000,000.00" : "1.00"));
        }
        lines.add("Total: S/ 1,000,059.00");
        var bytes = new PdfBoxGenerator().generateReadOnly("Cotización", lines);
        try (var pdf = Loader.loadPDF(bytes)) {
            var words = new WordPositions();
            words.getText(pdf);
            assertEquals(2, pdf.getNumberOfPages());
            assertEquals(2, words.starts("Vencimiento").size(), "the header is repeated on the second page");
            assertEquals(1, words.starts("Total:").size());
            assertEquals(5, words.starts("12/02/2027").size());
            assertEquals(1, rounded(words.ends("1.00"), words.ends("1,000,000.00")).size(), "amounts line up on the right");
            assertEquals(1, rounded(words.starts("12/02/2027")).size(), "dates line up on the left");
        }
    }

    @SafeVarargs
    private static Set<Float> rounded(List<Float>... positions) {
        var result = new HashSet<Float>();
        for (var list : positions) list.forEach(position -> result.add(Math.round(position * 10) / 10f));
        return result;
    }

    /** Where each word of the PDF starts and ends on its line. */
    private static final class WordPositions extends PDFTextStripper {
        private final Map<String, List<float[]>> words = new HashMap<>();

        @Override
        protected void writeString(String text, List<TextPosition> positions) {
            var word = new StringBuilder();
            float start = 0;
            float end = 0;
            for (var position : positions) {
                if (position.getUnicode().isBlank()) {
                    add(word, start, end);
                    word.setLength(0);
                    continue;
                }
                if (word.isEmpty()) start = position.getXDirAdj();
                word.append(position.getUnicode());
                end = position.getXDirAdj() + position.getWidthDirAdj();
            }
            add(word, start, end);
        }

        private void add(StringBuilder word, float start, float end) {
            if (!word.isEmpty()) words.computeIfAbsent(word.toString(), key -> new ArrayList<>()).add(new float[]{start, end});
        }

        List<Float> starts(String word) {
            return words.getOrDefault(word, List.of()).stream().map(position -> position[0]).toList();
        }

        List<Float> ends(String word) {
            return words.getOrDefault(word, List.of()).stream().map(position -> position[1]).toList();
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
