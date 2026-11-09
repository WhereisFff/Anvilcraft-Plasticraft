package dev.anvilcraft.plasticraft.client.molding.editor;

import java.util.function.DoubleUnaryOperator;

/** 数值框只解析有限的四则运算；前缀加号、乘号和除号分别作用于每个选中元素的原值。 */
final class MoldingNumericExpression {
    private final String text;
    private int cursor;
    private int depth;

    private MoldingNumericExpression(String text) {
        this.text = text;
    }

    static DoubleUnaryOperator parse(String text) {
        String input = text.strip();
        if (input.isEmpty() || input.length() > 128) throw new IllegalArgumentException("Invalid numeric expression");
        char operation = input.charAt(0);
        boolean relative = operation == '+' || operation == '*' || operation == '/'
            || operation == '-' && input.startsWith("-=");
        String expression = relative ? input.substring(input.length() > 1 && input.charAt(1) == '=' ? 2 : 1) : input;
        MoldingNumericExpression parser = new MoldingNumericExpression(expression);
        double operand = parser.sum();
        parser.skipWhitespace();
        if (parser.cursor != parser.text.length()) throw new IllegalArgumentException("Unexpected numeric input");
        return value -> finite(relative ? switch (operation) {
            case '+' -> value + operand;
            case '-' -> value - operand;
            case '*' -> value * operand;
            case '/' -> value / operand;
            default -> throw new IllegalArgumentException("Invalid relative operation");
        } : operand);
    }

    private double sum() {
        double value = product();
        while (true) {
            if (take('+')) value = finite(value + product());
            else if (take('-')) value = finite(value - product());
            else return value;
        }
    }

    private double product() {
        double value = factor();
        while (true) {
            if (take('*')) value = finite(value * factor());
            else if (take('/')) value = finite(value / factor());
            else return value;
        }
    }

    private double factor() {
        if (++this.depth > 32) throw new IllegalArgumentException("Numeric expression is too deeply nested");
        try {
            if (take('+')) return factor();
            if (take('-')) return -factor();
            if (take('(')) {
                double value = sum();
                if (!take(')')) throw new IllegalArgumentException("Unclosed numeric expression");
                return value;
            }
            skipWhitespace();
            int start = this.cursor;
            while (this.cursor < this.text.length()) {
                char character = this.text.charAt(this.cursor);
                if (Character.isDigit(character) || character == '.') this.cursor++;
                else break;
            }
            if (this.cursor < this.text.length() && (this.text.charAt(this.cursor) == 'e' || this.text.charAt(this.cursor) == 'E')) {
                this.cursor++;
                if (this.cursor < this.text.length() && (this.text.charAt(this.cursor) == '+' || this.text.charAt(this.cursor) == '-')) this.cursor++;
                while (this.cursor < this.text.length() && Character.isDigit(this.text.charAt(this.cursor))) this.cursor++;
            }
            if (start == this.cursor) throw new IllegalArgumentException("Expected a number");
            return finite(Double.parseDouble(this.text.substring(start, this.cursor)));
        } finally {
            this.depth--;
        }
    }

    private boolean take(char expected) {
        skipWhitespace();
        if (this.cursor >= this.text.length() || this.text.charAt(this.cursor) != expected) return false;
        this.cursor++;
        return true;
    }

    private void skipWhitespace() {
        while (this.cursor < this.text.length() && Character.isWhitespace(this.text.charAt(this.cursor))) this.cursor++;
    }

    private static double finite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Numeric result must be finite");
        return value == 0.0D ? 0.0D : value;
    }
}
