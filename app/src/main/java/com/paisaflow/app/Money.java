package com.paisaflow.app;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

final class Money {
    private static final Locale INDIA = new Locale("en", "IN");
    private Money() {}

    static long parseMinor(String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("Enter an amount");
        BigDecimal decimal = new BigDecimal(value.trim().replace(",", ""));
        if (decimal.signum() <= 0) throw new IllegalArgumentException("Amount must be greater than zero");
        return decimal.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    static String format(long minor) {
        NumberFormat format = NumberFormat.getCurrencyInstance(INDIA);
        format.setCurrency(Currency.getInstance("INR"));
        format.setMinimumFractionDigits(minor % 100 == 0 ? 0 : 2);
        format.setMaximumFractionDigits(2);
        return format.format(BigDecimal.valueOf(minor, 2));
    }

    static String inputValue(long minor) {
        return BigDecimal.valueOf(minor, 2).stripTrailingZeros().toPlainString();
    }

    static String formatRounded(long minor) {
        NumberFormat format = NumberFormat.getCurrencyInstance(INDIA);
        format.setCurrency(Currency.getInstance("INR"));
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(0);
        return format.format(BigDecimal.valueOf(minor, 2).setScale(0, RoundingMode.HALF_UP));
    }
}
