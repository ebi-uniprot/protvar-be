package uk.ac.ebi.protvar.record;

public record ArrayPair<T, U>(T[] first, U[] second) {
    public ArrayPair {
        if (first.length != second.length) {
            throw new IllegalArgumentException("Arrays must have same length");
        }
    }

    public boolean isEmpty() {
        return first.length == 0;
    }

    public int size() {
        return first.length;
    }
}