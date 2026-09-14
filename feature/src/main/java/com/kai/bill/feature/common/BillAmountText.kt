package com.kai.bill.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kai.bill.core.common.money.MoneyFormatter
import com.kai.bill.core.design.component.AmountText
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.AppTheme
import com.kai.bill.core.design.theme.BillOfKaiTheme
import com.kai.bill.core.design.theme.DarkMode
import com.kai.bill.domain.model.Bill
import com.kai.bill.domain.model.BillType
import com.kai.bill.domain.model.SourceType

/**
 * 按账单语义着色并格式化的金额文本。
 *
 * 这是「铁律 3」的落地示例：[AmountText] 属于设计系统，只认识 [androidx.compose.ui.graphics.Color]，
 * 不知道什么是支出 / 收入 / 转账；而「支出用红色、收入用绿色、转账用中性灰」是**业务规则**，
 * 因此这层映射必须放在 `feature` —— 绝不能为了让设计系统认识 [Bill] 而让它反向依赖 `:domain`。
 *
 * @param bill 账单；金额取其 [Bill.amountCents]，符号与颜色由 [Bill.type] 决定
 * @param modifier 外部修饰符
 * @param style 文本样式，默认 `bodyMedium`
 */
@Composable
fun BillAmountText(
    bill: Bill,
    modifier: Modifier = Modifier,
    style: TextStyle = AppTheme.typography.bodyMedium
) {
    // 金额本身恒为正，符号完全由类型推导 —— 若允许负数入账，
    // 同一笔钱就会同时存在「-50 的支出」和「50 的支出」两种表示
    val direction = when (bill.type) {
        BillType.EXPENSE -> -1
        BillType.INCOME -> 1
        BillType.TRANSFER -> 0
    }
    val color = when (bill.type) {
        BillType.EXPENSE -> AppTheme.ext.expense
        BillType.INCOME -> AppTheme.ext.income
        BillType.TRANSFER -> AppTheme.ext.neutral
    }

    AmountText(
        text = MoneyFormatter.signed(cents = bill.amountCents, direction = direction),
        color = color,
        modifier = modifier,
        style = style
    )
}

/** 账单类型的中文名，供列表与详情页复用 */
val BillType.displayName: String
    get() = when (this) {
        BillType.EXPENSE -> "支出"
        BillType.INCOME -> "收入"
        BillType.TRANSFER -> "转账"
    }

/**
 * 预览用账单样本：以固定金额演示三种类型各自的着色与符号。
 *
 * @param type 账单类型，决定支出红 / 收入绿 / 转账灰
 */
private fun previewBill(type: BillType) = Bill(
    amountCents = 25_99L,
    type = type,
    categoryId = 0L,
    accountId = null,
    merchant = null,
    note = null,
    tradeTimeMillis = 0L,
    source = SourceType.MANUAL,
    rawText = null,
    dedupHash = "",
    createdAt = 0L,
    updatedAt = 0L
)

@Composable
private fun PreviewBillAmounts() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BillAmountText(bill = previewBill(BillType.EXPENSE))
        BillAmountText(bill = previewBill(BillType.INCOME))
        BillAmountText(bill = previewBill(BillType.TRANSFER))
    }
}

@Preview(name = "金额-浅色", showBackground = true)
@Composable
private fun BillAmountTextLightPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.LIGHT) {
        PreviewBillAmounts()
    }
}

@Preview(name = "金额-深色", showBackground = true)
@Composable
private fun BillAmountTextDarkPreview() {
    BillOfKaiTheme(palette = AppPalette.MINT, darkMode = DarkMode.DARK) {
        PreviewBillAmounts()
    }
}
