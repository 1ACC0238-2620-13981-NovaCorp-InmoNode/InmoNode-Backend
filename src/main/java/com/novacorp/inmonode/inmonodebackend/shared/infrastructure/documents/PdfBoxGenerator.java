package com.novacorp.inmonode.inmonodebackend.shared.infrastructure.documents;

import com.novacorp.inmonode.inmonodebackend.shared.application.documents.PdfGenerator;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.encryption.*;
import org.springframework.stereotype.Component;
import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

@Component
public class PdfBoxGenerator implements PdfGenerator {
    private static final PDFont FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final int LINES_PER_PAGE = 45;
    private static final float LEFT = 45;
    private static final float WIDTH = 505;
    private static final float FIRST_BASELINE = 760;
    private static final float LEADING = 15;
    private static final float FONT_SIZE = 10;
    private static final float CELL_PADDING = 6;
    private static final float HEADER_GRAY = 0.86f;
    private static final float STRIPE_GRAY = 0.95f;
    /** Amounts, counts and rates: "12", "897.55", "40,001.93", "S/ 45,000.00", "12.5 %". */
    private static final Pattern NUMERIC = Pattern.compile("[-+]?(S/|US\\$)?\\s?[\\d.,]+\\s?%?");

    @Override
    public byte[] generateReadOnly(String title, List<String> lines) {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            document.getDocumentInformation().setTitle(title);
            document.getDocumentInformation().setCreator("InmoNode");
            var pages = paginate(flow(lines));
            for (int pageNumber = 0; pageNumber < pages.size(); pageNumber++) {
                var page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (var stream = new PDPageContentStream(document, page)) {
                    write(stream, FONT, 16, LEFT, 795, printable(title));
                    var rows = pages.get(pageNumber);
                    for (int slot = 0; slot < rows.size(); slot++) {
                        float baseline = FIRST_BASELINE - slot * LEADING;
                        switch (rows.get(slot)) {
                            case Text text -> write(stream, FONT, FONT_SIZE, LEFT, baseline, text.value());
                            case Cells cells -> drawRow(stream, cells, baseline);
                        }
                    }
                    write(stream, FONT, 9, LEFT, 35, "InmoNode | " + (pageNumber + 1) + " / " + pages.size());
                }
            }
            var permissions = new AccessPermission();
            permissions.setCanModify(false);
            permissions.setCanModifyAnnotations(false);
            permissions.setCanFillInForm(false);
            permissions.setCanAssembleDocument(false);
            var protection = new StandardProtectionPolicy(UUID.randomUUID().toString(), "", permissions);
            protection.setEncryptionKeyLength(256);
            document.protect(protection);
            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not generate the PDF document", e);
        }
    }

    /** One line of a page: wrapped text, or a row of a table. */
    private sealed interface Line permits Text, Cells {
    }

    private record Text(String value) implements Line {
    }

    /** Row {@code row} of {@code table}; row 0 is its header. */
    private record Cells(Table table, int row) implements Line {
    }

    /**
     * Consecutive lines with tabs: each tab separates two cells, and the first line is the header. Columns share the
     * page width in proportion to their longest cell; a column whose every cell is a number is aligned to the right.
     */
    private static final class Table {
        private final List<List<String>> rows = new ArrayList<>();
        private float[] widths;
        private boolean[] numeric;

        void measure() throws IOException {
            int columns = rows.stream().mapToInt(List::size).max().orElse(0);
            widths = new float[columns];
            numeric = new boolean[columns];
            float total = 0;
            for (int column = 0; column < columns; column++) {
                float widest = 0;
                boolean allNumbers = rows.size() > 1;
                for (int row = 0; row < rows.size(); row++) {
                    var cell = cell(row, column);
                    widest = Math.max(widest, width(row == 0 ? BOLD : FONT, cell));
                    if (row > 0 && !cell.isEmpty() && !NUMERIC.matcher(cell).matches()) allNumbers = false;
                }
                widths[column] = widest + 2 * CELL_PADDING;
                numeric[column] = allNumbers;
                total += widths[column];
            }
            for (int column = 0; column < columns; column++) widths[column] *= WIDTH / total;
        }

        String cell(int row, int column) {
            var cells = rows.get(row);
            return column < cells.size() ? cells.get(column) : "";
        }
    }

    /** Text lines are wrapped to the page width; lines with tabs become table rows. */
    private static List<Line> flow(List<String> lines) throws IOException {
        var flow = new ArrayList<Line>();
        Table table = null;
        for (var line : lines) {
            if (line.indexOf('\t') >= 0) {
                if (table == null) {
                    table = new Table();
                    flow.add(new Cells(table, 0));
                } else {
                    flow.add(new Cells(table, table.rows.size()));
                }
                table.rows.add(Arrays.stream(line.split("\t", -1)).map(cell -> printable(cell).strip()).toList());
                continue;
            }
            if (table != null) table.measure();
            table = null;
            for (var wrapped : wrap(line)) flow.add(new Text(wrapped));
        }
        if (table != null) table.measure();
        return flow;
    }

    /** Fills pages of 45 lines. A table that goes on to another page repeats its header there. */
    private static List<List<Line>> paginate(List<Line> flow) {
        var pages = new ArrayList<List<Line>>();
        var page = new ArrayList<Line>();
        for (var line : flow) {
            boolean orphanHeader = line instanceof Cells cells && cells.row() == 0 && cells.table().rows.size() > 1
                    && page.size() == LINES_PER_PAGE - 1;
            if (page.size() == LINES_PER_PAGE || orphanHeader) {
                pages.add(page);
                page = new ArrayList<>();
                if (line instanceof Cells cells && cells.row() > 0) page.add(new Cells(cells.table(), 0));
            }
            page.add(line);
        }
        if (!page.isEmpty() || pages.isEmpty()) pages.add(page);
        return pages;
    }

    private static void drawRow(PDPageContentStream stream, Cells cells, float baseline) throws IOException {
        var table = cells.table();
        boolean header = cells.row() == 0;
        if (header || cells.row() % 2 == 0) {
            stream.setNonStrokingColor(header ? HEADER_GRAY : STRIPE_GRAY);
            stream.addRect(LEFT, baseline - 4, WIDTH, LEADING);
            stream.fill();
            stream.setNonStrokingColor(0f);
        }
        var font = header ? BOLD : FONT;
        float x = LEFT;
        for (int column = 0; column < table.widths.length; column++) {
            var cell = table.cell(cells.row(), column);
            if (!cell.isEmpty()) {
                float left = table.numeric[column]
                        ? x + table.widths[column] - CELL_PADDING - width(font, cell)
                        : x + CELL_PADDING;
                write(stream, font, FONT_SIZE, left, baseline, cell);
            }
            x += table.widths[column];
        }
    }

    private static void write(PDPageContentStream stream, PDFont font, float size, float x, float y, String text)
            throws IOException {
        stream.beginText();
        stream.setFont(font, size);
        stream.newLineAtOffset(x, y);
        stream.showText(text);
        stream.endText();
    }

    private static float width(PDFont font, String text) throws IOException {
        return font.getStringWidth(text) / 1000 * FONT_SIZE;
    }

    /** Breaks a line at its last space that fits; only a word longer than a whole line is split inside. */
    private static List<String> wrap(String value) throws IOException {
        var result = new ArrayList<String>();
        var line = new StringBuilder();
        for (char ch : printable(value).toCharArray()) {
            if (FONT.getStringWidth(line.toString() + ch) / 1000 * 10 > 505) {
                var lastSpace = line.lastIndexOf(" ");
                if (ch == ' ' || lastSpace <= 0) {
                    result.add(line.toString());
                    line.setLength(0);
                    if (ch == ' ') continue;
                } else {
                    result.add(line.substring(0, lastSpace));
                    line.delete(0, lastSpace + 1);
                }
            }
            line.append(ch);
        }
        result.add(line.toString());
        return result;
    }

    private static String printable(String value) {
        var result = new StringBuilder();
        for (char ch : value.toCharArray()) {
            if (Character.isISOControl(ch)) { result.append(' '); continue; }
            try { FONT.encode(String.valueOf(ch)); result.append(ch); }
            catch (IOException | IllegalArgumentException e) { result.append('?'); }
        }
        return result.toString();
    }
}
