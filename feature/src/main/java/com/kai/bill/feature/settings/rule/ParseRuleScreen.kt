package com.kai.bill.feature.settings.rule

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kai.bill.feature.common.PlaceholderScreen

@Composable
fun ParseRuleScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "解析规则",
        subtitle = "M4 占位",
        modifier = modifier
    )
}

@Composable
fun ParseRuleRoute(modifier: Modifier = Modifier) {
    ParseRuleScreen(modifier = modifier)
}
