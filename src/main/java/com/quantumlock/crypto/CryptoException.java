package com.quantumlock.crypto;

/**
 * Unchecked exception thrown by the envelope encryption layer for cryptographic
 * failures, integrity violations, or unexpected algorithm errors.
 */
public class CryptoException extends RuntimeException {

    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}

