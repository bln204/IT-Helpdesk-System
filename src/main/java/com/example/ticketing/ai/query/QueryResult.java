package com.example.ticketing.ai.query;

import java.util.List;

public record QueryResult(
        Status status,
        List<Row> rows,
        long totalCount,
        boolean hasMore,
        String scopeNote,
        String message) {

    public enum Status { OK, EMPTY, DENIED, ERROR }

    public static QueryResult of(List<Row> rows, long totalCount, int offset, String scopeNote) {
        return new QueryResult(totalCount == 0 ? Status.EMPTY : Status.OK, List.copyOf(rows), totalCount,
                offset + rows.size() < totalCount, scopeNote, null);
    }

    public static QueryResult count(long totalCount, String scopeNote) {
        return new QueryResult(totalCount == 0 ? Status.EMPTY : Status.OK, List.of(), totalCount, false,
                scopeNote, null);
    }

    public static QueryResult denied(String message) {
        return new QueryResult(Status.DENIED, List.of(), 0, false, null, message);
    }

    public static QueryResult error(String message) {
        return new QueryResult(Status.ERROR, List.of(), 0, false, null, message);
    }
}
