package com.kai.bill.domain.calculator

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MoneyTextTest {

    @Test
    fun `textToCents_empty_returnsZero`() {
        assertThat(MoneyText.textToCents("")).isEqualTo(0L)
    }

    @Test
    fun `textToCents_integerYuan`() {
        assertThat(MoneyText.textToCents("12")).isEqualTo(1200L)
    }

    @Test
    fun `textToCents_oneDecimal`() {
        assertThat(MoneyText.textToCents("12.3")).isEqualTo(1230L)
    }

    @Test
    fun `textToCents_twoDecimals`() {
        assertThat(MoneyText.textToCents("12.34")).isEqualTo(1234L)
    }

    @Test
    fun `centsToText_padsTwoDecimals`() {
        assertThat(MoneyText.centsToText(1200L)).isEqualTo("12.00")
        assertThat(MoneyText.centsToText(1234L)).isEqualTo("12.34")
        assertThat(MoneyText.centsToText(5L)).isEqualTo("0.05")
    }
}
