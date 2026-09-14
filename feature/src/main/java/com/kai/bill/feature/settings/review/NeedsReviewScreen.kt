package com.kai.bill.feature.settings.review

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kai.bill.feature.common.PlaceholderScreen

/** 待审核 / 未解析列表。M4/M6 落地。 */
@Composable
fun NeedsReviewScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "待审核记录",
        subtitle = "M4/M6 占位：NEEDS_REVIEW / PARSE_FAILED",
        modifier = modifier
    )
}

@Composable
fun NeedsReviewRoute(modifier: Modifier = Modifier) {
    NeedsReviewScreen(modifier = modifier)
}
