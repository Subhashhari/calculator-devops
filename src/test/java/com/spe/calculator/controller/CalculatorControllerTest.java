package com.spe.calculator.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Integration test: full Spring context, real request mapping, JSON serialisation and error handling. */
@SpringBootTest
@AutoConfigureMockMvc
class CalculatorControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void add() throws Exception {
        mvc.perform(get("/api/v1/add").param("a", "2").param("b", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("add"))
                .andExpect(jsonPath("$.a").value(2.0))
                .andExpect(jsonPath("$.b").value(3.0))
                .andExpect(jsonPath("$.result").value(6.0));
    }

    @Test
    void subtract() throws Exception {
        mvc.perform(get("/api/v1/subtract").param("a", "2").param("b", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(-1.0));
    }

    @Test
    void multiply() throws Exception {
        mvc.perform(get("/api/v1/multiply").param("a", "4").param("b", "2.5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(10.0));
    }

    @Test
    void divide() throws Exception {
        mvc.perform(get("/api/v1/divide").param("a", "9").param("b", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("divide"))
                .andExpect(jsonPath("$.result").value(3.0));
    }

    @Test
    void divideByZeroReturns400() throws Exception {
        mvc.perform(get("/api/v1/divide").param("a", "1").param("b", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Division by zero is not allowed"))
                .andExpect(jsonPath("$.path").value("/api/v1/divide"));
    }

    @Test
    void missingParameterReturns400() throws Exception {
        mvc.perform(get("/api/v1/add").param("a", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required parameter 'b'"));
    }

    @Test
    void nonNumericParameterReturns400() throws Exception {
        mvc.perform(get("/api/v1/multiply").param("a", "abc").param("b", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parameter 'a' must be a number"));
    }

    @Test
    void nonFiniteParameterReturns400() throws Exception {
        mvc.perform(get("/api/v1/add").param("a", "NaN").param("b", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Operands must be finite numbers"));
    }

    @Test
    void healthIsUp() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
