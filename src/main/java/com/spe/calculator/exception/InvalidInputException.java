package com.spe.calculator.exception;

/** Thrown when a calculation cannot be performed for the given operands. */
public class InvalidInputException extends RuntimeException {

    public InvalidInputException(String message) {
        super(message);
    }
}
