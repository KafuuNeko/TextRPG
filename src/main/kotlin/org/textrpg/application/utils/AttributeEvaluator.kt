package org.textrpg.application.utils

/**
 * 属性表达式计算器
 *
 * 支持基本的加减乘除与括号，并能够将变量映射为具体数值。
 * 例如：可以计算 "str * 2.0 + agi * 0.5"
 */
object AttributeEvaluator {

    /**
     * 计算包含变量的数学表达式
     *
     * @param expression 表达式字符串（例如 "str * 2 + agi"）
     * @param valueProvider 变量值提供函数，传入变量名并返回其对应的数值
     * @return 计算结果，如果遇到错误则返回 0.0
     */
    fun evaluate(expression: String, valueProvider: (String) -> Double): Double {
        return try {
            evalMath(expression, valueProvider)
        } catch (e: Exception) {
            println("Error evaluating expression [$expression]: ${e.message}")
            0.0
        }
    }

    /**
     * 通过递归下降解析器计算算数表达式
     */
    private fun evalMath(str: String, valueProvider: (String) -> Double): Double {
        return object : Any() {
            var pos = -1
            var ch = 0

            fun nextChar() {
                ch = if (++pos < str.length) str[pos].code else -1
            }

            fun eat(charToEat: Int): Boolean {
                while (ch == ' '.code) nextChar()
                if (ch == charToEat) {
                    nextChar()
                    return true
                }
                return false
            }

            fun parse(): Double {
                nextChar()
                val x = parseExpression()
                if (pos < str.length) throw RuntimeException("Unexpected: " + ch.toChar())
                return x
            }

            fun parseExpression(): Double {
                var x = parseTerm()
                while (true) {
                    if (eat('+'.code)) x += parseTerm() // 加法
                    else if (eat('-'.code)) x -= parseTerm() // 减法
                    else return x
                }
            }

            fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    if (eat('*'.code)) x *= parseFactor() // 乘法
                    else if (eat('/'.code)) x /= parseFactor() // 除法
                    else return x
                }
            }

            fun parseFactor(): Double {
                if (eat('+'.code)) return parseFactor() // 正号
                if (eat('-'.code)) return -parseFactor() // 负号

                var x: Double
                val startPos = this.pos
                if (eat('('.code)) { // 括号
                    x = parseExpression()
                    eat(')'.code)
                } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) { // 数字
                    while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                    x = str.substring(startPos, this.pos).toDouble()
                } else if (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code || ch == '_'.code) { // 未知变量处理
                    while (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code || ch == '_'.code) nextChar()
                    val func = str.substring(startPos, this.pos)
                    x = valueProvider(func)
                } else {
                    throw RuntimeException("Unexpected offset: " + ch.toChar())
                }

                return x
            }
        }.parse()
    }
}