package com.diamon.civil.terminal.editor;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import java.util.List;

public class FeaTextSyntaxHighlighter implements TextWatcher {

    // Palette calibrated for dark console terminal background (#0A0E17)
    public static final int COLOR_KEYWORD = Color.parseColor("#00E5FF");     // Bright Cyan
    public static final int COLOR_PARAMETER = Color.parseColor("#FFD54F");   // Warm Amber/Yellow
    public static final int COLOR_CAD_CMD = Color.parseColor("#64B5F6");     // Sky Blue
    public static final int COLOR_COMMENT = Color.parseColor("#78909C");     // Muted Slate Gray
    public static final int COLOR_STRING = Color.parseColor("#81C784");      // Soft Light Green
    public static final int COLOR_VARIABLE = Color.parseColor("#FF4081");    // Accent Pink
    public static final int COLOR_NUMBER = Color.parseColor("#CE93D8");      // Lilac Purple

    private FeaTextTokenizer.SyntaxMode mode = FeaTextTokenizer.SyntaxMode.GENERIC;
    private boolean isHighlighting = false;
    private Runnable onContentChangedListener;

    public FeaTextSyntaxHighlighter() {
    }

    public FeaTextSyntaxHighlighter(FeaTextTokenizer.SyntaxMode mode) {
        this.mode = mode;
    }

    public void setSyntaxMode(FeaTextTokenizer.SyntaxMode mode) {
        this.mode = mode != null ? mode : FeaTextTokenizer.SyntaxMode.GENERIC;
    }

    public FeaTextTokenizer.SyntaxMode getSyntaxMode() {
        return mode;
    }

    public void setOnContentChangedListener(Runnable listener) {
        this.onContentChangedListener = listener;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable editable) {
        if (isHighlighting || editable == null) return;

        highlight(editable, mode);

        if (onContentChangedListener != null) {
            onContentChangedListener.run();
        }
    }

    public void highlight(Editable editable, FeaTextTokenizer.SyntaxMode syntaxMode) {
        if (editable == null) return;
        isHighlighting = true;
        try {
            // Remove existing syntax spans
            CharacterStyle[] existingSpans = editable.getSpans(0, editable.length(), CharacterStyle.class);
            for (CharacterStyle span : existingSpans) {
                if (span instanceof ForegroundColorSpan || span instanceof StyleSpan) {
                    editable.removeSpan(span);
                }
            }

            List<FeaTextToken> tokens = FeaTextTokenizer.tokenize(editable, syntaxMode);
            for (FeaTextToken token : tokens) {
                int start = Math.max(0, Math.min(token.getStart(), editable.length()));
                int end = Math.max(0, Math.min(token.getEnd(), editable.length()));
                if (start >= end) continue;

                switch (token.getType()) {
                    case KEYWORD:
                        editable.setSpan(new ForegroundColorSpan(COLOR_KEYWORD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        editable.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        break;
                    case PARAMETER:
                        editable.setSpan(new ForegroundColorSpan(COLOR_PARAMETER), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        break;
                    case CAD_COMMAND:
                        editable.setSpan(new ForegroundColorSpan(COLOR_CAD_CMD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        editable.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        break;
                    case COMMENT:
                        editable.setSpan(new ForegroundColorSpan(COLOR_COMMENT), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        editable.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        break;
                    case STRING:
                        editable.setSpan(new ForegroundColorSpan(COLOR_STRING), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        break;
                    case VARIABLE:
                        editable.setSpan(new ForegroundColorSpan(COLOR_VARIABLE), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        break;
                    case NUMBER:
                        editable.setSpan(new ForegroundColorSpan(COLOR_NUMBER), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        break;
                    case TEXT:
                    default:
                        break;
                }
            }
        } finally {
            isHighlighting = false;
        }
    }
}
