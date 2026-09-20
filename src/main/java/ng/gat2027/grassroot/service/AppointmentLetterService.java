package ng.gat2027.grassroot.service;

import ng.gat2027.grassroot.domain.Committee;
import ng.gat2027.grassroot.domain.Member;
import ng.gat2027.grassroot.domain.Position;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates a standalone appointment-letter PDF for a member holding an organisational Position.
 * This is a placeholder layout/wording until GAT supplies the official per-position letter format;
 * swap the body text in {@link #letterBody} once that's available.
 */
@Service
public class AppointmentLetterService {

    public byte[] generate(Member m, Position position, Committee committee) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font italic = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

            float margin = 60;
            float width = page.getMediaBox().getWidth() - 2 * margin;
            float y = page.getMediaBox().getHeight() - margin;

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                y = text(cs, bold, 16, margin, y, "GRASSROOT ADVOCACY FOR TINUBU (GAT) 2027");
                y = text(cs, italic, 10, margin, y - 4, "Motto: Forward Together with PBAT") - 26;
                y = text(cs, regular, 11, margin, y, "Date: " + LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy"))) - 6;
                y = text(cs, regular, 11, margin, y, "Ref: " + m.getMemberCode() + "/APPT") - 20;
                y = text(cs, bold, 13, margin, y, "LETTER OF APPOINTMENT") - 20;
                y = text(cs, regular, 11, margin, y, "Dear " + m.getFullName() + ",") - 18;

                for (String line : wrap(letterBody(m, position, committee), regular, 11, width)) {
                    y = text(cs, regular, 11, margin, y, line) - 4;
                }
                y -= 16;
                y = text(cs, regular, 11, margin, y, "Congratulations on this appointment.") - 40;
                y = text(cs, bold, 11, margin, y, "National Coordinator") - 14;
                text(cs, regular, 10, margin, y, "Grassroot Advocacy for Tinubu (GAT) 2027");
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private String letterBody(Member m, Position position, Committee committee) {
        return "On behalf of Grassroot Advocacy for Tinubu (GAT) 2027, I am pleased to formally notify you of your appointment as "
            + position.getTitle().trim() + " under the " + committee.getName() + " (" + committee.getCode() + "). This appointment "
            + "takes immediate effect and carries the responsibilities associated with the office. We look forward to your continued "
            + "dedication to the success of our mobilisation efforts across the country.";
    }

    private static float text(PDPageContentStream cs, PDType1Font font, float size, float x, float y, String value) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(value);
        cs.endText();
        return y - (size + 4);
    }

    private static List<String> wrap(String text, PDType1Font font, float size, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (font.getStringWidth(candidate) / 1000 * size > maxWidth && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }
}
