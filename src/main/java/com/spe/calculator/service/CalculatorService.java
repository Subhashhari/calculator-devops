package com.spe.calculator.service;

import com.spe.calculator.exception.DivisionByZeroException;
import com.spe.calculator.exception.InvalidInputException;
import org.springframework.stereotype.Service;

@Service
public class CalculatorService {

    public double add(double a, double b) {
        return checked(a, b, a + b);
    }

    public double subtract(double a, double b) {
        return checked(a, b, a - b);
    }

    public double multiply(double a, double b) {
        return checked(a, b, a * b);
    }

    public double divide(double a, double b) {
        requireFinite(a, b);
        // double division by zero yields Infinity/NaN instead of throwing, so check explicitly
        if (b == 0) {
            throw new DivisionByZeroException();
        }
        return checked(a, b, a / b);
    }

    private static double checked(double a, double b, double result) {
        requireFinite(a, b);
        if (!Double.isFinite(result)) {
            throw new InvalidInputException("Result is out of range");
        }
        return result;
    }

    private static void requireFinite(double a, double b) {
        if (!Double.isFinite(a) || !Double.isFinite(b)) {
            throw new InvalidInputException("Operands must be finite numbers");
        }
    }
}
