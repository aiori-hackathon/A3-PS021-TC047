package com.adapteraspring.b;

public interface BTransport {
    BResponse send(BRequest request, String faultHeader) throws BTransientException, BRejectedException;
}
