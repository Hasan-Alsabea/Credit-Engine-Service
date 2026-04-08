package com.digibank.creditengine.exception;

/**
 * Thrown when a request passes bean validation but fails a business rule
 * (e.g. zero income when a DTI calculation is required).
 */
public class InvalidInputException extends RuntimeException {

    public InvalidInputException(String message) {
        super(message);
    }
}
