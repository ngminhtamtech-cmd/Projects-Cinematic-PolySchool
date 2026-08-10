package com.mycompany.website.ban.ve.xem.phim.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Locale-stable number formatting used by JSP EL functions. */
public final class DisplayFormat {

    private DisplayFormat() {
    }

    /** Formats integral business values with Vietnamese thousands separators. */
    public static String whole(Object value) {
        return format(value, "#,##0");
    }

    /** Formats values such as discount percentages with at most one decimal place. */
    public static String decimal(Object value) {
        return format(value, "#,##0.#");
    }

    private static String format(Object value, String pattern) {
        BigDecimal number = toBigDecimal(value);
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        DecimalFormat formatter = new DecimalFormat(pattern, symbols);
        formatter.setParseBigDecimal(true);
        return formatter.format(number);
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        String text = value.toString().trim();
        return text.isEmpty() ? BigDecimal.ZERO : new BigDecimal(text);
    }
}
