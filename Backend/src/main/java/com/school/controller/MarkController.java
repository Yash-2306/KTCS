package com.school.controller;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.school.model.Mark;
import com.school.model.User;
import com.school.repository.MarkRepository;
import com.school.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/marks")
@CrossOrigin(origins = "*")
public class MarkController {

    private final MarkRepository markRepository;
    private final UserRepository userRepository;

    @Autowired
    public MarkController(MarkRepository markRepository, UserRepository userRepository) {
        this.markRepository = markRepository;
        this.userRepository = userRepository;
    }

    // ── 1. Submit marks ───────────────────────────────────────────────
    @PostMapping("/submit")
    public ResponseEntity<?> submitMarks(@RequestBody List<Mark> marksList) {
        List<Mark> savedMarks = new ArrayList<>();
        for (Mark newMark : marksList) {
            List<Mark> existingMarks = markRepository.findByStudentId(newMark.getStudentId());
            Optional<Mark> existing = existingMarks.stream()
                    .filter(m -> m.getSubject().equalsIgnoreCase(newMark.getSubject())
                            && m.getExamType().equalsIgnoreCase(newMark.getExamType()))
                    .findFirst();
            Mark mark;
            if (existing.isPresent()) {
                mark = existing.get();
                mark.setMarksObtained(newMark.getMarksObtained());
                mark.setMaxMarks(newMark.getMaxMarks());
            } else {
                mark = newMark;
            }
            savedMarks.add(markRepository.save(mark));
        }
        return ResponseEntity.ok(savedMarks);
    }

    // ── 2. JSON Report Card ───────────────────────────────────────────
    @GetMapping("/student/{studentId}/report-card")
    public ResponseEntity<?> generateReportCard(
            @PathVariable Long studentId,
            @RequestParam(required = false) String examType) {

        Optional<User> studentOpt = userRepository.findById(studentId);
        if (studentOpt.isEmpty() || !studentOpt.get().getRole().equalsIgnoreCase("STUDENT"))
            return ResponseEntity.badRequest().body("Student profile not found!");

        User student = studentOpt.get();
        List<Mark> marks = (examType != null && !examType.isEmpty())
                ? markRepository.findByStudentIdAndExamType(studentId, examType.toUpperCase())
                : markRepository.findByStudentId(studentId);

        double totalObtained = marks.stream().mapToDouble(Mark::getMarksObtained).sum();
        double totalMax = marks.stream().mapToDouble(Mark::getMaxMarks).sum();
        double percentage = totalMax > 0 ? (totalObtained / totalMax) * 100 : 0;
        String grade = getGrade(percentage);
        String status = percentage >= 40.0 ? "PASS" : "FAIL";

        Map<String, Object> reportCard = new LinkedHashMap<>();
        reportCard.put("studentName", student.getFullName());
        reportCard.put("className", student.getClassName());
        reportCard.put("section", student.getSection());
        reportCard.put("username", student.getUsername());
        reportCard.put("examType", examType != null ? examType.toUpperCase() : "ALL EXAMS");
        reportCard.put("subjects", marks);
        reportCard.put("totalObtained", totalObtained);
        reportCard.put("totalMax", totalMax);
        reportCard.put("percentage", Math.round(percentage * 100.0) / 100.0);
        reportCard.put("grade", grade);
        reportCard.put("status", status);
        return ResponseEntity.ok(reportCard);
    }

    // ── 3. PDF Report Card ────────────────────────────────────────────
    /**
     * Generates a downloadable PDF report card using OpenPDF.
     * Interview: "We used OpenPDF to generate report cards server-side.
     * The PDF is returned as a byte stream with Content-Disposition: attachment
     * so the browser prompts a download automatically."
     *
     * GET /api/marks/student/{studentId}/report-card/pdf?examType=MIDTERM
     */
    @GetMapping("/student/{studentId}/report-card/pdf")
    public ResponseEntity<byte[]> generatePdf(
            @PathVariable Long studentId,
            @RequestParam(required = false) String examType) {

        Optional<User> studentOpt = userRepository.findById(studentId);
        if (studentOpt.isEmpty() || !studentOpt.get().getRole().equalsIgnoreCase("STUDENT"))
            return ResponseEntity.notFound().build();

        User student = studentOpt.get();
        List<Mark> marks = (examType != null && !examType.isEmpty())
                ? markRepository.findByStudentIdAndExamType(studentId, examType.toUpperCase())
                : markRepository.findByStudentId(studentId);

        double totalObtained = marks.stream().mapToDouble(Mark::getMarksObtained).sum();
        double totalMax = marks.stream().mapToDouble(Mark::getMaxMarks).sum();
        double percentage = totalMax > 0 ? (totalObtained / totalMax) * 100 : 0;
        String grade = getGrade(percentage);
        String status = percentage >= 40.0 ? "PASS" : "FAIL";

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 40, 40, 60, 40);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            // ── Fonts ─────────────────────────────────────────────────
            Font titleFont   = new Font(Font.HELVETICA, 20, Font.BOLD, new Color(30, 58, 95));
            Font schoolFont  = new Font(Font.HELVETICA, 11, Font.NORMAL, new Color(80, 80, 80));
            Font headerFont  = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
            Font cellFont    = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(55, 65, 81));
            Font labelFont   = new Font(Font.HELVETICA, 9,  Font.BOLD, new Color(100, 116, 139));
            Font valueFont   = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(30, 30, 30));
            Font summaryFont = new Font(Font.HELVETICA, 11, Font.BOLD, new Color(30, 58, 95));

            // ── Header ────────────────────────────────────────────────
            Paragraph school = new Paragraph("Kriti – The Concept School", titleFont);
            school.setAlignment(Element.ALIGN_CENTER);
            doc.add(school);

            Paragraph address = new Paragraph("5-31-303, Saibaba Nagar, Suraram, Hyderabad – 500055\nPhone: +91 99497 89922  |  Email: ktcs.school@gmail.com", schoolFont);
            address.setAlignment(Element.ALIGN_CENTER);
            address.setSpacingAfter(6);
            doc.add(address);

            // Divider
            PdfPTable divider = new PdfPTable(1);
            divider.setWidthPercentage(100);
            PdfPCell divCell = new PdfPCell();
            divCell.setBackgroundColor(new Color(30, 58, 95));
            divCell.setFixedHeight(3);
            divCell.setBorder(Rectangle.NO_BORDER);
            divider.addCell(divCell);
            doc.add(divider);
            doc.add(Chunk.NEWLINE);

            // ── Title ─────────────────────────────────────────────────
            Font rcTitleFont = new Font(Font.HELVETICA, 14, Font.BOLD, new Color(30, 58, 95));
            Paragraph rcTitle = new Paragraph(
                    "REPORT CARD — " + (examType != null ? examType.toUpperCase() : "ALL EXAMS"), rcTitleFont);
            rcTitle.setAlignment(Element.ALIGN_CENTER);
            rcTitle.setSpacingAfter(12);
            doc.add(rcTitle);

            // ── Student Info ──────────────────────────────────────────
            PdfPTable infoTable = new PdfPTable(4);
            infoTable.setWidthPercentage(100);
            infoTable.setSpacingAfter(16);
            addInfoCell(infoTable, "Student Name", student.getFullName() != null ? student.getFullName() : "—", labelFont, valueFont);
            addInfoCell(infoTable, "Username",     student.getUsername(), labelFont, valueFont);
            addInfoCell(infoTable, "Class",        (student.getClassName() != null ? student.getClassName() : "—")
                    + " – " + (student.getSection() != null ? student.getSection() : "—"), labelFont, valueFont);
            addInfoCell(infoTable, "Date",         LocalDate.now().toString(), labelFont, valueFont);
            doc.add(infoTable);

            // ── Marks Table ───────────────────────────────────────────
            PdfPTable marksTable = new PdfPTable(5);
            marksTable.setWidthPercentage(100);
            marksTable.setWidths(new float[]{3f, 1.5f, 1.5f, 1.5f, 1.5f});
            marksTable.setSpacingAfter(16);

            String[] headers = {"Subject", "Marks Obtained", "Max Marks", "Percentage", "Grade"};
            for (String h : headers) {
                PdfPCell hCell = new PdfPCell(new Phrase(h, headerFont));
                hCell.setBackgroundColor(new Color(30, 58, 95));
                hCell.setPadding(8);
                hCell.setBorderColor(new Color(30, 58, 95));
                hCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                marksTable.addCell(hCell);
            }

            for (Mark m : marks) {
                double pct = m.getMaxMarks() > 0 ? (m.getMarksObtained() / m.getMaxMarks()) * 100 : 0;
                String g = getGrade(pct);
                Color rowBg = marks.indexOf(m) % 2 == 0 ? Color.WHITE : new Color(248, 250, 252);

                addMarkCell(marksTable, m.getSubject(), cellFont, rowBg, Element.ALIGN_LEFT);
                addMarkCell(marksTable, String.valueOf(m.getMarksObtained().intValue()), cellFont, rowBg, Element.ALIGN_CENTER);
                addMarkCell(marksTable, String.valueOf(m.getMaxMarks().intValue()), cellFont, rowBg, Element.ALIGN_CENTER);
                addMarkCell(marksTable, String.format("%.1f%%", pct), cellFont, rowBg, Element.ALIGN_CENTER);
                addMarkCell(marksTable, g, cellFont, rowBg, Element.ALIGN_CENTER);
            }
            doc.add(marksTable);

            // ── Summary ───────────────────────────────────────────────
            PdfPTable summary = new PdfPTable(4);
            summary.setWidthPercentage(60);
            summary.setHorizontalAlignment(Element.ALIGN_RIGHT);
            Color summaryBg = new Color(241, 245, 249);
            addSummaryRow(summary, "Total Marks",  (int)totalObtained + " / " + (int)totalMax, summaryFont, summaryBg);
            addSummaryRow(summary, "Percentage",   String.format("%.2f%%", percentage),       summaryFont, summaryBg);
            addSummaryRow(summary, "Grade",        grade,                                      summaryFont, summaryBg);
            Color statusColor = "PASS".equals(status) ? new Color(240, 253, 244) : new Color(254, 226, 226);
            Font statusFont = new Font(Font.HELVETICA, 11, Font.BOLD,
                    "PASS".equals(status) ? new Color(21, 128, 61) : new Color(185, 28, 28));
            addSummaryRow(summary, "Result", status, statusFont, statusColor);
            doc.add(summary);

            // ── Footer ────────────────────────────────────────────────
            doc.add(Chunk.NEWLINE);
            Font footerFont = new Font(Font.HELVETICA, 8, Font.ITALIC, new Color(150, 150, 150));
            Paragraph footer = new Paragraph("This is a computer-generated report card. — Kriti The Concept School, " + LocalDate.now().getYear(), footerFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            doc.add(footer);

            doc.close();

            String filename = "ReportCard_" + student.getUsername() + "_" +
                    (examType != null ? examType : "ALL") + ".pdf";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(baos.toByteArray());

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private String getGrade(double pct) {
        if (pct >= 90) return "A+";
        if (pct >= 80) return "A";
        if (pct >= 70) return "B";
        if (pct >= 60) return "C";
        if (pct >= 50) return "D";
        return "F";
    }

    private void addInfoCell(PdfPTable t, String label, String value, Font lf, Font vf) {
        PdfPCell c = new PdfPCell();
        c.addElement(new Phrase(label, lf));
        c.addElement(new Phrase(value, vf));
        c.setBorderColor(new Color(226, 232, 240));
        c.setPadding(8);
        t.addCell(c);
    }

    private void addMarkCell(PdfPTable t, String text, Font f, Color bg, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setBackgroundColor(bg);
        c.setPadding(7);
        c.setHorizontalAlignment(align);
        c.setBorderColor(new Color(226, 232, 240));
        t.addCell(c);
    }

    private void addSummaryRow(PdfPTable t, String label, String value, Font vf, Color bg) {
        Font lf = new Font(Font.HELVETICA, 10, Font.BOLD, new Color(100, 116, 139));
        PdfPCell lc = new PdfPCell(new Phrase(label, lf));
        lc.setBackgroundColor(bg); lc.setPadding(7); lc.setBorderColor(new Color(226, 232, 240));
        PdfPCell vc = new PdfPCell(new Phrase(value, vf));
        vc.setBackgroundColor(bg); vc.setPadding(7); vc.setBorderColor(new Color(226, 232, 240));
        t.addCell(lc); t.addCell(vc);
    }
}
