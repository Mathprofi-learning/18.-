package ru.example.expression;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;


public record EvaluationContext(Map<EvaluationContext.VariableName, Double> variables) {


    public EvaluationContext {
        Objects.requireNonNull(variables, "Словарь переменных не должен быть null");
        variables = Map.copyOf(variables);
    }

    public static EvaluationContext empty() {
        return new EvaluationContext(Map.of());
    }


    public double valueOf(VariableName name) throws ExpressionException.UnknownVariableException {
        Objects.requireNonNull(name, "Имя переменной не должно быть null");

        Double value = variables.get(name);

        if (value == null) {
            throw new ExpressionException.UnknownVariableException(name.value());
        }

        return value;
    }


    public record VariableName(String value) {

        private static final Pattern VALID_NAME =
                Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

        public VariableName {
            Objects.requireNonNull(value, "Имя переменной не должно быть null");

            if (!VALID_NAME.matcher(value).matches()) {
                throw new IllegalArgumentException("Недопустимое имя переменной: " + value);
            }
        }
    }
}