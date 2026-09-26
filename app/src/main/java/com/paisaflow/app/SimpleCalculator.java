package com.paisaflow.app;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

final class SimpleCalculator {
    private static final MathContext CONTEXT = new MathContext(16, RoundingMode.HALF_UP);
    private final String value;
    private int position;

    private SimpleCalculator(String value) {
        this.value = value == null ? "" : value.replace('×', '*').replace('÷', '/').replace('−', '-');
    }

    static String evaluate(String expression) {
        SimpleCalculator calculator = new SimpleCalculator(expression);
        BigDecimal result = calculator.expression();
        calculator.skipSpaces();
        if (calculator.position != calculator.value.length()) {
            throw new IllegalArgumentException("Check the calculation");
        }
        return result.stripTrailingZeros().toPlainString();
    }

    private BigDecimal expression() {
        BigDecimal result = term();
        while (true) {
            skipSpaces();
            if (consume('+')) result = result.add(term(), CONTEXT);
            else if (consume('-')) result = result.subtract(term(), CONTEXT);
            else return result;
        }
    }

    private BigDecimal term() {
        BigDecimal result = number();
        while (true) {
            skipSpaces();
            if (consume('*')) result = result.multiply(number(), CONTEXT);
            else if (consume('/')) {
                BigDecimal divisor = number();
                if (divisor.signum() == 0) throw new IllegalArgumentException("Cannot divide by zero");
                result = result.divide(divisor, CONTEXT);
            } else return result;
        }
    }

    private BigDecimal number() {
        skipSpaces();
        int start = position;
        if (peek('+') || peek('-')) position++;
        boolean digit = false;
        boolean decimal = false;
        while (position < value.length()) {
            char current = value.charAt(position);
            if (Character.isDigit(current)) {
                digit = true;
                position++;
            } else if (current == '.' && !decimal) {
                decimal = true;
                position++;
            } else break;
        }
        if (!digit) throw new IllegalArgumentException("Check the calculation");
        try {
            return new BigDecimal(value.substring(start, position), CONTEXT);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Check the calculation", error);
        }
    }

    private boolean consume(char expected) {
        if (!peek(expected)) return false;
        position++;
        return true;
    }

    private boolean peek(char expected) {
        return position < value.length() && value.charAt(position) == expected;
    }

    private void skipSpaces() {
        while (position < value.length() && Character.isWhitespace(value.charAt(position))) position++;
    }
}
