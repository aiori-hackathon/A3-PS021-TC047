package com.adapteraspring.b;

public class BTransientException extends Exception {
    public BTransientException(String message, Throwable cause) { super(message, cause); }
    public BTransientException(String message) { super(message); }
}
