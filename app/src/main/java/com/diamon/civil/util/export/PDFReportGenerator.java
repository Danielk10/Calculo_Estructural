package com.diamon.civil.util.export;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.util.Log;

import com.diamon.civil.engine.DatParser;
import com.diamon.civil.engine.StructuralModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PDFReportGenerator {
    private static final String TAG = "PDFReportGenerator";

    // A4 dimensions in PostScript points (72 dpi)
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final float MARGIN_LEFT = 40f;
    private static final float MARGIN_RIGHT = 40f;
    private static final float MARGIN_TOP = 50f;
    private static final float MARGIN_BOTTOM = 50f;

    private final Paint titlePaint;
    private final Paint headerPaint;
    private final Paint subHeaderPaint;
    private final Paint bodyPaint;
    private final Paint tablePaint;
    private final Paint tableHeaderPaint;
    private final Paint linePaint;
    private final Paint footerPaint;

    private int pageNumber = 0;

    public PDFReportGenerator() {
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

        subHeaderPaint = new Paint();
        subHeaderPaint.setColor(Color.parseColor("#303F9F"));
        subHeaderPaint.setTextSize(12f);
        subHeaderPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        subHeaderPaint.setAntiAlias(true);

        bodyPaint = new Paint();
        bodyPaint.setColor(Color.DKGRAY);
        bodyPaint.setTextSize(10f);
        bodyPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
        bodyPaint.setAntiAlias(true);

        tablePaint = new Paint();
        tablePaint.setColor(Color.BLACK);
        tablePaint.setTextSize(9f);
        tablePaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
        tablePaint.setAntiAlias(true);

        tableHeaderPaint = new Paint();
        tableHeaderPaint.setColor(Color.WHITE);
        tableHeaderPaint.setTextSize(9f);
        tableHeaderPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        tableHeaderPaint.setAntiAlias(true);

        linePaint = new Paint();
        linePaint.setColor(Color.parseColor("#9E9E9E"));
        linePaint.setStrokeWidth(0.5f);
        linePaint.setAntiAlias(true);

        footerPaint = new Paint();
        footerPaint.setColor(Color.GRAY);
        footerPaint.setTextSize(8f);
        footerPaint.setAntiAlias(true);
    }

    public boolean generateReport(Context context, StructuralModel model, DatParser.ParseResult result,
                                   String projectName, String engineerName, File outputFile) {
        PdfDocument document = new PdfDocument();
        pageNumber = 0;

        try {
            drawCoverPage(document, projectName, engineerName);

            if (model != null) {
                drawModelSummaryPage(document, model);
            }

            if (result != null) {
                drawResultsPage(document, result);
            }

            FileOutputStream fos = new FileOutputStream(outputFile);
            document.writeTo(fos);
            fos.close();
            document.close();

            Log.i(TAG, "PDF report generated: " + outputFile.getAbsolutePath());
            return true;

        } catch (IOException e) {
            Log.e(TAG, "Error generating PDF: " + e.getMessage());
            document.close();
            return false;
        }
    }

    private void finishPage(PdfDocument document, Canvas canvas) {
        String footer = String.format("Structural Analysis FEA Advanced | CalculiX Engine | Page %d", pageNumber);
        canvas.drawText(footer, MARGIN_LEFT, PAGE_HEIGHT - 20f, footerPaint);

        String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
        float dateWidth = footerPaint.measureText(dateStr);
        canvas.drawText(dateStr, PAGE_WIDTH - MARGIN_RIGHT - dateWidth, PAGE_HEIGHT - 20f, footerPaint);

        canvas.drawLine(MARGIN_LEFT, PAGE_HEIGHT - 35f, PAGE_WIDTH - MARGIN_RIGHT, PAGE_HEIGHT - 35f, linePaint);
    }

    private void drawCoverPage(PdfDocument document, String projectName, String engineerName) {
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, ++pageNumber).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        float y = 120f;

        Paint bigTitle = new Paint(titlePaint);
        bigTitle.setTextSize(22f);
        String title = "REPORTE DE ANÁLISIS ESTRUCTURAL";
        float titleWidth = bigTitle.measureText(title);
        canvas.drawText(title, (PAGE_WIDTH - titleWidth) / 2f, y, bigTitle);
        y += 8f;

        Paint accentLine = new Paint();
        accentLine.setColor(Color.parseColor("#1A237E"));
        accentLine.setStrokeWidth(2f);
        canvas.drawLine((PAGE_WIDTH - titleWidth) / 2f, y, (PAGE_WIDTH + titleWidth) / 2f, y, accentLine);
        y += 40f;

        String[][] info = {
                {"Proyecto:", projectName != null ? projectName : "Cálculo Estructural"},
                {"Ingeniero:", engineerName != null ? engineerName : "N/A"},
                {"Fecha:", new SimpleDateFormat("dd 'de' MMMM, yyyy", Locale.getDefault()).format(new Date())},
                {"Software:", "Structural Analysis FEA Advanced"},
                {"Motor de Cálculo:", "CalculiX (ccx)"},
                {"Mallador:", "Gmsh"},
                {"Modelador CAD:", "Open CASCADE Technology (OCCT)"},
                {"Plataforma:", "Android NDK / ARM64-v8a"}
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
                "Este informe se genera con fines de cálculo y referencia usando CalculiX.",
                "El ingeniero proyectista/calculista es responsable de verificar todos los resultados."
        };

        for (String line : notice) {
            float lineWidth = noticePaint.measureText(line);
            canvas.drawText(line, (PAGE_WIDTH - lineWidth) / 2f, y, noticePaint);
            y += 14f;
        }

        finishPage(document, canvas);
        document.finishPage(page);
    }

    private void drawModelSummaryPage(PdfDocument document, StructuralModel model) {
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, ++pageNumber).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        float y = MARGIN_TOP;

        canvas.drawText("MODEL SUMMARY", MARGIN_LEFT, y, headerPaint);
        y += 6f;
        canvas.drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y, linePaint);
        y += 20f;

        if (model.nodes != null && !model.nodes.isEmpty()) {
            canvas.drawText("Node Coordinates", MARGIN_LEFT, y, subHeaderPaint);
            y += 16f;

            String[] nodeHeaders = {"ID", "X (m)", "Y (m)", "Z (m)"};
            float[] nodeColWidths = {60f, 100f, 100f, 100f};

            y = drawTableHeader(canvas, nodeHeaders, nodeColWidths, y);

            for (StructuralModel.Node node : model.nodes) {
                if (y > PAGE_HEIGHT - MARGIN_BOTTOM - 20f) {
                    finishPage(document, canvas);
                    document.finishPage(page);
                    pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, ++pageNumber).create();
                    page = document.startPage(pageInfo);
                    canvas = page.getCanvas();
                    y = MARGIN_TOP;
                    y = drawTableHeader(canvas, nodeHeaders, nodeColWidths, y);
                }
                String[] row = {
                        String.valueOf(node.id),
                        String.format(Locale.US, "%.3f", node.x),
                        String.format(Locale.US, "%.3f", node.y),
                        String.format(Locale.US, "%.3f", node.z)
                };
                y = drawTableRow(canvas, row, nodeColWidths, y);
            }
            y += 20f;
        }

        if (model.elements != null && !model.elements.isEmpty()) {
            canvas.drawText("Element Connectivity", MARGIN_LEFT, y, subHeaderPaint);
            y += 16f;

            String[] elemHeaders = {"ID", "Node I", "Node J", "Section", "Material"};
            float[] elemColWidths = {60f, 80f, 80f, 100f, 100f};

            y = drawTableHeader(canvas, elemHeaders, elemColWidths, y);

            for (StructuralModel.Element elem : model.elements) {
                if (y > PAGE_HEIGHT - MARGIN_BOTTOM - 20f) {
                    finishPage(document, canvas);
                    document.finishPage(page);
                    pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, ++pageNumber).create();
                    page = document.startPage(pageInfo);
                    canvas = page.getCanvas();
                    y = MARGIN_TOP;
                    y = drawTableHeader(canvas, elemHeaders, elemColWidths, y);
                }
                String[] row = {
                        String.valueOf(elem.id),
                        String.valueOf(elem.node1Id),
                        String.valueOf(elem.node2Id),
                        elem.sectionName,
                        elem.materialName
                };
                y = drawTableRow(canvas, row, elemColWidths, y);
            }
        }

        finishPage(document, canvas);
        document.finishPage(page);
    }

    private void drawResultsPage(PdfDocument document, DatParser.ParseResult result) {
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, ++pageNumber).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        float y = MARGIN_TOP;

        canvas.drawText("ANALYSIS RESULTS", MARGIN_LEFT, y, headerPaint);
        y += 6f;
        canvas.drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y, linePaint);
        y += 20f;

        if (result.displacements != null && !result.displacements.isEmpty()) {
            canvas.drawText("Nodal Displacements", MARGIN_LEFT, y, subHeaderPaint);
            y += 16f;

            String[] dispHeaders = {"Node ID", "Ux (m)", "Uy (m)", "Uz (m)"};
            float[] dispColWidths = {60f, 120f, 120f, 120f};

            y = drawTableHeader(canvas, dispHeaders, dispColWidths, y);

            for (DatParser.NodeDisplacement disp : result.displacements) {
                if (y > PAGE_HEIGHT - MARGIN_BOTTOM - 20f) {
                    finishPage(document, canvas);
                    document.finishPage(page);
                    pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, ++pageNumber).create();
                    page = document.startPage(pageInfo);
                    canvas = page.getCanvas();
                    y = MARGIN_TOP;
                    y = drawTableHeader(canvas, dispHeaders, dispColWidths, y);
                }
                String[] row = {
                        String.valueOf(disp.nodeId),
                        String.format(Locale.US, "%.6e", disp.ux),
                        String.format(Locale.US, "%.6e", disp.uy),
                        String.format(Locale.US, "%.6e", disp.uz)
                };
                y = drawTableRow(canvas, row, dispColWidths, y);
            }
            y += 20f;
        }

        if (result.forces != null && !result.forces.isEmpty()) {
            canvas.drawText("Element Internal Forces", MARGIN_LEFT, y, subHeaderPaint);
            y += 16f;

            String[] forceHeaders = {"Elem ID", "Int Pt", "N", "V2", "V3", "M1", "M2", "M3"};
            float[] forceColWidths = {50f, 40f, 60f, 60f, 60f, 60f, 60f, 60f};

            y = drawTableHeader(canvas, forceHeaders, forceColWidths, y);

            for (DatParser.SectionForces force : result.forces) {
                if (y > PAGE_HEIGHT - MARGIN_BOTTOM - 20f) {
                    finishPage(document, canvas);
                    document.finishPage(page);
                    pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, ++pageNumber).create();
                    page = document.startPage(pageInfo);
                    canvas = page.getCanvas();
                    y = MARGIN_TOP;
                    y = drawTableHeader(canvas, forceHeaders, forceColWidths, y);
                }
                String[] row = {
                        String.valueOf(force.elementId),
                        String.valueOf(force.integrationPoint),
                        String.format(Locale.US, "%.3e", force.N),
                        String.format(Locale.US, "%.3e", force.V2),
                        String.format(Locale.US, "%.3e", force.V3),
                        String.format(Locale.US, "%.3e", force.M1),
                        String.format(Locale.US, "%.3e", force.M2),
                        String.format(Locale.US, "%.3e", force.M3)
                };
                y = drawTableRow(canvas, row, forceColWidths, y);
            }
        }

        finishPage(document, canvas);
        document.finishPage(page);
    }

    private float drawTableHeader(Canvas canvas, String[] headers, float[] colWidths, float y) {
        float rowHeight = 16f;
        float x = MARGIN_LEFT;
        Paint headerBg = new Paint();
        headerBg.setColor(Color.parseColor("#1A237E"));
        canvas.drawRect(x, y - 11f, x + sumArray(colWidths), y + rowHeight - 7f, headerBg);
        for (int i = 0; i < headers.length; i++) {
            canvas.drawText(headers[i], x + 4f, y + 2f, tableHeaderPaint);
            x += colWidths[i];
        }
        return y + rowHeight;
    }

    private float drawTableRow(Canvas canvas, String[] values, float[] colWidths, float y) {
        float rowHeight = 14f;
        float x = MARGIN_LEFT;
        Paint rowBg = new Paint();
        int rowIndex = (int) ((y - MARGIN_TOP) / rowHeight);
        rowBg.setColor(rowIndex % 2 == 0 ? Color.parseColor("#F5F5F5") : Color.WHITE);
        canvas.drawRect(x, y - 10f, x + sumArray(colWidths), y + rowHeight - 8f, rowBg);
        canvas.drawLine(x, y + rowHeight - 8f, x + sumArray(colWidths), y + rowHeight - 8f, linePaint);
        for (int i = 0; i < values.length && i < colWidths.length; i++) {
            String text = values[i];
            if (text == null) text = "N/A";
            float maxWidth = colWidths[i] - 8f;
            if (tablePaint.measureText(text) > maxWidth) {
                while (text.length() > 1 && tablePaint.measureText(text + "…") > maxWidth) {
                    text = text.substring(0, text.length() - 1);
                }
                text += "…";
            }
            canvas.drawText(text, x + 4f, y + 2f, tablePaint);
            x += colWidths[i];
        }
        return y + rowHeight;
    }

    private float sumArray(float[] arr) {
        float sum = 0;
        for (float v : arr) sum += v;
        return sum;
    }
}
