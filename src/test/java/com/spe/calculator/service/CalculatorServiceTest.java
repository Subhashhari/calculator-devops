package com.spe.calculator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.spe.calculator.exception.DivisionByZeroException;
import com.spe.calculator.exception.InvalidInputException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CalculatorServiceTest {

    private final CalculatorService service = new CalculatorService();

    @ParameterizedTest
    @CsvSource({"2, 3, 5", "-2, -3, -5", "1.5, 2.25, 3.75", "0, 0, 0"})
    void add(double a, double b, double expected) {
        assertThat(service.add(a, b)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"5, 3, 2", "3, 5, -2", "-1.5, -1.5, 0"})
    void subtract(double a, double b, double expected) {
        assertThat(service.subtract(a, b)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"4, 5, 20", "-4, 5, -20", "0.5, 0.5, 0.25", "7, 0, 0"})
    void multiply(double a, double b, double expected) {
        assertThat(service.multiply(a, b)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"10, 2, 5", "-9, 3, -3", "0, 5, 0"})
    void divide(double a, double b, double expected) {
        assertThat(service.divide(a, b)).isEqualTo(expected);
    }

    @Test
    void divideNonTerminating() {
        assertThat(service.divide(1, 3)).isCloseTo(0.3333, within(1e-4));
    }

    @Test
    void divideByZeroThrows() {
        assertThatThrownBy(() -> service.divide(1, 0))
                .isInstanceOf(DivisionByZeroException.class)
                .hasMessage("Division by zero is not allowed");
    }

    @Test
    void divideZeroByZeroThrows() {
        assertThatThrownBy(() -> service.divide(0, 0)).isInstanceOf(DivisionByZeroException.class);
    }

    @Test
    void nonFiniteOperandRejected() {
        assertThatThrownBy(() -> service.add(Double.NaN, 1)).isInstanceOf(InvalidInputException.class);
        assertThatThrownBy(() -> service.multiply(Double.POSITIVE_INFINITY, 1))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void overflowRejected() {
        assertThatThrownBy(() -> service.multiply(Double.MAX_VALUE, 2))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("Result is out of range");
    }
}
