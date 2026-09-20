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
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates the official GAT 2027 appointment-letter PDF: the letterhead/watermark/footer come from
 * the template image GAT supplied (src/main/resources/letters/appointment-letterhead.png, matching
 * that PDF's exact page size), with per-member text and the National Coordinator's signature drawn
 * on top. The five "Scope of Responsibilities" bullets are the standard set from that template;
 * a handful of positions are expected to need their own wording later (see {@link #bulletsFor}).
 */
@Service
public class AppointmentLetterService {
    private static final float PAGE_WIDTH = 595.32f;
    private static final float PAGE_HEIGHT = 841.92f;
    private static final float MARGIN = 60;
    private static final List<String> DEFAULT_BULLETS = List.of(
        "Policy Advocacy: Leading the efforts to communicate, explain, and advocate for the Federal Government's policies to the grassroots.",
        "Mobilisation: Overseeing the strategic mobilisation and sensitisation campaigns within your area of responsibility.",
        "Committee Participation: Attending all scheduled committee meetings, contributing constructively to strategic planning, and participating in sub-committees as required.",
        "Reporting: Providing regular updates and reports to the National Coordinator on the progress and challenges encountered in the execution of your mandate.",
        "Digital Communication: Managing the association's presence across all digital and social media platforms, crafting positive narratives, and ensuring timely communication of GAT 2027 activities and positions."
    );

    public byte[] generate(Member m, Position position, Committee committee, String stateName) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(PAGE_WIDTH, PAGE_HEIGHT));
            doc.addPage(page);
            PDImageXObject background = loadImage(doc, "letters/appointment-letterhead.png");
            PDImageXObject signature = loadImage(doc, "letters/coordinator-signature.png");
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(background, 0, 0, PAGE_WIDTH, PAGE_HEIGHT);

                // Date, on the template's existing "Date:....." line.
                y(cs, regular, 11, 448, 657, LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy")));

                float width = PAGE_WIDTH - 2 * MARGIN;
                float cy = 630;
                String positionTitle = position.getTitle().trim();
                String committeeLine = committee.getName() + " (" + committee.getCode() + ")";

                cy = paragraph(cs, bold, 11, MARGIN, cy, width, 14,
                    "SUBJECT: LETTER OF APPOINTMENT AS " + positionTitle.toUpperCase() + " IN THE " + committeeLine.toUpperCase());
                cy -= 12;
                cy = y(cs, bold, 11, MARGIN, cy, "Dear " + m.getFullName() + ",") - 14;
                if (stateName != null && !stateName.isBlank()) cy = y(cs, bold, 11, MARGIN, cy, stateName) - 14;
                else cy -= 4;

                cy = paragraph(cs, regular, 11, MARGIN, cy, width, 14,
                    "The National Leadership of the Grassroots Advocacy for Tinubu (GAT) 2027 is pleased to formally offer you "
                    + "an appointment to the position of " + positionTitle + " in the " + committeeLine + ".");
                cy -= 6;
                cy = paragraph(cs, regular, 11, MARGIN, cy, width, 14,
                    "This appointment is effective immediately and is a reflection of your demonstrated commitment, unwavering "
                    + "loyalty to the ideals of the All Progressives Congress (APC), and your proactive dedication to the Renewed "
                    + "Hope Agenda of His Excellency, President Bola Ahmed Tinubu.");
                cy -= 10;

                cy = y(cs, bold, 11, MARGIN, cy, "1. Scope of Responsibilities") - 14;
                cy = paragraph(cs, regular, 11, MARGIN, cy, width, 14,
                    "As a member of the " + committee.getName() + ", your duties and responsibilities shall include, but not be "
                    + "limited to, the following:");
                cy -= 4;
                for (String bullet : bulletsFor(position)) {
                    cy = bulletPoint(cs, regular, bold, 11, MARGIN, cy, width, bullet);
                }
                cy -= 8;

                cy = paragraph(cs, regular, 11, MARGIN, cy, width, 14,
                    "Please signify your acceptance of this appointment by signing the space provided below and returning a copy "
                    + "to the National Secretariat within seven (7) days of receipt.");
                cy -= 6;
                cy = y(cs, regular, 11, MARGIN, cy, "Congratulations on your appointment. We look forward to working with you.") - 16;
                cy = y(cs, regular, 11, MARGIN, cy, "Sincerely,") - 4;

                cs.drawImage(signature, MARGIN, cy - 45, 70, 45);
                cy -= 55;
                cy = y(cs, bold, 11, MARGIN, cy, "Prof. Ochugudu Achoda Ipuele") - 14;
                cy = y(cs, regular, 10, MARGIN, cy, "National Coordinator") - 13;
                y(cs, bold, 10, MARGIN, cy, "07038960277, 08085653558");
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    /** Standard responsibilities for most positions; override here once GAT supplies position-specific wording. */
    private List<String> bulletsFor(Position position) {
        return DEFAULT_BULLETS;
    }

    private static PDImageXObject loadImage(PDDocument doc, String classpath) throws IOException {
        try (InputStream in = new ClassPathResource(classpath).getInputStream()) {
            return PDImageXObject.createFromByteArray(doc, in.readAllBytes(), classpath);
        }
    }

    private static float y(PDPageContentStream cs, PDType1Font font, float size, float x, float y, String value) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(value);
        cs.endText();
        return y;
    }

    private static float paragraph(PDPageContentStream cs, PDType1Font font, float size, float x, float startY, float maxWidth, float lineHeight, String text) throws IOException {
        float cy = startY;
        for (String line : wrap(text, font, size, maxWidth)) {
            y(cs, font, size, x, cy, line);
            cy -= lineHeight;
        }
        return cy;
    }

    private static float bulletPoint(PDPageContentStream cs, PDType1Font regular, PDType1Font bold, float size, float x, float startY, float maxWidth, String text) throws IOException {
        float indent = 14;
        float cy = startY;
        y(cs, regular, size, x, cy, "•");
        String label = null, rest = text;
        int colon = text.indexOf(':');
        if (colon > 0 && colon < 40) { label = text.substring(0, colon + 1); rest = text.substring(colon + 1).trim(); }
        List<String> lines = wrap(label != null ? label + " " + rest : rest, regular, size, maxWidth - indent);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (i == 0 && label != null && line.startsWith(label)) {
                y(cs, bold, size, x + indent, cy, label);
                float labelWidth = bold.getStringWidth(label) / 1000 * size;
                y(cs, regular, size, x + indent + labelWidth + 3, cy, line.substring(label.length()).trim());
            } else {
                y(cs, regular, size, x + indent, cy, line);
            }
            cy -= 14;
        }
        return cy;
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
