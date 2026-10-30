package com.quantumlock.kms;

/**
 * Unchecked exception thrown by the KMS layer on wrap/unwrap failures,
 * invalid key versions, or simulated fault injection.
 */
public class KmsException extends RuntimeException {

    public KmsException(String message) {
        super(message);
    }

    public KmsException(String message, Throwable cause) {
        super(message, cause);
    }
}

