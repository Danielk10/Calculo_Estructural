package com.diamon.civil.terminal.editor;

import java.util.Objects;

public class FeaTextToken {
    private final int start;
    private final int end;
    private final FeaTextTokenType type;

    public FeaTextToken(int start, int end, FeaTextTokenType type) {
        this.start = start;
        this.end = end;
        this.type = type;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public FeaTextTokenType getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FeaTextToken that = (FeaTextToken) o;
        return start == that.start && end == that.end && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end, type);
    }

    @Override
    public String toString() {
        return "FeaTextToken{" +
                "start=" + start +
                ", end=" + end +
                ", type=" + type +
                '}';
    }
}
