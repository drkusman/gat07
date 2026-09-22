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
 * Generates the official GAT 2027 appointment-letter PDF. Three page-one styles: the standard
 * committee-position letter (letterhead: appointment-letterhead.png, "Scope of Responsibilities"
 * bullets) for operational/committee positions; the ceremonial honorary letter (letterhead:
 * patron-letterhead.png) for Patrons, Grand Patrons, and Board of Trustees members - see
 * {@link OrganizationService#isHonoraryPosition(String)}; and the "Others" letter for admin-defined
 * ad hoc appointments with a numbered, admin-written Terms of Reference (see {@link #generateOther}),
 * which paginates automatically. Every letter ends with a second, plain "Acceptance of Appointment"
 * page for the appointee to sign and return.
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

    /** "Others" category: an ad hoc appointment outside the standing organisational structure, where the admin
     *  supplies both the position title and the free-text Terms of Reference printed on the letter. Modelled on
     *  GAT's own "Head of Department" letter format (National Executive Council decision, numbered Terms of
     *  Reference, "Yours faithfully" closing); paginates automatically since a full Terms of Reference list
     *  routinely runs to several pages. */
    public byte[] generateOther(Member m, String positionTitle, String termsOfReference, String stateName) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            String title = positionTitle.trim();
            otherPage(doc, m, title, termsOfReference, stateName);
            otherAcceptancePage(doc, m, title);

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

            cs.drawImage(signature, MARGIN, cy - 45, 30, 45);
            cy -= 55;
            cy = y(cs, bold, 11, MARGIN, cy, "Prof. Ochugudu Achoda Ipuele") - 14;
            cy = y(cs, regular, 10, MARGIN, cy, "National Coordinator") - 13;
            y(cs, bold, 10, MARGIN, cy, "07038960277, 08085653558");
        }
    }

    /** "Others" category letter, following GAT's "Head of Department" format: a National Executive Council
     *  decision, a numbered Terms of Reference list, and a "Yours faithfully" closing (no phone line). Uses the
     *  standard letterhead on every page and paginates automatically as the admin-supplied Terms of Reference
     *  can easily run past one page. */
    private void otherPage(PDDocument doc, Member m, String positionTitle, String termsOfReference, String stateName) throws IOException {
        PDImageXObject background = loadImage(doc, "letters/appointment-letterhead.png");
        PDImageXObject signature = loadImage(doc, "letters/coordinator-signature.png");
        PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        float width = PAGE_WIDTH - 2 * MARGIN;

        Flow f = new Flow(doc, background);
        f.newPage(630);
        try {
            y(f.cs, regular, 11, 448, 657, LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy")));

            f.cy = flowText(f, bold, 11, m.getFullName()) - 17;
            if (stateName != null && !stateName.isBlank()) f.cy = flowText(f, bold, 11, stateName) - 17;
            f.cy -= 10;

            f.cy = flowParagraph(f, bold, 11, width, 14,
                "APPOINTMENT AS " + positionTitle.toUpperCase() + ", GRASSROOTS ADVOCACY FOR TINUBU (GAT) 2027");
            f.cy -= 12;

            f.cy = flowMixedParagraph(f, regular, bold, 11, width, 14, List.of(
                new Run("I am pleased to formally convey to you the decision of the ", false),
                new Run("National Executive Council ", true),
                new Run("of Grassroots Advocacy for Tinubu (GAT) 2027 appointing you as " + positionTitle
                    + " of the Organisation, effective from the date of this letter.", false)
            ));
            f.cy -= 6;
            f.cy = flowParagraph(f, regular, 11, width, 14,
                "This appointment is in recognition of your competence, experience and capacity, and reflects our "
                + "confidence in your ability to discharge the responsibilities of this office effectively in support "
                + "of GAT's mission, structures and programmes.");
            f.cy -= 6;
            f.cy = flowParagraph(f, regular, 11, width, 14,
                "In this capacity, you shall report to the National Coordinator, GAT 2027, and work closely with the "
                + "relevant units and structures of the Organisation as required in the discharge of your responsibilities.");
            f.cy -= 10;

            f.cy = flowText(f, bold, 11, "TERMS OF REFERENCE") - 14;
            f.cy = flowParagraph(f, regular, 11, width, 14, "Your responsibilities shall include, but not be limited to, the following:");
            f.cy -= 4;
            int n = 1;
            if (termsOfReference != null && !termsOfReference.isBlank()) {
                for (String item : termsOfReference.split("\\r?\\n+")) {
                    if (item.isBlank()) continue;
                    f.cy = flowNumberedItem(f, regular, n++, width, item.trim());
                }
            }
            f.cy -= 6;

            f.cy = flowParagraph(f, regular, 11, width, 14,
                "You will be expected to work collaboratively with all departments and structures of GAT and to "
                + "demonstrate professionalism, innovation, integrity, confidentiality and commitment in the discharge "
                + "of your responsibilities.");
            f.cy -= 6;
            f.cy = flowParagraph(f, regular, 11, width, 14,
                "We congratulate you on your appointment and look forward to your valuable contributions to the growth "
                + "and effectiveness of Grassroots Advocacy for Tinubu (GAT) 2027.");
            f.cy -= 6;
            f.cy = flowParagraph(f, regular, 11, width, 14, "Please accept the assurances of our highest regards.");
            f.cy -= 4;
            f.cy = flowText(f, bold, 11, "Yours faithfully,") - 4;

            f.ensureSpace(100);
            f.cs.drawImage(signature, MARGIN, f.cy - 45, 30, 45);
            f.cy -= 55;
            f.cy = flowText(f, bold, 11, "Prof. Ochugudu Achoda Ipuele,") - 14;
            f.cy = flowText(f, regular, 10, "National Coordinator") - 13;
            flowText(f, regular, 10, "Grassroots Advocacy for Tinubu (GAT) 2027");
        } finally {
            f.close();
        }
    }

    /** "Others"-category acceptance page, matching GAT's "Head of Department" acceptance wording, which differs
     *  from the standard {@link #acceptancePage}. */
    private void otherAcceptancePage(PDDocument doc, Member m, String positionTitle) throws IOException {
        PDPage page = new PDPage(new PDRectangle(PAGE_WIDTH, PAGE_HEIGHT));
        doc.addPage(page);
        PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            float width = PAGE_WIDTH - 2 * MARGIN;
            float cy = 708;
            cy = y(cs, bold, 12, MARGIN, cy, "ACCEPTANCE OF APPOINTMENT") - 24;
            cy = mixedParagraph(cs, regular, bold, 11, MARGIN, cy, width, 17, List.of(
                new Run("I, ", false),
                new Run(m.getFullName() + ", ", true),
                new Run("hereby accept my appointment as ", false),
                new Run(positionTitle + ", Grassroots Advocacy for Tinubu (GAT) 2027, ", true),
                new Run("and undertake to discharge the responsibilities attached to the position diligently, "
                    + "professionally and in accordance with the objectives and directives of the Organization.", false)
            ));
            cy -= 22;
            cy = y(cs, bold, 12, MARGIN, cy, "Signature: ________________________") - 29;
            y(cs, bold, 12, MARGIN, cy, "Date: ________________________");
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

            cs.drawImage(signature, MARGIN, cy - 48, 30, 45);
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
        float spaceWidth = regular.getStringWidth(" ") / 1000 * size;
        float cy = startY;
        for (List<Object[]> ln : buildMixedLines(regular, bold, size, maxWidth, runs)) {
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

    /** Splits mixed bold/regular runs into word-wrapped lines (shared by {@link #mixedParagraph} and the
     *  pagination-aware {@link #flowMixedParagraph}); each word is tagged [text, isBold]. */
    private static List<List<Object[]>> buildMixedLines(PDType1Font regular, PDType1Font bold, float size, float maxWidth, List<Run> runs) throws IOException {
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
        return lines;
    }

    /** Tracks the current page/cursor for a paginated letter, drawing the same letterhead on every page and
     *  starting a fresh page automatically ({@link #ensureSpace}) whenever content would run past the margin. */
    private static final class Flow {
        private final PDDocument doc;
        private final PDImageXObject background;
        private static final float CONTINUATION_TOP = 630f;
        private static final float BOTTOM_SAFE = MARGIN + 30f;
        PDPageContentStream cs;
        float cy;

        Flow(PDDocument doc, PDImageXObject background) { this.doc = doc; this.background = background; }

        void newPage(float startCy) throws IOException {
            if (cs != null) cs.close();
            PDPage page = new PDPage(new PDRectangle(PAGE_WIDTH, PAGE_HEIGHT));
            doc.addPage(page);
            cs = new PDPageContentStream(doc, page);
            cs.drawImage(background, 0, 0, PAGE_WIDTH, PAGE_HEIGHT);
            cy = startCy;
        }

        void ensureSpace(float needed) throws IOException {
            if (cy - needed < BOTTOM_SAFE) newPage(CONTINUATION_TOP);
        }

        void close() throws IOException { if (cs != null) cs.close(); }
    }

    private static float flowText(Flow f, PDType1Font font, float size, String text) throws IOException {
        f.ensureSpace(size);
        y(f.cs, font, size, MARGIN, f.cy, text);
        return f.cy;
    }

    private static float flowParagraph(Flow f, PDType1Font font, float size, float maxWidth, float lineHeight, String text) throws IOException {
        for (String line : wrap(text, font, size, maxWidth)) {
            f.ensureSpace(lineHeight);
            y(f.cs, font, size, MARGIN, f.cy, line);
            f.cy -= lineHeight;
        }
        return f.cy;
    }

    private static float flowMixedParagraph(Flow f, PDType1Font regular, PDType1Font bold, float size, float maxWidth, float lineHeight, List<Run> runs) throws IOException {
        float spaceWidth = regular.getStringWidth(" ") / 1000 * size;
        for (List<Object[]> ln : buildMixedLines(regular, bold, size, maxWidth, runs)) {
            f.ensureSpace(lineHeight);
            float cx = MARGIN;
            for (Object[] w : ln) {
                String text = (String) w[0];
                PDType1Font fnt = ((Boolean) w[1]) ? bold : regular;
                y(f.cs, fnt, size, cx, f.cy, text);
                cx += fnt.getStringWidth(text) / 1000 * size + spaceWidth;
            }
            f.cy -= lineHeight;
        }
        return f.cy;
    }

    /** A numbered Terms of Reference item ("12. Reporting: Submit regular reports..."), wrapped and indented
     *  under the number, pagination-aware. */
    private static float flowNumberedItem(Flow f, PDType1Font regular, int number, float maxWidth, String text) throws IOException {
        float indent = 20;
        List<String> lines = wrap(text, regular, 11, maxWidth - indent);
        for (int i = 0; i < lines.size(); i++) {
            f.ensureSpace(14);
            if (i == 0) y(f.cs, regular, 11, MARGIN + 2, f.cy, number + ".");
            y(f.cs, regular, 11, MARGIN + indent, f.cy, lines.get(i));
            f.cy -= 14;
        }
        return f.cy;
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
