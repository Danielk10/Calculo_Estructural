package com.diamon.civil.solids.export;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
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
    private static final float USABLE_WIDTH = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT; // 515f

    private final Paint titlePaint;
    private final Paint headerPaint;
    private final Paint subHeaderPaint;
    private final Paint bodyPaint;
    private final Paint tablePaint;
    private final Paint tableHeaderPaint;
    private final Paint linePaint;
    private final Paint footerPaint;

    private int pageNumber = 0;

    public SolidPDFReportGenerator() {
        titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.BLACK);
        titlePaint.setTextSize(18f);
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        headerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        headerPaint.setColor(Color.parseColor("#1A237E")); // Dark blue
        headerPaint.setTextSize(13f);
        headerPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        subHeaderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        subHeaderPaint.setColor(Color.parseColor("#303F9F"));
        subHeaderPaint.setTextSize(11f);
        subHeaderPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bodyPaint.setColor(Color.DKGRAY);
        bodyPaint.setTextSize(9.5f);
        bodyPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));

        tablePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tablePaint.setColor(Color.BLACK);
        tablePaint.setTextSize(8.5f);
        tablePaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));

        tableHeaderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tableHeaderPaint.setColor(Color.WHITE);
        tableHeaderPaint.setTextSize(8.5f);
        tableHeaderPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.parseColor("#9E9E9E"));
        linePaint.setStrokeWidth(0.5f);

        footerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        footerPaint.setColor(Color.GRAY);
        footerPaint.setTextSize(8f);
    }

    private static class PageContext {
        final PdfDocument document;
        PdfDocument.Page page;
        Canvas canvas;
        float y;

        PageContext(PdfDocument doc) {
            this.document = doc;
        }

        void newPage(SolidPDFReportGenerator gen) {
            if (page != null) {
                gen.finishPage(document, canvas);
                document.finishPage(page);
            }
            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, ++gen.pageNumber).create();
            page = document.startPage(pageInfo);
            canvas = page.getCanvas();
            y = MARGIN_TOP;
        }

        void ensureSpace(SolidPDFReportGenerator gen, float neededHeight) {
            if (page == null || y + neededHeight > PAGE_HEIGHT - MARGIN_BOTTOM - 15f) {
                newPage(gen);
            }
        }

        void finish(SolidPDFReportGenerator gen) {
            if (page != null) {
                gen.finishPage(document, canvas);
                document.finishPage(page);
                page = null;
            }
        }
    }

    /**
     * @deprecated Use {@link #generateReport(Context, File, String, File)} instead.
     * Raw console logs are excluded from the PDF report; only engineering calculation data is exported.
     */
    @Deprecated
    public boolean generateReport(Context context, File outputFile, String projectName, String logText) {
        return generateReport(context, outputFile, projectName, (File) null);
    }

    public boolean generateReport(Context context, File outputFile, String projectName, File workDir) {
        PdfDocument document = new PdfDocument();
        pageNumber = 0;
        PageContext ctx = new PageContext(document);

        try {
            drawCoverPage(document, projectName);
            File datFile = (workDir != null) ? new File(workDir, "job_solid.dat") : null;
            ctx.newPage(this);
            drawSummaryPage(ctx, datFile, workDir);

            ctx.finish(this);
            FileOutputStream fos = new FileOutputStream(outputFile);
            document.writeTo(fos);
            fos.close();
            document.close();

            Log.i(TAG, "Solid PDF report generated successfully: " + outputFile.getAbsolutePath());
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error generating Solid PDF: " + e.getMessage(), e);
            document.close();
            return false;
        }
    }

    /**
     * Backward-compatible overload. The logText parameter is ignored to keep the PDF report
     * focused strictly on engineering calculation data without raw console logs.
     */
    public boolean generateReport(Context context, File outputFile, String projectName, File workDir, String logText) {
        return generateReport(context, outputFile, projectName, workDir);
    }

    private void finishPage(PdfDocument document, Canvas canvas) {
        String footer = String.format(Locale.US, "Structural Analysis FEA 3D | 3D Solid Analysis | Page %d", pageNumber);
        canvas.drawText(footer, MARGIN_LEFT, PAGE_HEIGHT - 20f, footerPaint);

        String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
        float dateWidth = footerPaint.measureText(dateStr);
        canvas.drawText(dateStr, PAGE_WIDTH - MARGIN_RIGHT - dateWidth, PAGE_HEIGHT - 20f, footerPaint);

        canvas.drawLine(MARGIN_LEFT, PAGE_HEIGHT - 32f, PAGE_WIDTH - MARGIN_RIGHT, PAGE_HEIGHT - 32f, linePaint);
    }

    private void drawCoverPage(PdfDocument document, String projectName) {
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, ++pageNumber).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        float y = 120f;

        Paint bigTitle = new Paint(titlePaint);
        bigTitle.setTextSize(20f);
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
                {"Software:", "Structural Analysis FEA 3D"},
                {"Date:", new SimpleDateFormat("MMMM dd, yyyy", Locale.US).format(new Date())},
                {"Engine:", "CalculiX FEA (ccx 2.23)"},
                {"Mesher:", "Gmsh 3D Solid Mesh Generator (Gmsh 5.0.0)"},
                {"CAD Modeler:", "OpenCASCADE Technology (OCCT 8.0.0.p1)"},
                {"Platform:", "Android NDK / ARM64-v8a"}
        };

        Paint labelPaint = new Paint(headerPaint);
        labelPaint.setTextSize(11f);
        Paint valuePaint = new Paint(bodyPaint);
        valuePaint.setTextSize(11f);
        valuePaint.setTypeface(Typeface.DEFAULT);

        for (String[] row : info) {
            canvas.drawText(row[0], MARGIN_LEFT + 40f, y, labelPaint);
            canvas.drawText(row[1], MARGIN_LEFT + 170f, y, valuePaint);
            y += 22f;
        }

        y += 30f;
        canvas.drawLine(MARGIN_LEFT + 20f, y, PAGE_WIDTH - MARGIN_RIGHT - 20f, y, linePaint);
        y += 30f;

        Paint noticePaint = new Paint(bodyPaint);
        noticePaint.setTextSize(9f);
        noticePaint.setColor(Color.GRAY);
        String[] notice = {
                "This report is generated for solid mechanics calculation and reference using CalculiX.",
                "The structural engineer is responsible for validating all boundary conditions and results."
        };

        for (String line : notice) {
            float lineWidth = noticePaint.measureText(line);
            canvas.drawText(line, (PAGE_WIDTH - lineWidth) / 2f, y, noticePaint);
            y += 14f;
        }

        finishPage(document, canvas);
        document.finishPage(page);
    }

    private static class DispEntry implements Comparable<DispEntry> {
        int nodeId;
        double ux, uy, uz, mag;
        @Override
        public int compareTo(DispEntry o) {
            return Double.compare(o.mag, this.mag); // descending
        }
    }

    private static class StressEntry implements Comparable<StressEntry> {
        int elemId, intPt;
        double sxx, syy, szz, sxy, sxz, syz, vm;
        @Override
        public int compareTo(StressEntry o) {
            return Double.compare(o.vm, this.vm); // descending
        }
    }

    private void drawSummaryPage(PageContext ctx, File datFile, File workDir) {
        ctx.ensureSpace(this, 30f);
        ctx.canvas.drawText("EXECUTIVE SUMMARY (FEA SOLID RESULTS)", MARGIN_LEFT, ctx.y, headerPaint);
        ctx.y += 6f;
        ctx.canvas.drawLine(MARGIN_LEFT, ctx.y, PAGE_WIDTH - MARGIN_RIGHT, ctx.y, linePaint);
        ctx.y += 18f;

        double maxDisp = 0.0;
        int maxDispNode = 0;
        double maxVonMises = 0.0;
        int maxStressNode = 0;
        int maxStressElem = 0;

        List<DispEntry> dispList = new ArrayList<>();
        List<StressEntry> stressList = new ArrayList<>();

        if (datFile != null && datFile.exists()) {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(datFile), 32768)) {
                String line;
                boolean inDisp = false;
                boolean inStress = false;

                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) continue;
                    
                    char firstChar = trimmed.charAt(0);
                    if (!Character.isDigit(firstChar) && firstChar != '-') {
                        String lower = trimmed.toLowerCase(Locale.US);
                        if (lower.contains("displacements (vx,vy,vz)")) {
                            inDisp = true;
                            inStress = false;
                            continue;
                        }
                        if (lower.contains("stresses (elem, integ.pnt.,sxx,syy,szz,sxy,sxz,syz)")) {
                            inStress = true;
                            inDisp = false;
                            continue;
                        }
                    }

                    if (!inDisp && !inStress) continue;

                    String[] parts = trimmed.split("\\s+");
                    if (parts.length == 0 || !Character.isDigit(parts[0].charAt(0))) continue;

                    if (inDisp && parts.length >= 4) {
                        try {
                            int nodeId = Integer.parseInt(parts[0]);
                            double ux = Double.parseDouble(parts[1].replace('D', 'E'));
                            double uy = Double.parseDouble(parts[2].replace('D', 'E'));
                            double uz = Double.parseDouble(parts[3].replace('D', 'E'));
                            double disp = Math.sqrt(ux*ux + uy*uy + uz*uz);
                            DispEntry de = new DispEntry();
                            de.nodeId = nodeId; de.ux = ux; de.uy = uy; de.uz = uz; de.mag = disp;
                            dispList.add(de);
                            if (disp > maxDisp) {
                                maxDisp = disp;
                                maxDispNode = nodeId;
                            }
                        } catch (Exception ignore) {}
                    } else if (inStress && parts.length >= 8) {
                        try {
                            int elemId = Integer.parseInt(parts[0]);
                            int intPt = Integer.parseInt(parts[1]);
                            double sxx = Double.parseDouble(parts[2].replace('D', 'E'));
                            double syy = Double.parseDouble(parts[3].replace('D', 'E'));
                            double szz = Double.parseDouble(parts[4].replace('D', 'E'));
                            double sxy = Double.parseDouble(parts[5].replace('D', 'E'));
                            double sxz = Double.parseDouble(parts[6].replace('D', 'E'));
                            double syz = Double.parseDouble(parts[7].replace('D', 'E'));
                            
                            double vm = Math.sqrt(0.5 * (Math.pow(sxx-syy, 2) + Math.pow(syy-szz, 2) + Math.pow(szz-sxx, 2) + 6*(sxy*sxy + syz*syz + sxz*sxz)));
                            StressEntry se = new StressEntry();
                            se.elemId = elemId; se.intPt = intPt;
                            se.sxx = sxx; se.syy = syy; se.szz = szz; se.sxy = sxy; se.sxz = sxz; se.syz = syz; se.vm = vm;
                            stressList.add(se);
                            if (vm > maxVonMises) {
                                maxVonMises = vm;
                                maxStressElem = elemId;
                                maxStressNode = intPt;
                            }
                        } catch (Exception ignore) {}
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing dat: " + e.getMessage());
            }
        }

        if (dispList.isEmpty() && workDir != null) {
            File frdFile = new File(workDir, "job_solid.frd");
            if (frdFile.exists()) {
                try (java.io.BufferedReader frdReader = new java.io.BufferedReader(new java.io.FileReader(frdFile))) {
                    String frdLine;
                    boolean captureDisp = false;
                    while ((frdLine = frdReader.readLine()) != null) {
                        if (frdLine.contains("-4  DISP")) {
                            captureDisp = true;
                            continue;
                        }
                        if (captureDisp && frdLine.startsWith(" -3")) break;
                        if (captureDisp && frdLine.startsWith(" -1") && frdLine.length() >= 13) {
                            try {
                                int nodeId = Integer.parseInt(frdLine.substring(3, 13).trim());
                                List<Double> vals = new ArrayList<>();
                                for (int i = 13; i < frdLine.length(); i += 12) {
                                    int end = Math.min(i + 12, frdLine.length());
                                    String chunk = frdLine.substring(i, end).trim();
                                    if (!chunk.isEmpty()) vals.add(Double.parseDouble(chunk));
                                }
                                if (vals.size() >= 3) {
                                    double ux = vals.get(0), uy = vals.get(1), uz = vals.get(2);
                                    double disp = Math.sqrt(ux * ux + uy * uy + uz * uz);
                                    DispEntry de = new DispEntry();
                                    de.nodeId = nodeId; de.ux = ux; de.uy = uy; de.uz = uz; de.mag = disp;
                                    dispList.add(de);
                                    if (disp > maxDisp) {
                                        maxDisp = disp;
                                        maxDispNode = nodeId;
                                    }
                                }
                            } catch (Exception ignore) {}
                        }
                    }
                } catch (Exception ignore) {}
            }
        }

        if (dispList.isEmpty() && stressList.isEmpty()) {
            ctx.ensureSpace(this, 60f);
            ctx.canvas.drawText("No completed simulation output available.", MARGIN_LEFT, ctx.y, subHeaderPaint);
            ctx.y += 16f;
            ctx.y = drawWrappedText(ctx, "Execute the FEA solver using the 'EXECUTE FEA SOLVER' button to compute nodal displacements and Cauchy stresses before generating the engineering summary.", MARGIN_LEFT, USABLE_WIDTH, 12f, bodyPaint);
            return;
        }

        ctx.ensureSpace(this, 120f);
        ctx.canvas.drawText("1. Peak Response Extremes", MARGIN_LEFT, ctx.y, subHeaderPaint);
        ctx.y += 14f;

        String[] peakHeaders = {"Response Quantity", "Peak Value", "Location in Mesh"};
        float[] peakColWidths = {160f, 150f, 200f}; // Sum = 510f
        ctx.y = drawTableHeader(ctx, peakHeaders, peakColWidths);

        ctx.y = drawTableRow(ctx, new String[]{
                "Max Nodal Displacement (|U|)",
                String.format(Locale.US, "%.4f mm", maxDisp),
                String.format(Locale.US, "Node ID: %d", maxDispNode)
        }, peakColWidths);

        ctx.y = drawTableRow(ctx, new String[]{
                "Max Von Mises Stress (sigma_v)",
                String.format(Locale.US, "%.2f MPa", maxVonMises),
                String.format(Locale.US, "Element %d (Int. Pt %d)", maxStressElem, maxStressNode)
        }, peakColWidths);
        ctx.y += 16f;

        // Top Displacements Table
        if (!dispList.isEmpty()) {
            Collections.sort(dispList);
            int count = Math.min(dispList.size(), 8);

            ctx.ensureSpace(this, 35f);
            ctx.canvas.drawText("2. Critical Displacement Concentrations (Top Nodes)", MARGIN_LEFT, ctx.y, subHeaderPaint);
            ctx.y += 14f;

            String[] dispHeaders = {"Node ID", "Ux (mm)", "Uy (mm)", "Uz (mm)", "Total |U| (mm)"};
            float[] dispColWidths = {70f, 110f, 110f, 110f, 110f}; // Sum = 510f
            ctx.y = drawTableHeader(ctx, dispHeaders, dispColWidths);

            for (int i = 0; i < count; i++) {
                ctx.ensureSpace(this, 16f);
                DispEntry de = dispList.get(i);
                String[] row = {
                        String.valueOf(de.nodeId),
                        String.format(Locale.US, "%.4e", de.ux),
                        String.format(Locale.US, "%.4e", de.uy),
                        String.format(Locale.US, "%.4e", de.uz),
                        String.format(Locale.US, "%.4e", de.mag)
                };
                ctx.y = drawTableRow(ctx, row, dispColWidths);
            }
            ctx.y += 16f;
        }

        // Top Stresses Table
        if (!stressList.isEmpty()) {
            Collections.sort(stressList);
            int count = Math.min(stressList.size(), 8);

            ctx.ensureSpace(this, 35f);
            ctx.canvas.drawText("3. Critical Von Mises Stress Concentrations (Top Elements)", MARGIN_LEFT, ctx.y, subHeaderPaint);
            ctx.y += 14f;

            String[] stressHeaders = {"Elem ID", "Int. Pt", "S_xx (MPa)", "S_yy (MPa)", "S_zz (MPa)", "Von Mises (MPa)"};
            float[] stressColWidths = {65f, 45f, 100f, 100f, 100f, 100f}; // Sum = 510f
            ctx.y = drawTableHeader(ctx, stressHeaders, stressColWidths);

            for (int i = 0; i < count; i++) {
                ctx.ensureSpace(this, 16f);
                StressEntry se = stressList.get(i);
                String[] row = {
                        String.valueOf(se.elemId),
                        String.valueOf(se.intPt),
                        String.format(Locale.US, "%.2f", se.sxx),
                        String.format(Locale.US, "%.2f", se.syy),
                        String.format(Locale.US, "%.2f", se.szz),
                        String.format(Locale.US, "%.2f", se.vm)
                };
                ctx.y = drawTableRow(ctx, row, stressColWidths);
            }
            ctx.y += 16f;
        }

        ctx.ensureSpace(this, 75f);
        ctx.canvas.drawText("Engineering Interpretation & Yield Criterion:", MARGIN_LEFT, ctx.y, subHeaderPaint);
        ctx.y += 14f;
        ctx.y = drawWrappedText(ctx, "• Von Mises Equivalent Stress evaluates yielding in ductile materials under multi-axial state of stress per Huber-Mises-Hencky theory.", MARGIN_LEFT, USABLE_WIDTH, 12f, bodyPaint);
        ctx.y = drawWrappedText(ctx, "• Displacements and deformations can be visualized in full 3D interactive color contour in the 3D Viewer module.", MARGIN_LEFT, USABLE_WIDTH, 12f, bodyPaint);
        ctx.y = drawWrappedText(ctx, "• High stress concentrations near sharp geometric features should be evaluated for notch fatigue or local yielding.", MARGIN_LEFT, USABLE_WIDTH, 12f, bodyPaint);
    }


    private float drawTableHeader(PageContext ctx, String[] headers, float[] colWidths) {
        float rowHeight = 15f;
        float x = MARGIN_LEFT;
        Paint headerBg = new Paint();
        headerBg.setColor(Color.parseColor("#1A237E"));
        ctx.canvas.drawRect(x, ctx.y - 10f, x + sumArray(colWidths), ctx.y + rowHeight - 6f, headerBg);
        for (int i = 0; i < headers.length; i++) {
            ctx.canvas.drawText(headers[i], x + 4f, ctx.y + 2f, tableHeaderPaint);
            x += colWidths[i];
        }
        return ctx.y + rowHeight;
    }

    private float drawTableRow(PageContext ctx, String[] values, float[] colWidths) {
        float rowHeight = 13.5f;
        float x = MARGIN_LEFT;
        Paint rowBg = new Paint();
        int rowIndex = (int) ((ctx.y - MARGIN_TOP) / rowHeight);
        rowBg.setColor(rowIndex % 2 == 0 ? Color.parseColor("#F5F5F5") : Color.WHITE);
        ctx.canvas.drawRect(x, ctx.y - 9f, x + sumArray(colWidths), ctx.y + rowHeight - 7f, rowBg);
        ctx.canvas.drawLine(x, ctx.y + rowHeight - 7f, x + sumArray(colWidths), ctx.y + rowHeight - 7f, linePaint);
        for (int i = 0; i < values.length && i < colWidths.length; i++) {
            String text = values[i];
            if (text == null) text = "N/A";
            float maxWidth = colWidths[i] - 6f;
            if (tablePaint.measureText(text) > maxWidth) {
                while (text.length() > 1 && tablePaint.measureText(text + "…") > maxWidth) {
                    text = text.substring(0, text.length() - 1);
                }
                text += "…";
            }
            ctx.canvas.drawText(text, x + 3f, ctx.y + 1.5f, tablePaint);
            x += colWidths[i];
        }
        return ctx.y + rowHeight;
    }

    private float drawWrappedText(PageContext ctx, String text, float x, float maxWidth, float lineHeight, Paint paint) {
        if (text == null || text.isEmpty()) return ctx.y;
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine.append(currentLine.length() == 0 ? "" : " ").append(word);
            } else {
                if (currentLine.length() > 0) {
                    ctx.ensureSpace(this, lineHeight + 4f);
                    ctx.canvas.drawText(currentLine.toString(), x, ctx.y, paint);
                    ctx.y += lineHeight;
                    currentLine = new StringBuilder(word);
                } else {
                    ctx.ensureSpace(this, lineHeight + 4f);
                    ctx.canvas.drawText(word, x, ctx.y, paint);
                    ctx.y += lineHeight;
                }
            }
        }
        if (currentLine.length() > 0) {
            ctx.ensureSpace(this, lineHeight + 4f);
            ctx.canvas.drawText(currentLine.toString(), x, ctx.y, paint);
            ctx.y += lineHeight;
        }
        return ctx.y;
    }

    private float sumArray(float[] arr) {
        float sum = 0;
        for (float v : arr) sum += v;
        return sum;
    }
}

