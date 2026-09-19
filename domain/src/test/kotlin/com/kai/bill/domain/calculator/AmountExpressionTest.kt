package com.kai.bill.domain.calculator

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AmountExpressionTest {

    @Test
    fun `toCents_plainAmount_keepsMoneyTextBehaviour`() {
        assertThat(AmountExpression.toCents("12.34")).isEqualTo(1234L)
    }

    @Test
    fun `toCents_addition`() {
        assertThat(AmountExpression.toCents("12+8")).isEqualTo(2000L)
    }

    @Test
    fun `toCents_subtraction`() {
        assertThat(AmountExpression.toCents("100-30.5")).isEqualTo(6950L)
    }

    @Test
    fun `toCents_multipleTerms_leftToRight`() {
        assertThat(AmountExpression.toCents("10+20-5+0.5")).isEqualTo(2550L)
    }

    @Test
    fun `toCents_trailingOperator_countsZeroTerm`() {
        assertThat(AmountExpression.toCents("12+")).isEqualTo(1200L)
    }

    @Test
    fun `toCents_leadingOperator_countsZeroTerm`() {
        assertThat(AmountExpression.toCents("-5+8")).isEqualTo(300L)
    }

    @Test
    fun `toCents_negativeResult_isAllowed`() {
        assertThat(AmountExpression.toCents("12-20")).isEqualTo(-800L)
    }

    @Test
    fun `toCents_brokenTerm_countsZero`() {
        assertThat(AmountExpression.toCents("12+.")).isEqualTo(1200L)
    }

    @Test
    fun `toCents_emptyAndBlankAreZero`() {
        assertThat(AmountExpression.toCents("")).isEqualTo(0L)
        assertThat(AmountExpression.toCents("+")).isEqualTo(0L)
    }

    @Test
    fun `hasOperator_detectsPlusAndMinus`() {
        assertThat(AmountExpression.hasOperator("12.34")).isFalse()
        assertThat(AmountExpression.hasOperator("12+8")).isTrue()
        assertThat(AmountExpression.hasOperator("12-8")).isTrue()
    }
}
