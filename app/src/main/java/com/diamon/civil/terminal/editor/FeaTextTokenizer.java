package com.diamon.civil.terminal.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FeaTextTokenizer {

    public enum SyntaxMode {
        INP,
        TCL,
        CAD_SCRIPT,
        GENERIC
    }

    public static SyntaxMode detectMode(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return SyntaxMode.GENERIC;
        }
        String lower = filename.trim().toLowerCase(Locale.US);
        if (lower.endsWith(".inp")) {
            return SyntaxMode.INP;
        } else if (lower.endsWith(".tcl") || lower.endsWith(".draw")) {
            return SyntaxMode.TCL;
        } else if (lower.endsWith(".geo") || lower.endsWith(".geo_unrolled") ||
                   lower.endsWith(".step") || lower.endsWith(".stp") ||
                   lower.endsWith(".brep") || lower.endsWith(".py")) {
            return SyntaxMode.CAD_SCRIPT;
        }
        return SyntaxMode.GENERIC;
    }

    // --- Regex Patterns for INP (CalculiX / Abaqus) ---
    private static final Pattern INP_COMMENT = Pattern.compile("(?m)^\\s*\\*\\*.*$");
    private static final Pattern INP_KEYWORD = Pattern.compile("(?m)^\\s*\\*[A-Za-z][A-Za-z0-9_ ]*");
    private static final Pattern INP_PARAM = Pattern.compile("(?i)\\b(TYPE|NSET|ELSET|MATERIAL|NAME|ORIENTATION|DIRECTION|COMPACT|TOTALS|UNITS)\\s*=");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(?i)\\b[-+]?\\d*\\.?\\d+(?:[eE][-+]?\\d+)?\\b");

    // --- Regex Patterns for TCL (DRAWEXE & Tcl scripts) ---
    private static final Pattern TCL_COMMENT = Pattern.compile("(?m)^\\s*#.*$");
    private static final Pattern TCL_STRING = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");
    private static final Pattern TCL_VARIABLE = Pattern.compile("\\$(?:\\{[a-zA-Z0-9_]+\\}|[a-zA-Z0-9_]+)");
    private static final Pattern TCL_KEYWORD = Pattern.compile("\\b(proc|set|if|then|else|elseif|for|foreach|while|break|continue|return|expr|puts|exit|package|source|catch|error|eval|global|upvar|variable)\\b");
    private static final Pattern TCL_CAD_CMD = Pattern.compile("\\b(pload|box|cylinder|sphere|cone|torus|wedge|bcut|bfuse|bcommon|bsection|vprops|sprops|mprops|checkshape|writebrep|readbrep|testwritestep|stepread|igesread|testwriteiges|donly|fit|vinit|vdisplay|vfit|erase|compound)\\b");

    // --- Regex Patterns for CAD Scripts (Gmsh .geo & OpenCASCADE) ---
    private static final Pattern CAD_LINE_COMMENT = Pattern.compile("(?m)//.*$");
    private static final Pattern CAD_BLOCK_COMMENT = Pattern.compile("/\\*[\\s\\S]*?\\*/");
    private static final Pattern CAD_STRING = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");
    private static final Pattern CAD_KEYWORD = Pattern.compile("\\b(Point|Line|Curve|Wire|Surface|Volume|Physical|SetFactory|Merge|Mesh|MeshSizeMax|MeshSizeMin|BooleanDifference|BooleanUnion|BooleanIntersection|BooleanFragments|Transfinite|Extrude|Rotate|Translate|Dilate|Symmetry|Delete|Coherence|Compound|Recombine|Circle|Ellipse|BSpline|Bezier|Spline|Plane|Ruled)\\b");
    private static final Pattern CAD_FACTORY = Pattern.compile("\\b(OpenCASCADE|Built_in)\\b");

    public static List<FeaTextToken> tokenize(CharSequence text, SyntaxMode mode) {
        List<FeaTextToken> tokens = new ArrayList<>();
        if (text == null || text.length() == 0) {
            return tokens;
        }

        switch (mode) {
            case INP:
                tokenizeInp(text, tokens);
                break;
            case TCL:
                tokenizeTcl(text, tokens);
                break;
            case CAD_SCRIPT:
                tokenizeCad(text, tokens);
                break;
            case GENERIC:
            default:
                tokenizeGeneric(text, tokens);
                break;
        }

        return tokens;
    }

    private static void tokenizeInp(CharSequence text, List<FeaTextToken> tokens) {
        // 1. Comments
        Matcher mComment = INP_COMMENT.matcher(text);
        while (mComment.find()) {
            tokens.add(new FeaTextToken(mComment.start(), mComment.end(), FeaTextTokenType.COMMENT));
        }

        // 2. Keywords (*NODE, *ELEMENT, etc.)
        Matcher mKeyword = INP_KEYWORD.matcher(text);
        while (mKeyword.find()) {
            if (!isInside(mKeyword.start(), tokens)) {
                tokens.add(new FeaTextToken(mKeyword.start(), mKeyword.end(), FeaTextTokenType.KEYWORD));
            }
        }

        // 3. Parameters (TYPE=, NSET=)
        Matcher mParam = INP_PARAM.matcher(text);
        while (mParam.find()) {
            if (!isInside(mParam.start(), tokens)) {
                tokens.add(new FeaTextToken(mParam.start(), mParam.end(), FeaTextTokenType.PARAMETER));
            }
        }

        // 4. Numbers
        Matcher mNum = NUMBER_PATTERN.matcher(text);
        while (mNum.find()) {
            if (!isInside(mNum.start(), tokens)) {
                tokens.add(new FeaTextToken(mNum.start(), mNum.end(), FeaTextTokenType.NUMBER));
            }
        }
    }

    private static void tokenizeTcl(CharSequence text, List<FeaTextToken> tokens) {
        // 1. Comments
        Matcher mComment = TCL_COMMENT.matcher(text);
        while (mComment.find()) {
            tokens.add(new FeaTextToken(mComment.start(), mComment.end(), FeaTextTokenType.COMMENT));
        }

        // 2. Strings
        Matcher mString = TCL_STRING.matcher(text);
        while (mString.find()) {
            if (!isInside(mString.start(), tokens)) {
                tokens.add(new FeaTextToken(mString.start(), mString.end(), FeaTextTokenType.STRING));
            }
        }

        // 3. Variables ($var)
        Matcher mVar = TCL_VARIABLE.matcher(text);
        while (mVar.find()) {
            if (!isInside(mVar.start(), tokens)) {
                tokens.add(new FeaTextToken(mVar.start(), mVar.end(), FeaTextTokenType.VARIABLE));
            }
        }

        // 4. CAD & DRAW Commands (box, cylinder, etc.)
        Matcher mCad = TCL_CAD_CMD.matcher(text);
        while (mCad.find()) {
            if (!isInside(mCad.start(), tokens)) {
                tokens.add(new FeaTextToken(mCad.start(), mCad.end(), FeaTextTokenType.CAD_COMMAND));
            }
        }

        // 5. Tcl Language Keywords (set, proc, if, puts)
        Matcher mKw = TCL_KEYWORD.matcher(text);
        while (mKw.find()) {
            if (!isInside(mKw.start(), tokens)) {
                tokens.add(new FeaTextToken(mKw.start(), mKw.end(), FeaTextTokenType.KEYWORD));
            }
        }

        // 6. Numbers
        Matcher mNum = NUMBER_PATTERN.matcher(text);
        while (mNum.find()) {
            if (!isInside(mNum.start(), tokens)) {
                tokens.add(new FeaTextToken(mNum.start(), mNum.end(), FeaTextTokenType.NUMBER));
            }
        }
    }

    private static void tokenizeCad(CharSequence text, List<FeaTextToken> tokens) {
        // 1. Comments (Line and Block)
        Matcher mBlock = CAD_BLOCK_COMMENT.matcher(text);
        while (mBlock.find()) {
            tokens.add(new FeaTextToken(mBlock.start(), mBlock.end(), FeaTextTokenType.COMMENT));
        }

        Matcher mLine = CAD_LINE_COMMENT.matcher(text);
        while (mLine.find()) {
            if (!isInside(mLine.start(), tokens)) {
                tokens.add(new FeaTextToken(mLine.start(), mLine.end(), FeaTextTokenType.COMMENT));
            }
        }

        // 2. Strings
        Matcher mString = CAD_STRING.matcher(text);
        while (mString.find()) {
            if (!isInside(mString.start(), tokens)) {
                tokens.add(new FeaTextToken(mString.start(), mString.end(), FeaTextTokenType.STRING));
            }
        }

        // 3. Gmsh Entities / CAD Keywords
        Matcher mKw = CAD_KEYWORD.matcher(text);
        while (mKw.find()) {
            if (!isInside(mKw.start(), tokens)) {
                tokens.add(new FeaTextToken(mKw.start(), mKw.end(), FeaTextTokenType.KEYWORD));
            }
        }

        // 4. CAD Engine / Factory identifiers
        Matcher mFac = CAD_FACTORY.matcher(text);
        while (mFac.find()) {
            if (!isInside(mFac.start(), tokens)) {
                tokens.add(new FeaTextToken(mFac.start(), mFac.end(), FeaTextTokenType.CAD_COMMAND));
            }
        }

        // 5. Numbers
        Matcher mNum = NUMBER_PATTERN.matcher(text);
        while (mNum.find()) {
            if (!isInside(mNum.start(), tokens)) {
                tokens.add(new FeaTextToken(mNum.start(), mNum.end(), FeaTextTokenType.NUMBER));
            }
        }
    }

    private static void tokenizeGeneric(CharSequence text, List<FeaTextToken> tokens) {
        Matcher mLine = Pattern.compile("(?m)^\\s*(?:#|//).*$").matcher(text);
        while (mLine.find()) {
            tokens.add(new FeaTextToken(mLine.start(), mLine.end(), FeaTextTokenType.COMMENT));
        }

        Matcher mString = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"").matcher(text);
        while (mString.find()) {
            if (!isInside(mString.start(), tokens)) {
                tokens.add(new FeaTextToken(mString.start(), mString.end(), FeaTextTokenType.STRING));
            }
        }

        Matcher mNum = NUMBER_PATTERN.matcher(text);
        while (mNum.find()) {
            if (!isInside(mNum.start(), tokens)) {
                tokens.add(new FeaTextToken(mNum.start(), mNum.end(), FeaTextTokenType.NUMBER));
            }
        }
    }

    private static boolean isInside(int pos, List<FeaTextToken> tokens) {
        for (FeaTextToken t : tokens) {
            if (pos >= t.getStart() && pos < t.getEnd()) {
                return true;
            }
        }
        return false;
    }
}
