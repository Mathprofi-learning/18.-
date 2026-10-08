package ru.example.expression;

public abstract class ExpressionException extends Exception {

    protected ExpressionException(String message) {
        super(message);
    }

    public static class EvaluationException extends ExpressionException {

        public EvaluationException(String message) {
            super(message);
        }
    }

    public static final class DivisionByZeroException extends EvaluationException {

        public DivisionByZeroException(double dividend, double divisor) {
            super("Деление на ноль: " + dividend + " / " + divisor);
        }
    }

    public static final class UnknownVariableException extends EvaluationException {

        private final String variableName;

        public UnknownVariableException(String variableName) {
            super("Неизвестная переменная: " + variableName);
            this.variableName = variableName;
        }

        public String getVariableName() {
            return variableName;
        }
    }

    public static final class SyntaxException extends ExpressionException {

        private final int position;

        public SyntaxException(String message, int position) {
            super(message + " (позиция: " + (position + 1) + ")");
            this.position = position;
        }

        public int getPosition() {
            return position;
        }
    }
}