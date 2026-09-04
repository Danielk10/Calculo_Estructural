package com.diamon.civil.terminal.export;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Dedicated PDF Report Generator for the Interactive Engineering Terminal Module.
 * Formats commands, engine execution logs (CalculiX, Gmsh, OpenCASCADE DRAWEXE),
 * benchmarks, and solver outputs into a professional engineering document.
 */
public class TerminalPDFReportGenerator {
    private static final String TAG = "TerminalPDFReport";

    // Standard A4 dimensions in PostScript points (72 points/inch)
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final float MARGIN_LEFT = 40f;
    private static final float MARGIN_RIGHT = 40f;
    private static final float MARGIN_TOP = 50f;
    private static final float MARGIN_BOTTOM = 50f;
    private static final float USABLE_WIDTH = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT; // 515f

    private final Paint titlePaint;
    private final Paint headerPaint;
    private final Paint subHeaderPaint;
    private final Paint bodyPaint;
    private final Paint promptPaint;
    private final Paint tablePaint;
    private final Paint tableHeaderPaint;
    private final Paint linePaint;
    private final Paint footerPaint;
    private final Paint cardBgPaint;

    private int pageNumber = 0;

    public TerminalPDFReportGenerator() {
        titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.parseColor("#0D1B2A"));
        titlePaint.setTextSize(18f);
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        headerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        headerPaint.setColor(Color.parseColor("#1A237E")); // Deep Navy Blue
        headerPaint.setTextSize(12.5f);
        headerPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        subHeaderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        subHeaderPaint.setColor(Color.parseColor("#283593")); // Indigo Blue
        subHeaderPaint.setTextSize(10f);
        subHeaderPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bodyPaint.setColor(Color.parseColor("#212529"));
        bodyPaint.setTextSize(8.5f);
        bodyPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));

        promptPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        promptPaint.setColor(Color.parseColor("#1A237E")); // Highlight user commands
        promptPaint.setTextSize(8.8f);
        promptPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));

        tablePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tablePaint.setColor(Color.parseColor("#212529"));
        tablePaint.setTextSize(8.5f);
        tablePaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));

        tableHeaderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tableHeaderPaint.setColor(Color.WHITE);
        tableHeaderPaint.setTextSize(8.5f);
        tableHeaderPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.parseColor("#B0BEC5"));
        linePaint.setStrokeWidth(0.5f);

        footerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        footerPaint.setColor(Color.parseColor("#757575"));
        footerPaint.setTextSize(8f);

        cardBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardBgPaint.setColor(Color.parseColor("#F5F7FA"));
    }

    public static class SessionStats {
        public int totalLines = 0;
        public int commandCount = 0;
        public int totalChars = 0;
    }

    public static SessionStats parseSessionStats(String logText) {
        SessionStats stats = new SessionStats();
        if (logText == null || logText.isEmpty()) return stats;
        stats.totalChars = logText.length();
        String[] lines = logText.split("\n");
        stats.totalLines = lines.length;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("$ ") || trimmed.startsWith("$")) {
                stats.commandCount++;
            }
        }
        return stats;
    }

    private static class PageContext {
        final PdfDocument document;
        PdfDocument.Page page;
        Canvas canvas;
        float y;

        PageContext(PdfDocument doc) {
            this.document = doc;
        }

        void newPage(TerminalPDFReportGenerator gen) {
            if (page != null) {
                gen.finishPage(document, canvas);
                document.finishPage(page);
            }
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, ++gen.pageNumber).create();
            page = document.startPage(pageInfo);
            canvas = page.getCanvas();
            y = MARGIN_TOP;
            gen.drawPageHeader(canvas);
        }

        void ensureSpace(TerminalPDFReportGenerator gen, float neededHeight) {
            if (page == null || y + neededHeight > PAGE_HEIGHT - MARGIN_BOTTOM - 20f) {
                newPage(gen);
            }
        }

        void finish(TerminalPDFReportGenerator gen) {
            if (page != null) {
                gen.finishPage(document, canvas);
                document.finishPage(page);
                page = null;
            }
        }
    }

    public boolean generateReport(Context context, File outputFile, String sessionTitle, String logText) {
        return generateReport(context, outputFile, sessionTitle, null, logText);
    }

    public boolean generateReport(Context context, File outputFile, String sessionTitle, File workDir, String logText) {
        PdfDocument document = new PdfDocument();
        pageNumber = 0;
        PageContext ctx = new PageContext(document);

        try {
            drawCoverPage(document, sessionTitle, workDir, logText);

            if (logText != null && !logText.trim().isEmpty()) {
                ctx.newPage(this);
                drawLogPages(ctx, logText);
            }

            ctx.finish(this);
            FileOutputStream fos = new FileOutputStream(outputFile);
            document.writeTo(fos);
            fos.close();
            document.close();

            Log.i(TAG, "Terminal PDF report generated successfully: " + outputFile.getAbsolutePath());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error generating Terminal PDF report: " + e.getMessage(), e);
            try {
                document.close();
            } catch (Throwable ignored) {}
            return false;
        }
    }

    private void drawPageHeader(Canvas canvas) {
        canvas.drawText("Structural Analysis FEA 3D | Interactive Engineering Terminal", MARGIN_LEFT, MARGIN_TOP - 16f, footerPaint);
        canvas.drawLine(MARGIN_LEFT, MARGIN_TOP - 10f, PAGE_WIDTH - MARGIN_RIGHT, MARGIN_TOP - 10f, linePaint);
    }

    private void finishPage(PdfDocument document, Canvas canvas) {
        String footer = String.format(Locale.US, "Structural Analysis FEA 3D | Terminal Execution Report | Page %d", pageNumber);
        canvas.drawText(footer, MARGIN_LEFT, PAGE_HEIGHT - 20f, footerPaint);

        String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
        float dateWidth = footerPaint.measureText(dateStr);
        canvas.drawText(dateStr, PAGE_WIDTH - MARGIN_RIGHT - dateWidth, PAGE_HEIGHT - 20f, footerPaint);

        canvas.drawLine(MARGIN_LEFT, PAGE_HEIGHT - 32f, PAGE_WIDTH - MARGIN_RIGHT, PAGE_HEIGHT - 32f, linePaint);
    }

    private void drawCoverPage(PdfDocument document, String sessionTitle, File workDir, String logText) {
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, ++pageNumber).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        float y = 110f;

        // Cover Title
        Paint bigTitle = new Paint(titlePaint);
        bigTitle.setTextSize(21f);
        String title = "TERMINAL EXECUTION & ANALYSIS REPORT";
        float titleWidth = bigTitle.measureText(title);
        canvas.drawText(title, (PAGE_WIDTH - titleWidth) / 2f, y, bigTitle);
        y += 8f;

        // Subtitle
        Paint subTitlePaint = new Paint(subHeaderPaint);
        subTitlePaint.setTextSize(10.5f);
        subTitlePaint.setColor(Color.parseColor("#455A64"));
        String subTitle = "Interactive CLI & Engineering Engines Execution Record";
        float subTitleWidth = subTitlePaint.measureText(subTitle);
        y += 14f;
        canvas.drawText(subTitle, (PAGE_WIDTH - subTitleWidth) / 2f, y, subTitlePaint);
        y += 8f;

        // Accent Line
        Paint accentLine = new Paint();
        accentLine.setColor(Color.parseColor("#1A237E"));
        accentLine.setStrokeWidth(2.5f);
        canvas.drawLine((PAGE_WIDTH - titleWidth) / 2f, y, (PAGE_WIDTH + titleWidth) / 2f, y, accentLine);
        y += 35f;

        // Metadata & Engine Specifications Table
        String workDirPath = workDir != null ? workDir.getAbsolutePath() : "/data/data/com.diamon.civil/files/terminal";
        String[][] info = {
                {"Session / Project:", sessionTitle != null ? sessionTitle : "Engineering Terminal Session"},
                {"Software:", "Structural Analysis FEA 3D"},
                {"Module:", "Interactive Engineering Terminal Console"},
                {"Execution Date:", new SimpleDateFormat("MMMM dd, yyyy HH:mm", Locale.US).format(new Date())},
                {"FEA Engine:", "CalculiX FEA (ccx 2.23 - Multi-Thread / OpenMP)"},
                {"3D Mesh Generator:", "Gmsh 3D Solid Mesh Generator (Gmsh 5.0.0)"},
                {"CAD Modeler Engine:", "OpenCASCADE Technology (OCCT 8.0.0.p1 / DRAWEXE)"},
                {"Native Architecture:", "Android NDK / ARM64-v8a (Bionic / POSIX)"},
                {"Working Directory:", workDirPath}
        };

        Paint labelPaint = new Paint(headerPaint);
        labelPaint.setTextSize(10f);
        Paint valuePaint = new Paint(bodyPaint);
        valuePaint.setTextSize(10f);
        valuePaint.setTypeface(Typeface.DEFAULT);

        for (String[] row : info) {
            canvas.drawText(row[0], MARGIN_LEFT + 30f, y, labelPaint);
            String val = row[1];
            float maxValWidth = USABLE_WIDTH - 190f;
            if (valuePaint.measureText(val) > maxValWidth) {
                while (val.length() > 3 && valuePaint.measureText(val + "…") > maxValWidth) {
                    val = val.substring(0, val.length() - 1);
                }
                val += "…";
            }
            canvas.drawText(val, MARGIN_LEFT + 180f, y, valuePaint);
            y += 20f;
        }

        y += 15f;
        canvas.drawLine(MARGIN_LEFT + 15f, y, PAGE_WIDTH - MARGIN_RIGHT - 15f, y, linePaint);
        y += 25f;

        // Session Statistics Card
        SessionStats stats = parseSessionStats(logText);
        float cardWidth = USABLE_WIDTH;
        float cardHeight = 55f;
        canvas.drawRoundRect(MARGIN_LEFT, y, MARGIN_LEFT + cardWidth, y + cardHeight, 6f, 6f, cardBgPaint);

        Paint cardBorder = new Paint(linePaint);
        cardBorder.setStyle(Paint.Style.STROKE);
        cardBorder.setStrokeWidth(0.8f);
        canvas.drawRoundRect(MARGIN_LEFT, y, MARGIN_LEFT + cardWidth, y + cardHeight, 6f, 6f, cardBorder);

        float statColWidth = cardWidth / 3f;
        String[][] statsData = {
                {"COMMANDS EXECUTED", String.valueOf(stats.commandCount)},
                {"TOTAL LOG LINES", String.valueOf(stats.totalLines)},
                {"LOG CHARACTERS", String.format(Locale.US, "%,d chars", stats.totalChars)}
        };

        for (int i = 0; i < statsData.length; i++) {
            float cx = MARGIN_LEFT + i * statColWidth + statColWidth / 2f;
            Paint statTitle = new Paint(subHeaderPaint);
            statTitle.setTextSize(7.5f);
            statTitle.setColor(Color.parseColor("#546E7A"));
            float stw = statTitle.measureText(statsData[i][0]);
            canvas.drawText(statsData[i][0], cx - stw / 2f, y + 20f, statTitle);

            Paint statVal = new Paint(titlePaint);
            statVal.setTextSize(14f);
            statVal.setColor(Color.parseColor("#1A237E"));
            float svw = statVal.measureText(statsData[i][1]);
            canvas.drawText(statsData[i][1], cx - svw / 2f, y + 42f, statVal);
        }

        y += cardHeight + 35f;

        // Notice & Scope
        Paint noticePaint = new Paint(bodyPaint);
        noticePaint.setTextSize(8.5f);
        noticePaint.setColor(Color.parseColor("#546E7A"));
        String[] notice = {
                "This technical document compiles the execution history, diagnostic benchmarks, native engine outputs,",
                "and engineering calculations performed directly within the interactive mobile terminal console.",
                "Computational solvers and mesh generators run locally on-device under the isolated sandbox environment."
        };

        for (String line : notice) {
            float lineWidth = noticePaint.measureText(line);
            canvas.drawText(line, (PAGE_WIDTH - lineWidth) / 2f, y, noticePaint);
            y += 14f;
        }

        finishPage(document, canvas);
        document.finishPage(page);
    }

    private void drawLogPages(PageContext ctx, String logText) {
        ctx.canvas.drawText("1. COMMAND EXECUTION & ENGINE OUTPUT LOG", MARGIN_LEFT, ctx.y, headerPaint);
        ctx.y += 16f;

        Paint descPaint = new Paint(bodyPaint);
        descPaint.setColor(Color.parseColor("#546E7A"));
        descPaint.setTextSize(8.5f);
        ctx.canvas.drawText("Monospace console transcript of interactive session commands and backend engine messages:", MARGIN_LEFT, ctx.y, descPaint);
        ctx.y += 10f;
        ctx.canvas.drawLine(MARGIN_LEFT, ctx.y, PAGE_WIDTH - MARGIN_RIGHT, ctx.y, linePaint);
        ctx.y += 16f;

        String[] lines = logText.split("\n");
        for (String line : lines) {
            String cleaned = line.replace("\t", "    ");
            String trimmed = cleaned.trim();

            if (trimmed.startsWith("$ ") || trimmed.startsWith("$")) {
                // Command line prompt: highlight in dark navy blue bold
                ctx.ensureSpace(this, 18f);
                ctx.y += 4f;
                ctx.y = drawWrappedText(ctx, cleaned, MARGIN_LEFT, USABLE_WIDTH, 12f, promptPaint);
            } else if (trimmed.startsWith("===") || trimmed.startsWith("---")) {
                // Section header
                ctx.ensureSpace(this, 16f);
                ctx.y += 3f;
                ctx.y = drawWrappedText(ctx, cleaned, MARGIN_LEFT, USABLE_WIDTH, 12f, subHeaderPaint);
            } else {
                // Standard engine output
                ctx.y = drawWrappedText(ctx, cleaned, MARGIN_LEFT, USABLE_WIDTH, 11f, bodyPaint);
            }
        }
    }

    private float drawWrappedText(PageContext ctx, String text, float x, float maxWidth, float lineHeight, Paint paint) {
        if (text == null || text.isEmpty()) {
            ctx.ensureSpace(this, lineHeight);
            return ctx.y + lineHeight;
        }

        // If line fits entirely
        if (paint.measureText(text) <= maxWidth) {
            ctx.ensureSpace(this, lineHeight + 2f);
            ctx.canvas.drawText(text, x, ctx.y, paint);
            return ctx.y + lineHeight;
        }

        // Otherwise wrap by words
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine.append(currentLine.length() == 0 ? "" : " ").append(word);
            } else {
                if (currentLine.length() > 0) {
                    ctx.ensureSpace(this, lineHeight + 2f);
                    ctx.canvas.drawText(currentLine.toString(), x, ctx.y, paint);
                    ctx.y += lineHeight;
                    currentLine = new StringBuilder(word);
                } else {
                    // Word is longer than max width: break it by characters
                    String longWord = word;
                    while (paint.measureText(longWord) > maxWidth && longWord.length() > 1) {
                        int breakIdx = 1;
                        while (breakIdx < longWord.length() && paint.measureText(longWord.substring(0, breakIdx + 1)) <= maxWidth) {
                            breakIdx++;
                        }
                        ctx.ensureSpace(this, lineHeight + 2f);
                        ctx.canvas.drawText(longWord.substring(0, breakIdx), x, ctx.y, paint);
                        ctx.y += lineHeight;
                        longWord = longWord.substring(breakIdx);
                    }
                    currentLine = new StringBuilder(longWord);
                }
            }
        }

        if (currentLine.length() > 0) {
            ctx.ensureSpace(this, lineHeight + 2f);
            ctx.canvas.drawText(currentLine.toString(), x, ctx.y, paint);
            ctx.y += lineHeight;
        }

        return ctx.y;
    }
}
