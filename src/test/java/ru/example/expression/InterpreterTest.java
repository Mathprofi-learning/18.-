package ru.example.expression;

package ru.example.expression;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты базового уровня интерпретатора.
 */
class InterpreterTest {

    private static final double EPS = 1e-9;

    private static final EvaluationContext CONTEXT = createContextWithXY();

    @Test
    void numberLiteralIsEvaluated() {
        assertEvaluation("42", 42.0);
        assertEvaluation("2.5", 2.5);
        assertEvaluation(".5", 0.5);
    }

    @Test
    void additiveOperationsAreLeftAssociative() {
        assertEvaluation("1 - 2 - 3", -4.0);
        assertEvaluation("2 + 3 + 5", 10.0);
    }

    @Test
    void multiplicationHasHigherPriorityThanAddition() {
        assertEvaluation("2 + 3 * 4", 14.0);
        assertEvaluation("2 * 3 + 4", 10.0);
    }

    @Test
    void parenthesesChangePriority() {
        assertEvaluation("(2 + 3) * 4", 20.0);
        assertEvaluation("2 * (3 + 4)", 14.0);
    }

    @Test
    void unaryMinusIsSupported() {
        assertEvaluation("-5 + 2", -3.0);
        assertEvaluation("--5", 5.0);
        assertEvaluation("-(1 + 1)", -2.0);
    }

    @Test
    void unaryMinusHasLowerPriorityThanPower() {
        assertEvaluation("-2 ^ 2", -4.0);
        assertEvaluation("2 ^ -2", 0.25);
    }

    @Test
    void powerIsRightAssociative() {
        assertEvaluation("2 ^ 3 ^ 2", 512.0);
    }

    @Test
    void divisionProducesRealResult() {
        assertEvaluation("10 / 4", 2.5);
    }

    @Test
    void divisionByZeroThrows() {
        assertDivisionByZero("1 / 0");
    }

    @Test
    void divisionByNegativeZeroThrows() {
        assertDivisionByZero("1 / -0");
    }

    @Test
    void unknownVariableThrows() {
        assertUnknownVariable("z + 1", "z");
    }

    @Test
    void functionsAreEvaluated() {
        assertEvaluation("min(1, 2)", 1.0);
        assertEvaluation("max(1, 2)", 2.0);
        assertEvaluation("abs(-5)", 5.0);
        assertEvaluation("max(abs(-3), min(2, 7))", 3.0);
    }

    @Test
    void variablesFromContextAreUsed() {
        assertEvaluation("x + y * 2", 8.0);
    }

    @Test
    void syntaxErrorPositionIsReportedForIncompleteExpression() {
        var exception = syntaxError("1 +");

        assertEquals(3, exception.getPosition());
        assertTrue(exception.getMessage().contains("позиция"));
    }

    @Test
    void syntaxErrorPositionIsReportedForExtraToken() {
        var exception = syntaxError("1 2");

        assertEquals(2, exception.getPosition());
    }

    @Test
    void functionWithWrongArityIsSyntaxError() {
        syntaxError("min(1)");
        syntaxError("abs(1, 2)");
    }

    @Test
    void unknownFunctionIsSyntaxError() {
        syntaxError("foo(1)");
    }

    @Test
    void invalidCharacterIsSyntaxError() {
        syntaxError("2 $");
    }

    @Test
    void nonFinitePowerIsEvaluationError() {
        var expression = assertDoesNotThrow(() -> parse("0 ^ -1"));

        assertThrows(
                ExpressionException.EvaluationException.class,
                () -> expression.evaluate(CONTEXT)
        );
    }

    @Test
    void variableNameEqualsAndHashCodeContract() {
        var first = variable("x");
        var second = variable("x");
        var other = variable("y");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, other);
        assertNotEquals(null, first);
        assertEquals(first, first);
    }

    @Test
    void variableNameRejectsInvalidNames() {
        assertInvalidVariableName("1x");
        assertInvalidVariableName("x y");
        assertInvalidVariableName("");

        assertThrows(
                NullPointerException.class,
                () -> variable(null)
        );
    }

    @Test
    void contextCopiesExternalMap() {
        var map = new HashMap<EvaluationContext.VariableName, Double>();
        map.put(variable("x"), 1.0);

        var context = new EvaluationContext(map);
        map.put(variable("y"), 2.0);

        assertThrows(
                ExpressionException.UnknownVariableException.class,
                () -> context.valueOf(variable("y"))
        );
    }

    @Test
    void contextVariablesAreUnmodifiable() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> CONTEXT.variables().put(variable("z"), 0.0)
        );
    }

    @Test
    void functionCallCopiesArguments() {
        var arguments = new ArrayList<Expression>();
        arguments.add(new Expression.NumberExpression(-1.0));

        var call = new Expression.FunctionCallExpression(Expression.Function.ABS, arguments);
        arguments.add(new Expression.NumberExpression(2.0));

        assertEquals(1, call.arguments().size());

        assertThrows(
                UnsupportedOperationException.class,
                () -> call.arguments().add(new Expression.NumberExpression(0.0))
        );
    }

    @Test
    void binaryOperatorMetadataIsConsistent() {
        assertTrue(Expression.BinaryOperator.POWER.priority()
                > Expression.BinaryOperator.MULTIPLY.priority());

        assertTrue(Expression.BinaryOperator.MULTIPLY.priority()
                > Expression.BinaryOperator.PLUS.priority());

        assertEquals(
                Expression.Associativity.RIGHT,
                Expression.BinaryOperator.POWER.associativity()
        );

        assertEquals(
                Expression.Associativity.LEFT,
                Expression.BinaryOperator.PLUS.associativity()
        );
    }

    private static EvaluationContext createContextWithXY() {
        return new EvaluationContext(Map.of(
                variable("x"), 2.0,
                variable("y"), 3.0
        ));
    }

    private static EvaluationContext.VariableName variable(String name) {
        return new EvaluationContext.VariableName(name);
    }

    private static Expression parse(String source) throws ExpressionException {
        return new ExpressionParser(source).parse();
    }

    private static double eval(String source) throws ExpressionException {
        return eval(source, CONTEXT);
    }

    private static double eval(String source, EvaluationContext context) throws ExpressionException {
        return parse(source).evaluate(context);
    }

    private static void assertEvaluation(String source, double expected) {
        var actual = assertDoesNotThrow(() -> eval(source));
        assertEquals(expected, actual, EPS);
    }

    private static void assertDivisionByZero(String source) {
        var expression = assertDoesNotThrow(() -> parse(source));

        assertThrows(
                ExpressionException.DivisionByZeroException.class,
                () -> expression.evaluate(CONTEXT)
        );
    }

    private static void assertUnknownVariable(String source, String expectedVariableName) {
        var exception = assertThrows(
                ExpressionException.UnknownVariableException.class,
                () -> eval(source, EvaluationContext.empty())
        );

        assertEquals(expectedVariableName, exception.getVariableName());
    }

    private static ExpressionException.SyntaxException syntaxError(String source) {
        return assertThrows(
                ExpressionException.SyntaxException.class,
                () -> parse(source)
        );
    }

    private static void assertInvalidVariableName(String name) {
        assertThrows(
                IllegalArgumentException.class,
                () -> variable(name)
        );
    }
}