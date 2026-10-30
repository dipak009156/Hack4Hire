package com.quantumlock.document;

/** Unchecked exception for object store (MinIO) failures. */
public class ObjectStoreException extends RuntimeException {

    public ObjectStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}

