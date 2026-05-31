package com.selavu.app.util

import kotlin.math.min

sealed class AmountValidationResult {
    object Valid : AmountValidationResult()
    data class Error(val message: String) : AmountValidationResult()
}

fun validateAmount(input: String): AmountValidationResult {
    if (input.isBlank()) {
        return AmountValidationResult.Error("Amount is required")
    }
    val amount = input.toDoubleOrNull()
    if (amount == null) {
        return AmountValidationResult.Error("Enter a valid number")
    }
    if (amount < 0) {
        return AmountValidationResult.Error("Amount cannot be negative")
    }
    if (amount <= 0) {
        return AmountValidationResult.Error("Amount must be greater than 0")
    }
    return AmountValidationResult.Valid
}

// Spec regex: A-Z, a-z, 0-9, space, ., ,, ', -, _
private val ALLOWED_CHARS_REGEX = Regex("^[A-Za-z0-9 .,'\\-_]+$")

fun isItemNameValid(name: String): Boolean {
    return ALLOWED_CHARS_REGEX.matches(name)
}

fun isPurelyNumerical(name: String): Boolean {
    return name.all { it.isDigit() }
}

fun validateItemName(name: String, existingNames: List<String>): String? {
    if (name.isBlank()) return "Item name cannot be empty"

    if (!isItemNameValid(name)) {
        return "Invalid characters"
    }

    if (name.equals("Others", ignoreCase = true)) {
        return "'Others' is a reserved name"
    }

    if (existingNames.any { it.equals(name, ignoreCase = true) }) {
        return "This item already exists"
    }

    return null
}

/**
 * Calculate Levenshtein (edit) distance between two strings.
 * Returns the minimum number of single-character edits (insertions, deletions, substitutions)
 * needed to transform one string into the other.
 */
fun levenshteinDistance(a: String, b: String): Int {
    val m = a.length
    val n = b.length
    if (m == 0) return n
    if (n == 0) return m

    var prev = IntArray(n + 1) { it }
    var curr = IntArray(n + 1)

    for (i in 1..m) {
        curr[0] = i
        for (j in 1..n) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            curr[j] = minOf(
                curr[j - 1] + 1,       // insertion
                prev[j] + 1,           // deletion
                prev[j - 1] + cost     // substitution
            )
        }
        val temp = prev
        prev = curr
        curr = temp
    }
    return prev[n]
}

// Notes validation: max 50 chars, alphanumeric plus .,' " - @ : ! =
private val NOTES_ALLOWED_CHARS_REGEX = Regex("^[A-Za-z0-9 .,'\"\\-@:!=]+$")

fun validateNotes(notes: String): String? {
    if (notes.length > 50) {
        return "Notes must be 50 characters or less"
    }
    if (!NOTES_ALLOWED_CHARS_REGEX.matches(notes)) {
        return "Invalid characters in notes"
    }
    return null
}
