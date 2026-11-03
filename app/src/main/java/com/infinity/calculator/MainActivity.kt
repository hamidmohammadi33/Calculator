package com.infinity.calculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

private val divisionContext = MathContext(1000, RoundingMode.HALF_UP)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("calculator_settings", MODE_PRIVATE)
        val savedTheme = prefs.getString("theme", "system") ?: "system"
        val savedGrouping = prefs.getBoolean("grouping", true)

        setContent {
            var theme by remember { mutableStateOf(savedTheme) }
            var grouping by remember { mutableStateOf(savedGrouping) }

            val dark = when (theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            InfinityTheme(dark) {
                CalculatorScreen(
                    theme = theme,
                    grouping = grouping,
                    onThemeChange = {
                        theme = it
                        prefs.edit().putString("theme", it).apply()
                    },
                    onGroupingChange = {
                        grouping = it
                        prefs.edit().putBoolean("grouping", it).apply()
                    }
                )
            }
        }
    }
}

@Composable
private fun InfinityTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) {
        darkColorScheme(
            primary = Color(0xFFB8A0FF),
            background = Color(0xFF101116),
            surface = Color(0xFF191A22),
            secondaryContainer = Color(0xFF343044)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF6750A4),
            background = Color(0xFFF7F5FC),
            surface = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFE9E1F8)
        )
    }

    MaterialTheme(colorScheme = colors, content = content)
}

private fun formatNumber(value: String, grouping: Boolean): String {
    if (!grouping || value.isEmpty()) return value

    // Format only integer parts, leaving operators and decimal portions untouched.
    val numberPattern = Regex("""\d+(\.\d*)?""")
    return numberPattern.replace(value) { match ->
        val raw = match.value
        val parts = raw.split(".", limit = 2)
        val integer = parts[0]
        val formatted = integer.reversed().chunked(3).joinToString(",").reversed()
        if (parts.size == 2) "$formatted.${parts[1]}" else formatted
    }
}

private class ExpressionParser(private val source: String) {
    private var position = 0

    private fun skipSpaces() {
        while (position < source.length && source[position].isWhitespace()) position++
    }

    private fun consume(char: Char): Boolean {
        skipSpaces()
        if (position < source.length && source[position] == char) {
            position++
            return true
        }
        return false
    }

    fun parse(): BigDecimal {
        val value = expression()
        skipSpaces()
        require(position == source.length) { "عبارت نامعتبر است" }
        return value
    }

    private fun expression(): BigDecimal {
        var value = term()
        while (true) {
            value = when {
                consume('+') -> value.add(term())
                consume('-') -> value.subtract(term())
                else -> return value
            }
        }
    }

    private fun term(): BigDecimal {
        var value = power()
        while (true) {
            value = when {
                consume('*') -> value.multiply(power())
                consume('/') -> {
                    val divisor = power()
                    require(divisor.compareTo(BigDecimal.ZERO) != 0) {
                        "تقسیم بر صفر مجاز نیست"
                    }
                    value.divide(divisor, divisionContext)
                }
                else -> return value
            }
        }
    }

    private fun power(): BigDecimal {
        val base = unary()
        if (!consume('^')) return base

        val exponent = power().toBigIntegerExact()
        require(exponent.abs() <= BigInteger.valueOf(10000)) { "توان بیش از حد بزرگ است" }
        val n = exponent.toInt()

        if (n >= 0) return base.pow(n)
        require(base.compareTo(BigDecimal.ZERO) != 0) { "تقسیم بر صفر مجاز نیست" }
        return BigDecimal.ONE.divide(base.pow(-n), divisionContext)
    }

    private fun unary(): BigDecimal {
        if (consume('+')) return unary()
        if (consume('-')) return unary()

        var value = primary()
        while (consume('%')) {
            value = value.divide(BigDecimal("100"), divisionContext)
        }
        return value
    }

    private fun primary(): BigDecimal {
        skipSpaces()

        if (source.startsWith("sqrt(", position)) {
            position += 4
            require(consume('('))
            val number = expression()
            require(consume(')')) { "پرانتز بسته نشده است" }
            require(number.signum() >= 0) { "جذر عدد منفی تعریف نشده است" }
            return sqrt(number)
        }

        if (consume('(')) {
            val value = expression()
            require(consume(')')) { "پرانتز بسته نشده است" }
            return value
        }

        return readNumber()
    }

    private fun readNumber(): BigDecimal {
        skipSpaces()
        val start = position
        var dots = 0

        while (position < source.length) {
            val c = source[position]
            when {
                c.isDigit() -> position++
                c == '.' && dots == 0 -> {
                    dots++
                    position++
                }
                else -> break
            }
        }

        require(position > start) { "عدد نامعتبر است" }
        return BigDecimal(source.substring(start, position))
    }

    private fun sqrt(value: BigDecimal): BigDecimal {
        if (value.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO

        var x = if (value >= BigDecimal.ONE) value else BigDecimal.ONE
        repeat(4000) {
            val next = x.add(value.divide(x, divisionContext))
                .divide(BigDecimal("2"), divisionContext)
            if (next.compareTo(x) == 0) return next
            x = next
        }
        return x
    }
}

private fun calculate(expression: String): String {
    require(expression.isNotBlank()) { "عبارتی وارد نشده است" }
    val result = ExpressionParser(expression).parse().stripTrailingZeros()
    return if (result.compareTo(BigDecimal.ZERO) == 0) "0" else result.toPlainString()
}

@Composable
private fun CalculatorScreen(
    theme: String,
    grouping: Boolean,
    onThemeChange: (String) -> Unit,
    onGroupingChange: (Boolean) -> Unit
) {
    var expression by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var evaluated by remember { mutableStateOf(false) }

    val textColor = MaterialTheme.colorScheme.onBackground
    val rows = listOf(
        listOf("AC", "DEL", "(", ")"),
        listOf("%", "^", "√", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "−"),
        listOf("1", "2", "3", "+"),
        listOf("±", "0", ".", "=")
    )

    fun append(value: String) {
        if (evaluated && (value.firstOrNull()?.isDigit() == true || value == ".")) {
            expression = ""
        }
        expression += value
        evaluated = false
        error = ""
        result = try {
            calculate(expression)
        } catch (_: Exception) {
            ""
        }
    }

    fun evaluate() {
        try {
            result = calculate(expression)
            expression = result
            evaluated = true
            error = ""
        } catch (e: Exception) {
            error = e.message ?: "خطا در محاسبه"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Infinity Calculator",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Box {
                TextButton(onClick = { showSettings = true }) { Text("تنظیمات ⚙") }
                DropdownMenu(
                    expanded = showSettings,
                    onDismissRequest = { showSettings = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("تم روشن") },
                        onClick = { onThemeChange("light"); showSettings = false }
                    )
                    DropdownMenuItem(
                        text = { Text("تم تاریک") },
                        onClick = { onThemeChange("dark"); showSettings = false }
                    )
                    DropdownMenuItem(
                        text = { Text("مطابق سیستم") },
                        onClick = { onThemeChange("system"); showSettings = false }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Text(if (grouping) "✓ جداکننده سه‌رقمی" else "جداکننده سه‌رقمی")
                        },
                        onClick = { onGroupingChange(!grouping); showSettings = false }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(28.dp)
                )
                .padding(20.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = formatNumber(expression.ifEmpty { "0" }, grouping),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                color = textColor.copy(alpha = 0.75f),
                fontSize = 27.sp,
                textAlign = TextAlign.End,
                maxLines = 5,
                overflow = TextOverflow.Clip
            )

            Spacer(Modifier.height(18.dp))

            Text(
                text = formatNumber(result, grouping).ifEmpty { " " },
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                maxLines = 5,
                overflow = TextOverflow.Clip
            )

            if (error.isNotEmpty()) {
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { key ->
                    val isOperator = key in listOf("+", "−", "×", "÷", "^", "=", "%")
                    val isUtility = key in listOf("AC", "DEL", "√", "(", ")", "±")
                    val buttonColor = when {
                        key == "=" -> MaterialTheme.colorScheme.primary
                        isOperator -> MaterialTheme.colorScheme.secondaryContainer
                        isUtility -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.surface
                    }
                    val foreground = if (key == "=") {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        textColor
                    }

                    Button(
                        onClick = {
                            when (key) {
                                "AC" -> {
                                    expression = ""
                                    result = ""
                                    error = ""
                                    evaluated = false
                                }
                                "DEL" -> {
                                    if (expression.isNotEmpty()) expression = expression.dropLast(1)
                                    result = try {
                                        if (expression.isBlank()) "" else calculate(expression)
                                    } catch (_: Exception) { "" }
                                    evaluated = false
                                    error = ""
                                }
                                "=" -> evaluate()
                                "√" -> append("sqrt(")
                                "×" -> append("*")
                                "÷" -> append("/")
                                "−" -> append("-")
                                "±" -> {
                                    expression = if (expression.startsWith("-")) {
                                        expression.drop(1)
                                    } else {
                                        "-$expression"
                                    }
                                    result = try {
                                        calculate(expression)
                                    } catch (_: Exception) { "" }
                                    evaluated = false
                                }
                                else -> append(key)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(0.82f),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonColor,
                            contentColor = foreground
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            key,
                            fontSize = if (key == "=") 25.sp else 21.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
