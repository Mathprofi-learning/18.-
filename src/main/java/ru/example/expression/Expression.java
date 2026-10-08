package ru.example.expression;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public sealed interface Expression
        permits Expression.NumberExpression,
        Expression.VariableExpression,
        Expression.UnaryExpression,
        Expression.BinaryExpression,
        Expression.FunctionCallExpression {

    double evaluate(EvaluationContext context) throws ExpressionException;

    enum Associativity {

        LEFT,

        RIGHT
    }

    enum UnaryOperator {

        NEGATE {
            @Override
            public double apply(double value) {
                return -value;
            }
        };

        public abstract double apply(double value);
    }

    enum BinaryOperator {

        PLUS(1, Associativity.LEFT) {
            @Override
            public double apply(double left, double right) {
                return left + right;
            }
        },

        MINUS(1, Associativity.LEFT) {
            @Override
            public double apply(double left, double right) {
                return left - right;
            }
        },

        MULTIPLY(2, Associativity.LEFT) {
            @Override
            public double apply(double left, double right) {
                return left * right;
            }
        },

        DIVIDE(2, Associativity.LEFT) {
            @Override
            public double apply(double left, double right)
                    throws ExpressionException.DivisionByZeroException {

                if (right == 0.0) {
                    throw new ExpressionException.DivisionByZeroException(left, right);
                }

                return left / right;
            }
        },

        POWER(3, Associativity.RIGHT) {
            @Override
            public double apply(double left, double right)
                    throws ExpressionException.EvaluationException {

                var result = Math.pow(left, right);

                if (!Double.isFinite(result)) {
                    throw new ExpressionException.EvaluationException(
                            "Результат операции не определён: " + left + " ^ " + right
                    );
                }

                return result;
            }
        };

        private final int priority;
        private final Associativity associativity;

        BinaryOperator(int priority, Associativity associativity) {
            this.priority = priority;
            this.associativity = associativity;
        }

        public int priority() {
            return priority;
        }

        public Associativity associativity() {
            return associativity;
        }

        public abstract double apply(double left, double right) throws ExpressionException;
    }


    enum Function {

        ABS(1) {
            @Override
            public double apply(List<Double> arguments) {
                return Math.abs(arguments.get(0));
            }
        },

        MIN(2) {
            @Override
            public double apply(List<Double> arguments) {
                return Math.min(arguments.get(0), arguments.get(1));
            }
        },

        MAX(2) {
            @Override
            public double apply(List<Double> arguments) {
                return Math.max(arguments.get(0), arguments.get(1));
            }
        };

        private final int arity;

        Function(int arity) {
            this.arity = arity;
        }

        public int arity() {
            return arity;
        }

        public static Optional<Function> byName(String name) {
            Objects.requireNonNull(name, "Имя функции не должно быть null");

            return switch (name.toLowerCase(Locale.ROOT)) {
                case "abs" -> Optional.of(ABS);
                case "min" -> Optional.of(MIN);
                case "max" -> Optional.of(MAX);
                default -> Optional.empty();
            };
        }

        public abstract double apply(List<Double> arguments);
    }

    record NumberExpression(double value) implements Expression {

        @Override
        public double evaluate(EvaluationContext context) {
            return value;
        }
    }

    record VariableExpression(EvaluationContext.VariableName name) implements Expression {

        public VariableExpression {
            Objects.requireNonNull(name, "Имя переменной не должно быть null");
        }

        @Override
        public double evaluate(EvaluationContext context)
                throws ExpressionException.UnknownVariableException {
            return context.valueOf(name);
        }
    }


    record UnaryExpression(UnaryOperator operator, Expression operand) implements Expression {

        public UnaryExpression {
            Objects.requireNonNull(operator, "Оператор не должен быть null");
            Objects.requireNonNull(operand, "Операнд не должен быть null");
        }

        @Override
        public double evaluate(EvaluationContext context) throws ExpressionException {
            var operandValue = operand.evaluate(context);
            return operator.apply(operandValue);
        }
    }

    record BinaryExpression(BinaryOperator operator, Expression left, Expression right)
            implements Expression {

        public BinaryExpression {
            Objects.requireNonNull(operator, "Оператор не должен быть null");
            Objects.requireNonNull(left, "Левый операнд не должен быть null");
            Objects.requireNonNull(right, "Правый операнд не должен быть null");
        }

        @Override
        public double evaluate(EvaluationContext context) throws ExpressionException {
            var leftValue = left.evaluate(context);
            var rightValue = right.evaluate(context);

            return operator.apply(leftValue, rightValue);
        }
    }

    record FunctionCallExpression(Function function, List<Expression> arguments)
            implements Expression {

        public FunctionCallExpression {
            Objects.requireNonNull(function, "Функция не должна быть null");
            Objects.requireNonNull(arguments, "Аргументы не должны быть null");

            if (arguments.size() != function.arity()) {
                throw new IllegalArgumentException(
                        "Функция " + function + " ожидает " + function.arity()
                                + " аргументов, получено: " + arguments.size()
                );
            }

            arguments = List.copyOf(arguments);
        }

        @Override
        public double evaluate(EvaluationContext context) throws ExpressionException {
            var values = new ArrayList<Double>(arguments.size());

            for (var argument : arguments) {
                values.add(argument.evaluate(context));
            }

            return function.apply(values);
        }
    }
}