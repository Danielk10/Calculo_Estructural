package com.diamon.civil.util.export;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SolidPDFReportGenerator {
    private static final String TAG = "SolidPDFReportGenerator";

    // A4 dimensions in PostScript points (72 dpi)
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final float MARGIN_LEFT = 40f;
    private static final float MARGIN_RIGHT = 40f;
    private static final float MARGIN_TOP = 50f;
    private static final float MARGIN_BOTTOM = 50f;

    private final Paint titlePaint;
    private final Paint headerPaint;
    private final Paint bodyPaint;
    private final Paint linePaint;
    private final Paint footerPaint;

    private int pageNumber = 0;

    public SolidPDFReportGenerator() {
        titlePaint = new Paint();
        titlePaint.setColor(Color.BLACK);
        titlePaint.setTextSize(18f);
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        titlePaint.setAntiAlias(true);

        headerPaint = new Paint();
        headerPaint.setColor(Color.parseColor("#1A237E")); // Dark blue
        headerPaint.setTextSize(14f);
        headerPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        headerPaint.setAntiAlias(true);

        bodyPaint = new Paint();
        bodyPaint.setColor(Color.DKGRAY);
        bodyPaint.setTextSize(10f);
        bodyPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
        bodyPaint.setAntiAlias(true);

        linePaint = new Paint();
        linePaint.setColor(Color.parseColor("#9E9E9E"));
        linePaint.setStrokeWidth(0.5f);
        linePaint.setAntiAlias(true);

        footerPaint = new Paint();
        footerPaint.setColor(Color.GRAY);
        footerPaint.setTextSize(8f);
        footerPaint.setAntiAlias(true);
    }

    public boolean generateReport(Context context, File outputFile, String projectName, String logText) {
        PdfDocument document = new PdfDocument();
        pageNumber = 0;

        try {
            drawCoverPage(document, projectName);
            if (logText != null && !logText.isEmpty()) {
                drawLogPages(document, logText);
            }

            FileOutputStream fos = new FileOutputStream(outputFile);
            document.writeTo(fos);
            fos.close();
            document.close();

            Log.i(TAG, "Solid PDF report generated: " + outputFile.getAbsolutePath());
            return true;

        } catch (IOException e) {
            Log.e(TAG, "Error generating Solid PDF: " + e.getMessage());
            document.close();
            return false;
        }
    }

    private void finishPage(PdfDocument document, Canvas canvas) {
        String footer = String.format(Locale.US, "Structural FEA Suite | 3D Solid Analysis | Page %d", pageNumber);
        canvas.drawText(footer, MARGIN_LEFT, PAGE_HEIGHT - 20f, footerPaint);

        String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
        float dateWidth = footerPaint.measureText(dateStr);
        canvas.drawText(dateStr, PAGE_WIDTH - MARGIN_RIGHT - dateWidth, PAGE_HEIGHT - 20f, footerPaint);

        canvas.drawLine(MARGIN_LEFT, PAGE_HEIGHT - 35f, PAGE_WIDTH - MARGIN_RIGHT, PAGE_HEIGHT - 35f, linePaint);
    }

    private void drawCoverPage(PdfDocument document, String projectName) {
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, ++pageNumber).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        float y = 120f;

        Paint bigTitle = new Paint(titlePaint);
        bigTitle.setTextSize(22f);
        String title = "3D SOLID ANALYSIS REPORT";
        float titleWidth = bigTitle.measureText(title);
        canvas.drawText(title, (PAGE_WIDTH - titleWidth) / 2f, y, bigTitle);
        y += 8f;

        Paint accentLine = new Paint();
        accentLine.setColor(Color.parseColor("#1A237E"));
        accentLine.setStrokeWidth(2f);
        canvas.drawLine((PAGE_WIDTH - titleWidth) / 2f, y, (PAGE_WIDTH + titleWidth) / 2f, y, accentLine);
        y += 40f;

        String[][] info = {
                {"Project:", projectName != null ? projectName : "3D Solid Analysis"},
                {"Software:", "Structural FEA Suite"},
                {"Date:", new SimpleDateFormat("MMMM dd, yyyy", Locale.US).format(new Date())},
                {"Engine:", "CalculiX (ccx)"}
        };

        Paint labelPaint = new Paint(headerPaint);
        labelPaint.setTextSize(12f);
        Paint valuePaint = new Paint(bodyPaint);
        valuePaint.setTextSize(12f);
        valuePaint.setTypeface(Typeface.DEFAULT);

        for (String[] row : info) {
            canvas.drawText(row[0], MARGIN_LEFT + 80f, y, labelPaint);
            canvas.drawText(row[1], MARGIN_LEFT + 200f, y, valuePaint);
            y += 22f;
        }

        y += 30f;
        canvas.drawLine(MARGIN_LEFT + 40f, y, PAGE_WIDTH - MARGIN_RIGHT - 40f, y, linePaint);
        y += 30f;

        Paint noticePaint = new Paint(bodyPaint);
        noticePaint.setTextSize(9f);
        noticePaint.setColor(Color.GRAY);
        String[] notice = {
                "This report is generated for analysis and reference using CalculiX.",
                "The structural engineer is responsible for validating all results."
        };

        for (String line : notice) {
            float lineWidth = noticePaint.measureText(line);
            canvas.drawText(line, (PAGE_WIDTH - lineWidth) / 2f, y, noticePaint);
            y += 14f;
        }

        finishPage(document, canvas);
        document.finishPage(page);
    }

    private void drawLogPages(PdfDocument document, String logText) {
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, ++pageNumber).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        float y = MARGIN_TOP;

        canvas.drawText("ANALYSIS LOG", MARGIN_LEFT, y, headerPaint);
        y += 6f;
        canvas.drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y, linePaint);
        y += 20f;

        String[] lines = logText.split("\n");
        for (String line : lines) {
            line = line.replace("\t", "    ");
            
            while (line.length() > 0) {
                if (y > PAGE_HEIGHT - MARGIN_BOTTOM - 20f) {
                    finishPage(document, canvas);
                    document.finishPage(page);
                    pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, ++pageNumber).create();
                    page = document.startPage(pageInfo);
                    canvas = page.getCanvas();
                    y = MARGIN_TOP;
                }
                
                int breakIndex = line.length();
                while (breakIndex > 0 && bodyPaint.measureText(line.substring(0, breakIndex)) > (PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT)) {
                    breakIndex--;
                }
                if (breakIndex == 0) breakIndex = 1;
                
                String toDraw = line.substring(0, breakIndex);
                canvas.drawText(toDraw, MARGIN_LEFT, y, bodyPaint);
                y += 14f;
                line = line.substring(breakIndex);
            }
        }

        finishPage(document, canvas);
        document.finishPage(page);
    }
}
