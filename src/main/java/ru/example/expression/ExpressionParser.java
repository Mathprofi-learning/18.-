package ru.example.expression;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ExpressionParser {

    private static final int UNARY_PRIORITY = Expression.BinaryOperator.POWER.priority();

    private final List<Token> tokens;
    private int index;

    public ExpressionParser(String source) throws ExpressionException.SyntaxException {
        this.tokens = List.copyOf(new Lexer(source).tokenize());
    }

    public Expression parse() throws ExpressionException.SyntaxException {
        var result = parseExpression(0);
        expect(TokenType.EOF, "Ожидается конец выражения");
        return result;
    }

    private Expression parseExpression(int minPriority) throws ExpressionException.SyntaxException {
        var left = parsePrefix();

        while (true) {
            var token = peek();
            var operator = toBinaryOperator(token.type());

            if (operator == null || operator.priority() < minPriority) {
                break;
            }

            consume();

            int nextMinPriority = operator.associativity() == Expression.Associativity.LEFT
                    ? operator.priority() + 1
                    : operator.priority();

            var right = parseExpression(nextMinPriority);
            left = new Expression.BinaryExpression(operator, left, right);
        }

        return left;
    }

    private Expression parsePrefix() throws ExpressionException.SyntaxException {
        var token = peek();

        return switch (token.type()) {
            case PLUS -> {
                consume();
                yield parseExpression(UNARY_PRIORITY);
            }

            case MINUS -> {
                consume();
                var operand = parseExpression(UNARY_PRIORITY);
                yield new Expression.UnaryExpression(Expression.UnaryOperator.NEGATE, operand);
            }

            default -> parsePrimary();
        };
    }

    private Expression parsePrimary() throws ExpressionException.SyntaxException {
        var token = peek();

        return switch (token.type()) {
            case NUMBER -> {
                var numberToken = consume();
                yield createNumber(numberToken);
            }

            case IDENTIFIER -> {
                var identifier = consume();

                if (peek().type() == TokenType.LPAREN) {
                    yield parseFunctionCall(identifier);
                }

                yield createVariable(identifier);
            }

            case LPAREN -> {
                consume();
                var inner = parseExpression(0);
                expect(TokenType.RPAREN, "Ожидается ')'");
                yield inner;
            }

            default ->
                    throw new ExpressionException.SyntaxException(
                            "Ожидается число, переменная, функция или '('",
                            token.position()
                    );
        };
    }

    private Expression.NumberExpression createNumber(Token token)
            throws ExpressionException.SyntaxException {

        double value;

        try {
            value = Double.parseDouble(token.text());
        } catch (NumberFormatException ex) {
            throw new ExpressionException.SyntaxException(
                    "Некорректное число: " + token.text(),
                    token.position()
            );
        }

        if (!Double.isFinite(value)) {
            throw new ExpressionException.SyntaxException(
                    "Слишком большое число: " + token.text(),
                    token.position()
            );
        }

        return new Expression.NumberExpression(value);
    }

    private Expression.VariableExpression createVariable(Token identifier)
            throws ExpressionException.SyntaxException {

        try {
            return new Expression.VariableExpression(
                    new EvaluationContext.VariableName(identifier.text())
            );
        } catch (IllegalArgumentException ex) {
            throw new ExpressionException.SyntaxException(
                    "Недопустимое имя переменной: " + identifier.text(),
                    identifier.position()
            );
        }
    }

    private Expression.FunctionCallExpression parseFunctionCall(Token nameToken)
            throws ExpressionException.SyntaxException {

        expect(TokenType.LPAREN, "Ожидается '(' после имени функции");

        var arguments = new ArrayList<Expression>();

        if (peek().type() != TokenType.RPAREN) {
            arguments.add(parseExpression(0));

            while (peek().type() == TokenType.COMMA) {
                consume();
                arguments.add(parseExpression(0));
            }
        }

        expect(TokenType.RPAREN, "Ожидается ')' после аргументов функции");

        var function = Expression.Function.byName(nameToken.text())
                .orElseThrow(() -> new ExpressionException.SyntaxException(
                        "Неизвестная функция: " + nameToken.text(),
                        nameToken.position()
                ));

        if (arguments.size() != function.arity()) {
            throw new ExpressionException.SyntaxException(
                    "Функция " + nameToken.text()
                            + " ожидает аргументов: " + function.arity()
                            + ", получено: " + arguments.size(),
                    nameToken.position()
            );
        }

        return new Expression.FunctionCallExpression(function, arguments);
    }

    private Token peek() {
        return tokens.get(index);
    }

    private Token consume() {
        var current = peek();

        if (current.type() != TokenType.EOF) {
            index++;
        }

        return current;
    }

    private Token expect(TokenType type, String message) throws ExpressionException.SyntaxException {
        var token = peek();

        if (token.type() != type) {
            throw new ExpressionException.SyntaxException(message, token.position());
        }

        return consume();
    }

    private Expression.BinaryOperator toBinaryOperator(TokenType type) {
        return switch (type) {
            case PLUS -> Expression.BinaryOperator.PLUS;
            case MINUS -> Expression.BinaryOperator.MINUS;
            case STAR -> Expression.BinaryOperator.MULTIPLY;
            case SLASH -> Expression.BinaryOperator.DIVIDE;
            case CARET -> Expression.BinaryOperator.POWER;
            default -> null;
        };
    }

    /**
     * Типы лексем.
     */
    private enum TokenType {
        NUMBER,
        IDENTIFIER,
        PLUS,
        MINUS,
        STAR,
        SLASH,
        CARET,
        LPAREN,
        RPAREN,
        COMMA,
        EOF
    }

    private record Token(TokenType type, String text, int position) {
    }

    private static final class Lexer {

        private final String source;
        private int position;
        private final List<Token> tokens = new ArrayList<>();

        Lexer(String source) {
            this.source = Objects.requireNonNull(source, "Исходная строка не должна быть null");
        }

        List<Token> tokenize() throws ExpressionException.SyntaxException {
            while (position < source.length()) {
                char c = source.charAt(position);

                if (Character.isWhitespace(c)) {
                    position++;
                    continue;
                }

                if (isDigit(c)
                        || (c == '.' && position + 1 < source.length()
                        && isDigit(source.charAt(position + 1)))) {
                    readNumber();
                    continue;
                }

                if (isIdentifierStart(c)) {
                    readIdentifier();
                    continue;
                }

                switch (c) {
                    case '+' -> add(TokenType.PLUS, "+");
                    case '-' -> add(TokenType.MINUS, "-");
                    case '*' -> add(TokenType.STAR, "*");
                    case '/' -> add(TokenType.SLASH, "/");
                    case '^' -> add(TokenType.CARET, "^");
                    case '(' -> add(TokenType.LPAREN, "(");
                    case ')' -> add(TokenType.RPAREN, ")");
                    case ',' -> add(TokenType.COMMA, ",");
                    default ->
                            throw new ExpressionException.SyntaxException(
                                    "Неожиданный символ '" + c + "'",
                                    position
                            );
                }

                position++;
            }

            tokens.add(new Token(TokenType.EOF, "", position));
            return tokens;
        }

        private void add(TokenType type, String text) {
            tokens.add(new Token(type, text, position));
        }

        private void readNumber() throws ExpressionException.SyntaxException {
            int start = position;

            readDigits();

            if (position < source.length() && source.charAt(position) == '.') {
                position++;

                if (position >= source.length() || !isDigit(source.charAt(position))) {
                    throw new ExpressionException.SyntaxException(
                            "Некорректное число: ожидается цифра после '.'",
                            start
                    );
                }

                readDigits();
            }

            tokens.add(new Token(TokenType.NUMBER, source.substring(start, position), start));
        }

        private void readDigits() {
            while (position < source.length() && isDigit(source.charAt(position))) {
                position++;
            }
        }

        private void readIdentifier() {
            int start = position;

            while (position < source.length() && isIdentifierPart(source.charAt(position))) {
                position++;
            }

            tokens.add(new Token(TokenType.IDENTIFIER, source.substring(start, position), start));
        }

        private boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }

        private boolean isIdentifierStart(char c) {
            return (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || c == '_';
        }

        private boolean isIdentifierPart(char c) {
            return isIdentifierStart(c) || isDigit(c);
        }
    }
}