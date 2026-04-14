package org.textrpg.application.game.attribute

/**
 * 公式引擎
 *
 * 解析并求值属性计算公式。支持四则运算、括号、一元负号和属性引用。
 * 二级属性的 formula 字段（如 `"strength * 2 + 50"`）通过此引擎求值。
 *
 * 支持的语法：
 * - 数字字面量：`10`、`3.14`、`0.5`
 * - 属性引用：`strength`、`max_hp`、`physical_attack`（字母、数字、下划线）
 * - 四则运算：`+`、`-`、`*`、`/`
 * - 括号：`(expression)`
 * - 一元负号：`-expression`
 *
 * 运算符优先级（从低到高）：
 * 1. 加法 / 减法
 * 2. 乘法 / 除法
 * 3. 一元负号
 * 4. 括号 / 字面量 / 属性引用
 *
 * 使用示例：
 * ```kotlin
 * val damage = FormulaEngine.evaluate("strength * 2 + 50") { key ->
 *     attributeContainer.getValue(key)
 * }
 * ```
 */
object FormulaEngine {

    /**
     * 求值公式表达式
     *
     * @param formula 公式字符串
     * @param resolver 属性值解析器，接收属性 key 返回当前值
     * @return 计算结果
     * @throws IllegalStateException 当公式语法错误时
     */
    fun evaluate(formula: String, resolver: (String) -> Double): Double {
        val tokens = Tokenizer(formula).tokenize()
        return Parser(tokens, resolver).parse()
    }

    /**
     * 提取公式中引用的所有属性标识符
     *
     * 用于构建属性依赖图，实现二级属性的自动重算。
     *
     * @param formula 公式字符串
     * @return 公式中出现的所有属性 key 集合
     */
    fun extractReferences(formula: String): Set<String> {
        return Tokenizer(formula).tokenize()
            .filter { it.type == TokenType.IDENTIFIER }
            .map { it.value }
            .toSet()
    }

    // ======================== 词法分析 ========================

    private enum class TokenType {
        NUMBER,      // 数字字面量
        IDENTIFIER,  // 属性标识符
        PLUS,        // +
        MINUS,       // -
        STAR,        // *
        SLASH,       // /
        LPAREN,      // (
        RPAREN,      // )
        EOF          // 输入结束
    }

    private data class Token(val type: TokenType, val value: String)

    /**
     * 词法分析器：将公式字符串拆分为 Token 序列
     */
    private class Tokenizer(private val input: String) {
        private var pos = 0

        fun tokenize(): List<Token> {
            val tokens = mutableListOf<Token>()
            while (pos < input.length) {
                when {
                    input[pos].isWhitespace() -> pos++
                    input[pos].isDigit() || input[pos] == '.' -> tokens.add(readNumber())
                    input[pos].isLetter() || input[pos] == '_' -> tokens.add(readIdentifier())
                    else -> tokens.add(readOperator())
                }
            }
            tokens.add(Token(TokenType.EOF, ""))
            return tokens
        }

        /**
         * 读取数字字面量（支持小数点）
         */
        private fun readNumber(): Token {
            val start = pos
            var hasDot = false
            while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) {
                if (input[pos] == '.') {
                    if (hasDot) break // 第二个小数点，停止
                    hasDot = true
                }
                pos++
            }
            return Token(TokenType.NUMBER, input.substring(start, pos))
        }

        /**
         * 读取标识符（属性引用）：字母、数字、下划线
         */
        private fun readIdentifier(): Token {
            val start = pos
            while (pos < input.length && (input[pos].isLetterOrDigit() || input[pos] == '_')) {
                pos++
            }
            return Token(TokenType.IDENTIFIER, input.substring(start, pos))
        }

        /**
         * 读取运算符
         */
        private fun readOperator(): Token {
            val c = input[pos++]
            return when (c) {
                '+' -> Token(TokenType.PLUS, "+")
                '-' -> Token(TokenType.MINUS, "-")
                '*' -> Token(TokenType.STAR, "*")
                '/' -> Token(TokenType.SLASH, "/")
                '(' -> Token(TokenType.LPAREN, "(")
                ')' -> Token(TokenType.RPAREN, ")")
                else -> error("Formula syntax error: unexpected character '$c' at position ${pos - 1} in: $input")
            }
        }
    }

    // ======================== 语法分析（递归下降） ========================

    /**
     * 递归下降解析器
     *
     * 文法定义：
     * ```
     * expression = term (('+' | '-') term)*
     * term       = unary (('*' | '/') unary)*
     * unary      = '-' unary | primary
     * primary    = NUMBER | IDENTIFIER | '(' expression ')'
     * ```
     */
    private class Parser(
        private val tokens: List<Token>,
        private val resolver: (String) -> Double
    ) {
        private var pos = 0

        fun parse(): Double {
            val result = expression()
            if (current().type != TokenType.EOF) {
                error("Formula syntax error: unexpected token '${current().value}' after complete expression")
            }
            return result
        }

        /** expression = term (('+' | '-') term)* */
        private fun expression(): Double {
            var left = term()
            while (current().type == TokenType.PLUS || current().type == TokenType.MINUS) {
                val op = advance()
                val right = term()
                left = when (op.type) {
                    TokenType.PLUS -> left + right
                    TokenType.MINUS -> left - right
                    else -> left
                }
            }
            return left
        }

        /** term = unary (('*' | '/') unary)* */
        private fun term(): Double {
            var left = unary()
            while (current().type == TokenType.STAR || current().type == TokenType.SLASH) {
                val op = advance()
                val right = unary()
                left = when (op.type) {
                    TokenType.STAR -> left * right
                    TokenType.SLASH -> if (right != 0.0) left / right else 0.0
                    else -> left
                }
            }
            return left
        }

        /** unary = '-' unary | primary */
        private fun unary(): Double {
            if (current().type == TokenType.MINUS) {
                advance()
                return -unary()
            }
            return primary()
        }

        /** primary = NUMBER | IDENTIFIER | '(' expression ')' */
        private fun primary(): Double {
            return when (current().type) {
                TokenType.NUMBER -> {
                    val token = advance()
                    token.value.toDoubleOrNull()
                        ?: error("Formula syntax error: invalid number '${token.value}'")
                }
                TokenType.IDENTIFIER -> {
                    val token = advance()
                    resolver(token.value)
                }
                TokenType.LPAREN -> {
                    advance() // consume '('
                    val result = expression()
                    expect(TokenType.RPAREN)
                    result
                }
                else -> error("Formula syntax error: unexpected token '${current().value}'")
            }
        }

        private fun current(): Token = tokens[pos]

        private fun advance(): Token = tokens[pos++]

        private fun expect(type: TokenType) {
            if (current().type != type) {
                error("Formula syntax error: expected ${type.name} but got '${current().value}'")
            }
            advance()
        }
    }
}
