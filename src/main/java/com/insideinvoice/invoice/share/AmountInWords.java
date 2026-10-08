package com.insideinvoice.invoice.share;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Amount-in-words for the public invoice PDF. Mirrors the wording produced by the
 * frontend template helper ({@code numberToWords} in InvoicePDF.jsx) so the spoken
 * amount on the PDF matches what the on-screen invoice says: Indian numbering up to
 * "Lakh" (no "Crore" word, e.g. 1,00,00,000 reads "One Hundred Lakh"), rupees first,
 * paise after "and", terminal "Only".
 */
public final class AmountInWords {

    private static final String[] ONES = {
            "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
            "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"
    };
    private static final String[] TENS = {
            "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    };

    private AmountInWords() {
    }

    public static String rupees(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        if (value.signum() < 0) {
            value = BigDecimal.ZERO;
        }
        long whole = value.longValue();
        long paise = value.subtract(BigDecimal.valueOf(whole)).movePointRight(2).longValue();

        StringBuilder sb = new StringBuilder("Rupees ").append(convert(whole));
        if (paise > 0) {
            sb.append(" and ").append(convert(paise)).append(" Paise");
        }
        return sb.append(" Only").toString();
    }

    private static String convert(long n) {
        if (n == 0) {
            return "Zero";
        }
        if (n < 20) {
            return ONES[(int) n];
        }
        if (n < 100) {
            return TENS[(int) (n / 10)] + (n % 10 != 0 ? " " + ONES[(int) (n % 10)] : "");
        }
        if (n < 1000) {
            return ONES[(int) (n / 100)] + " Hundred" + (n % 100 != 0 ? " " + convert(n % 100) : "");
        }
        if (n < 100000) {
            return convert(n / 1000) + " Thousand" + (n % 1000 != 0 ? " " + convert(n % 1000) : "");
        }
        return convert(n / 100000) + " Lakh" + (n % 100000 != 0 ? " " + convert(n % 100000) : "");
    }
}
