package com.cashcanvas.statement.parser;

/**
 * One bad row in a statement. {@code reason} must describe the problem
 * ("amount is not a number") without echoing the row's narration or account number.
 */
public record RowError(int rowNumber, String reason) {
}
