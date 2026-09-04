package com.diamon.civil.terminal.editor;

public enum FeaTextTokenType {
    KEYWORD,      // Core language / solver keywords (*STEP, proc, SetFactory)
    PARAMETER,    // Keyword parameters (TYPE=, NSET=, etc.)
    CAD_COMMAND,  // CAD / DRAW commands (box, cylinder, Point, Line)
    COMMENT,      // Single or multiline comments (**, #, //, /*)
    STRING,       // Quoted strings ("...")
    VARIABLE,     // Script variables ($var, ${var})
    NUMBER,       // Floating point or integer numbers
    TEXT          // Default / plain text
}
