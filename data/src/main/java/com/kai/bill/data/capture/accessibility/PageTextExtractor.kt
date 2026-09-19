package com.kai.bill.data.capture.accessibility

import android.view.accessibility.AccessibilityNodeInfo

/**
 * 无障碍节点树 → 单行可见文本。
 *
 * 采到的是「用户此刻看到的这一屏字」，它会被送去做分类匹配（可能含商户名、商品名），
 * 因此有三条硬约束：
 * - **必须有界**：节点数 / 深度 / 文本长度三重上限。页面可能是无限滚动列表，
 *   也可能被异常应用构造出深树，不设上限就是给自己埋 OOM 与卡顿；
 * - **必须去重**：同一段文字在父子节点上重复出现是常态，不去重会让文本膨胀数倍；
 * - **只读不写**：本类不落盘、不写日志，文本只在内存里活到信号窗口过期（见 `CategorySignalStore`）。
 */
object PageTextExtractor {

    /**
     * 节点数上限。
     *
     * 取 800 而不是更小的数：服务开了 `flagIncludeNotImportantViews` 之后，
     * 原本被剪掉的装饰性节点都会回来，节点数成倍增长 ——
     * 上限定得太小会在读到金额之前就止损（真机上正是这样丢掉金额的）。
     * 800 仍远低于「一次遍历拖垮主线程」的量级。
     */
    internal const val MAX_NODES = 800

    /** 深度上限：防止异常深树把遍历拖长 */
    internal const val MAX_DEPTH = 30

    /** 文本长度上限：够分类匹配即可，截断也顺带缩小隐私暴露面 */
    internal const val MAX_CHARS = 2_000

    private val WHITESPACE = Regex("\\s+")

    /**
     * 从活动窗口的根节点提取可见文本。
     *
     * @param root 活动窗口根节点；为 null（无窗口 / 无权限）时返回 null
     * @return 去重、压缩空白并截断后的单行文本；无有效文本时返回 null
     */
    fun extract(root: AccessibilityNodeInfo?): String? {
        if (root == null) return null
        return normalizeText(collectTexts(root))
    }

    /**
     * 多窗口合并抽取：把多个根节点（如微信账单页被拆成的「顶部卡片」与「底部按钮」两窗口）
     * 的文本一并收集、去重、归一化，避免只抽到其中一块而漏掉金额 / 商户名。
     *
     * @param roots 需要合并的窗口根节点集合（去重用引用相等）
     * @return 合并后的单行文本；全部为空时返回 null
     */
    fun extract(roots: Collection<AccessibilityNodeInfo>): String? {
        if (roots.isEmpty()) return null
        val parts = roots.flatMap { collectTexts(it) }
        return normalizeText(parts)
    }

    /**
     * 把收集到的文本片段归一化为单行。
     *
     * 抽成纯函数是为了能在 JVM 上直接单测 —— [AccessibilityNodeInfo] 是 Android 类型，
     * 遍历部分无法脱离设备验证，但「去重 / 压缩 / 截断」这三条容易写错且影响匹配结果，
     * 必须能脱离设备钉住。
     *
     * @param parts 节点原始文本片段（可能含空白、换行、重复项）
     * @return 单行文本；无有效内容返回 null
     */
    internal fun normalizeText(parts: List<String>): String? {
        val joined = parts
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(" ")
            .replace(WHITESPACE, " ")
            .trim()

        if (joined.isEmpty()) return null
        return joined.take(MAX_CHARS)
    }

    /**
     * 广度优先遍历节点树，收集 [AccessibilityNodeInfo.text] 与 `contentDescription`。
     *
     * 用队列而不是递归：深树递归有栈溢出风险，而 BFS 的顺序也更接近「页面上从上到下」。
     * 两者都可能含商户名 —— 前者是界面上的字，后者是无障碍朗读用的语义描述。
     */
    private fun collectTexts(root: AccessibilityNodeInfo): List<String> {
        val texts = LinkedHashSet<String>()
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.addLast(root to 0)

        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val (node, depth) = queue.removeFirst()
            visited++

            node.text?.toString()?.let { texts += it }
            node.contentDescription?.toString()?.let { texts += it }

            if (depth >= MAX_DEPTH) continue
            for (index in 0 until node.childCount) {
                // getChild 可能返回 null（节点已失效 / 跨进程读取失败），必须判空
                node.getChild(index)?.let { queue.addLast(it to depth + 1) }
            }
        }
        return texts.toList()
    }
}
