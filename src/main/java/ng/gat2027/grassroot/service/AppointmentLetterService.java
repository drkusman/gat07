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
 * Generates the official GAT 2027 appointment-letter PDF. Two page-one styles share one document:
 * the standard committee-position letter (letterhead: appointment-letterhead.png, "Scope of
 * Responsibilities" bullets) for operational/committee positions, and the ceremonial honorary letter
 * (letterhead: patron-letterhead.png) for Patrons, Grand Patrons, and Board of Trustees members - see
 * {@link OrganizationService#isHonoraryPosition(String)}. Every letter ends with a second, plain
 * "Acceptance of Appointment" page for the appointee to sign and return.
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
            String positionTitle = position.getTitle().trim();
            if (OrganizationService.isHonoraryPosition(positionTitle)) {
                honoraryPage(doc, m, positionTitle, stateName);
            } else {
                committeePage(doc, m, position, committee, stateName);
            }
            acceptancePage(doc, m, positionTitle);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    /** Standard committee-position letter: subject line, "Scope of Responsibilities" bullets. */
    private void committeePage(PDDocument doc, Member m, Position position, Committee committee, String stateName) throws IOException {
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
    }

    /** Ceremonial honorary letter for Patrons, Grand Patrons, and Board of Trustees members. */
    private void honoraryPage(PDDocument doc, Member m, String positionTitle, String stateName) throws IOException {
        PDPage page = new PDPage(new PDRectangle(PAGE_WIDTH, PAGE_HEIGHT));
        doc.addPage(page);
        PDImageXObject background = loadImage(doc, "letters/patron-letterhead.png");
        PDImageXObject signature = loadImage(doc, "letters/coordinator-signature.png");
        PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            cs.drawImage(background, 0, 0, PAGE_WIDTH, PAGE_HEIGHT);

            y(cs, regular, 11, 448, 657, LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy")));

            float width = PAGE_WIDTH - 2 * MARGIN;
            float cy = 633;
            cy = y(cs, bold, 12, MARGIN, cy, m.getFullName()) - 17;
            if (stateName != null && !stateName.isBlank()) cy = y(cs, bold, 12, MARGIN, cy, stateName) - 17;
            cy -= 8;

            cy = paragraph(cs, bold, 12, MARGIN, cy, width, 17,
                "LETTER OF APPOINTMENT AS " + positionTitle.toUpperCase() + ", GRASSROOTS ADVOCACY FOR TINUBU 2027");
            cy -= 9;

            cy = paragraph(cs, regular, 12, MARGIN, cy, width, 17,
                "On behalf of the leadership and members of Grassroots Advocacy for Tinubu 2027, I am pleased to formally "
                + "convey our profound recognition of your unwavering dedication, strategic leadership, and outstanding "
                + "contributions to the advancement of President Bola Ahmed Tinubu's political vision and national agenda.");
            cy -= 9;

            cy = paragraph(cs, regular, 12, MARGIN, cy, width, 17,
                "Your exemplary service and unwavering loyalty to the ideals of the All Progressives Congress (APC) have "
                + "continued to inspire millions across the federation. Your commitment to mobilising, uniting, and "
                + "energising supporters at all levels stands as a model of true patriotism and progressive leadership.");
            cy -= 9;

            cy = paragraph(cs, regular, 12, MARGIN, cy, width, 17,
                "In acknowledgment of these remarkable qualities, and in appreciation of your consistent support for "
                + "grassroots advocacy and democratic participation, Grassroots Advocacy for Tinubu 2027 hereby appoints "
                + "you as " + article(positionTitle) + " " + positionTitle + " of our organisation.");
            cy -= 9;

            cy = paragraph(cs, regular, 12, MARGIN, cy, width, 17,
                "We are confident that your guidance and presence will greatly strengthen our mission as we expand our "
                + "reach nationwide and work tirelessly toward ensuring overwhelming support for the continuation of the "
                + "Renewed Hope Agenda in 2027.");
            cy -= 9;

            cy = paragraph(cs, regular, 12, MARGIN, cy, width, 17,
                "We humbly request your acceptance of this appointment and look forward to benefiting from your "
                + "experience, wisdom, and mentorship.");
            cy -= 9;

            cy = y(cs, regular, 12, MARGIN, cy, "Please accept the assurances of our highest esteem.") - 25;
            cy = y(cs, bold, 12, MARGIN, cy, "Sincerely,") - 4;

            cs.drawImage(signature, MARGIN, cy - 48, 70, 45);
            cy -= 58;
            cy = y(cs, bold, 12, MARGIN, cy, "Prof. Ochugudu Achoda Ipuele") - 15;
            cy = y(cs, regular, 11, MARGIN, cy, "National Coordinator,") - 14;
            y(cs, bold, 11, MARGIN, cy, "07038960277, 08085653558");
        }
    }

    /** Plain, letterhead-free "Acceptance of Appointment" page appended to every appointment letter. */
    private void acceptancePage(PDDocument doc, Member m, String positionTitle) throws IOException {
        PDPage page = new PDPage(new PDRectangle(PAGE_WIDTH, PAGE_HEIGHT));
        doc.addPage(page);
        PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            float width = PAGE_WIDTH - 2 * MARGIN;
            float cy = 758;
            cy = y(cs, bold, 12, MARGIN, cy, "Acceptance of Appointment") - 24;
            cy = mixedParagraph(cs, regular, bold, 12, MARGIN, cy, width, 17, List.of(
                new Run("I, ", false),
                new Run(m.getFullName() + ", ", true),
                new Run("hereby accept the appointment as " + article(positionTitle) + " ", false),
                new Run(positionTitle.toUpperCase() + " ", true),
                new Run("of ", false),
                new Run("Grassroots Advocacy for Tinubu (GAT) 2027, ", true),
                new Run("and pledge to discharge my duties faithfully.", false)
            ));
            cy -= 22;
            cy = y(cs, bold, 12, MARGIN, cy, "Signature: ________________________") - 29;
            y(cs, bold, 12, MARGIN, cy, "Date: ________________________");
        }
    }

    /** Standard responsibilities for most positions; override here once GAT supplies position-specific wording. */
    private List<String> bulletsFor(Position position) {
        return DEFAULT_BULLETS;
    }

    private static String article(String word) {
        if (word == null || word.isBlank()) return "a";
        char c = Character.toLowerCase(word.trim().charAt(0));
        return (c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u') ? "an" : "a";
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

    /** One inline text run with its own bold/regular style, for {@link #mixedParagraph}. */
    private record Run(String text, boolean bold) {}

    /** Wraps and draws a paragraph made of mixed bold/regular runs (e.g. "I, <b>Name</b>, hereby accept..."),
     *  word-wrapping across the whole paragraph regardless of which run each word came from. */
    private static float mixedParagraph(PDPageContentStream cs, PDType1Font regular, PDType1Font bold, float size, float x, float startY, float maxWidth, float lineHeight, List<Run> runs) throws IOException {
        List<Object[]> words = new ArrayList<>();
        for (Run r : runs) {
            for (String word : r.text().split(" ")) {
                if (!word.isEmpty()) words.add(new Object[]{word, r.bold()});
            }
        }
        float spaceWidth = regular.getStringWidth(" ") / 1000 * size;
        List<List<Object[]>> lines = new ArrayList<>();
        List<Object[]> line = new ArrayList<>();
        float lineWidth = 0;
        for (Object[] w : words) {
            PDType1Font f = ((Boolean) w[1]) ? bold : regular;
            float wordWidth = f.getStringWidth((String) w[0]) / 1000 * size;
            float added = line.isEmpty() ? wordWidth : wordWidth + spaceWidth;
            if (lineWidth + added > maxWidth && !line.isEmpty()) {
                lines.add(line);
                line = new ArrayList<>();
                lineWidth = 0;
                added = wordWidth;
            }
            line.add(w);
            lineWidth += added;
        }
        if (!line.isEmpty()) lines.add(line);

        float cy = startY;
        for (List<Object[]> ln : lines) {
            float cx = x;
            for (Object[] w : ln) {
                String text = (String) w[0];
                PDType1Font f = ((Boolean) w[1]) ? bold : regular;
                y(cs, f, size, cx, cy, text);
                cx += f.getStringWidth(text) / 1000 * size + spaceWidth;
            }
            cy -= lineHeight;
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
