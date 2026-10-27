package com.gathin.tvhome;

/**
 * Where the Continue-watching section sits in the grid: its cards start at {@code start}, then filler slots pad
 * them to whole rows so the apps below begin on a new row. The heading goes above the first row and a gap below
 * the last. No Android, so a host test pins it.
 */
final class WatchRows {
    final int start, count, columns;

    private WatchRows(int start, int count, int columns) {
        this.start = start;
        this.count = Math.max(0, count);
        this.columns = columns;
    }

    static WatchRows of(int start, int count, int columns) {
        return new WatchRows(start, count, columns);
    }

    /** Filler slots after {@code count} cards so the next entry starts a new row of {@code columns}. */
    static int fillersFor(int count, int columns) {
        if (count <= 0 || columns <= 0) return 0;
        return (columns - count % columns) % columns;
    }

    /** One past the last position of the section (cards and fillers). */
    int end() {
        return start + count + fillersFor(count, columns);
    }

    boolean inFirstRow(int position) {
        return count > 0 && position >= start && position < start + columns;
    }

    boolean inLastRow(int position) {
        return count > 0 && position >= end() - columns && position < end();
    }
}
