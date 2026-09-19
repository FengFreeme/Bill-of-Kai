package com.kai.bill.core.db.entity

/**
 * 待确认原因（数据库存储形态）。
 *
 * 与 `domain` 层的同名枚举一一对应：`core:db` 按依赖矩阵不允许依赖 `:domain`，
 * 两侧各留一份镜像、转换职责在 `data/mapper`（`BillType` 已有同样先例）。
 *
 * 为什么要存进表而不是运行时再算：用户看到待确认列表时，通知原文可能已经因为
 * 「同 dedupHash 只留一条」被覆盖，而「当初为什么判不了」正是他判断这笔账的唯一线索。
 */
enum class PendingReason {

    /** 完全没命中任何方向词：连是支出还是收入都判不出来 */
    DIRECTION_UNKNOWN,

    /** 自己的钱搬家：转出 / 提现 / 取现 / 代付 */
    SELF_TRANSFER,

    /** 红包：收 / 发、算不算人情往来因人而异 */
    GIFT,

    /** 泛化入账：入账 / 转入 / 存入 / 到账，可能是收入也可能只是账户互转 */
    GENERIC_INBOUND
}
