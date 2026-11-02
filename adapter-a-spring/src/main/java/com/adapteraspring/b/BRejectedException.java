package com.adapteraspring.b;

public class BRejectedException extends Exception {
    private final int statusCode;
    private final String bodyExcerpt;

    public BRejectedException(int statusCode, String bodyExcerpt) {
        super("B rejected request with HTTP " + statusCode + ": " + bodyExcerpt);
        this.statusCode = statusCode;
        this.bodyExcerpt = bodyExcerpt;
    }

    public int getStatusCode() { return statusCode; }
    public String getBodyExcerpt() { return bodyExcerpt; }
}
