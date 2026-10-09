package com.novacorp.inmonode.inmonodebackend.shared.infrastructure.documents;

import com.novacorp.inmonode.inmonodebackend.shared.application.documents.PdfGenerator;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.encryption.*;
import org.springframework.stereotype.Component;
import java.io.*;
import java.util.*;

@Component
public class PdfBoxGenerator implements PdfGenerator {
    private static final PDFont FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final int LINES_PER_PAGE = 45;

    @Override
    public byte[] generateReadOnly(String title, List<String> lines) {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            document.getDocumentInformation().setTitle(title);
            document.getDocumentInformation().setCreator("InmoNode");
            var wrapped = new ArrayList<String>();
            for (var line : lines) wrapped.addAll(wrap(line));
            if (wrapped.isEmpty()) wrapped.add("");
            int pageCount = (wrapped.size() + LINES_PER_PAGE - 1) / LINES_PER_PAGE;
            for (int pageNumber = 0; pageNumber < pageCount; pageNumber++) {
                var page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (var stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(FONT, 16);
                    stream.newLineAtOffset(45, 795);
                    stream.showText(printable(title));
                    stream.setFont(FONT, 10);
                    stream.setLeading(15);
                    stream.newLineAtOffset(0, -35);
                    int last = Math.min(wrapped.size(), (pageNumber + 1) * LINES_PER_PAGE);
                    for (int line = pageNumber * LINES_PER_PAGE; line < last; line++) {
                        stream.showText(wrapped.get(line));
                        stream.newLine();
                    }
                    stream.endText();
                    stream.beginText();
                    stream.setFont(FONT, 9);
                    stream.newLineAtOffset(45, 35);
                    stream.showText("InmoNode | " + (pageNumber + 1) + " / " + pageCount);
                    stream.endText();
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

    private static List<String> wrap(String value) throws IOException {
        var result = new ArrayList<String>();
        var line = new StringBuilder();
        for (char ch : printable(value).toCharArray()) {
            if (FONT.getStringWidth(line.toString() + ch) / 1000 * 10 > 505) {
                result.add(line.toString());
                line.setLength(0);
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
