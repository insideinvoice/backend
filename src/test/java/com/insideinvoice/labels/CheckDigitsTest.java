package com.insideinvoice.labels;

import com.insideinvoice.labels.util.CheckDigits;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CheckDigitsTest {

    @Test
    void gtin13ClassicExampleHasCheckDigit1() {
        // Well-known GS1 example: 400638133393 + check 1 => 4006381333931
        assertThat(CheckDigits.checkDigit("400638133393")).isEqualTo(1);
        assertThat(CheckDigits.isValidGtin("4006381333931")).isTrue();
    }

    @Test
    void upcAClassicExampleHasCheckDigit2() {
        assertThat(CheckDigits.checkDigit("03600029145")).isEqualTo(2);
        assertThat(CheckDigits.isValidGtin("036000291452")).isTrue();
    }

    @Test
    void gtinBuilderAppendsCheckDigit() {
        assertThat(CheckDigits.gtin("400638133393")).isEqualTo("4006381333931");
        assertThat(CheckDigits.gtin("03600029145")).isEqualTo("036000291452");
        assertThat(CheckDigits.gtin("0123456")).hasSize(8);
    }

    @Test
    void sscc18RoundTripIsValid() {
        String sscc = CheckDigits.sscc18("12345678901234501");
        assertThat(sscc).hasSize(18);
        assertThat(CheckDigits.isValidSscc18(sscc)).isTrue();
        // flipping the check digit must invalidate it
        char flipped = sscc.charAt(17) == '0' ? '1' : '0';
        assertThat(CheckDigits.isValidSscc18(sscc.substring(0, 17) + flipped)).isFalse();
    }

    @Test
    void incrementsOfSerialChangeCheckDigit() {
        String a = CheckDigits.sscc18("12345678901234501");
        String b = CheckDigits.sscc18("12345678901234502");
        assertThat(a).isNotEqualTo(b);
        assertThat(CheckDigits.isValidSscc18(a)).isTrue();
        assertThat(CheckDigits.isValidSscc18(b)).isTrue();
    }

    @Test
    void invalidInputsAreRejected() {
        assertThatThrownBy(() -> CheckDigits.checkDigit("12A")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CheckDigits.checkDigit("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CheckDigits.sscc18("123")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CheckDigits.gtin("1234")).isInstanceOf(IllegalArgumentException.class);
        assertThat(CheckDigits.isValidSscc18("not-a-number")).isFalse();
        assertThat(CheckDigits.isValidSscc18(null)).isFalse();
        assertThat(CheckDigits.isValidGtin("4006381333932")).isFalse();
    }
}
