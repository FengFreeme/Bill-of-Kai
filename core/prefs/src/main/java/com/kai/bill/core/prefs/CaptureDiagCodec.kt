package com.kai.bill.core.prefs

/**
 * 采集诊断历史的紧凑编码 —— 把一串 [CaptureDiagEntry] 与 DataStore 里的单个字符串互相转换。
 *
 * 为什么不用 JSON：core:prefs 没有引入序列化库，为「50 条诊断」这一个用途加依赖不划算。
 * 为什么不用 stringSet：诊断必须保序（新的在前），而 Set 无序，还得靠时间戳排序再截断。
 *
 * 格式：`atMillis<US>result<US>raw`，多条之间用 <RS> 连接（<US>/<RS> 为 ASCII 控制字符 0x1F / 0x1E）。
 *
 * 单独成对象而不是塞进 [KaiPrefs] 的私有方法：这段「分隔符 + 截断」的编解码是本功能唯一
 * 有状态风险的地方（写坏一次会让整段历史错位），需要能直接单测。
 */
internal object CaptureDiagCodec {

    private const val FIELD_SEP = '\u001F'
    private const val RECORD_SEP = '\u001E'

    /**
     * 追加一条诊断并截断到 [CAPTURE_DIAG_LIMIT] 条（新的在前）。
     *
     * @param existing DataStore 里原有的编码串，为空表示还没有历史
     */
    fun encode(newEntry: CaptureDiagEntry, existing: String?): String {
        val head = encodeEntry(newEntry)
        val tail = decode(existing).take(CAPTURE_DIAG_LIMIT - 1)
        return (listOf(head) + tail.map { encodeEntry(it) }).joinToString(RECORD_SEP.toString())
    }

    /**
     * 解码历史。**任何一段解不出来就跳过那一段**，不抛异常：
     * 诊断只是排查用的旁路数据，为它让整个采集状态流崩溃不值得。
     */
    fun decode(encoded: String?): List<CaptureDiagEntry> {
        if (encoded.isNullOrEmpty()) return emptyList()
        return encoded.split(RECORD_SEP).mapNotNull { record ->
            val parts = record.split(FIELD_SEP)
            if (parts.size < 3) return@mapNotNull null
            val atMillis = parts[0].toLongOrNull() ?: return@mapNotNull null
            // 原文理论上不含分隔符（写入前已替换），这里再把剩余的段拼回去：
            // 万一遇到被手工改过的数据，也不至于把原文截断
            CaptureDiagEntry(
                result = parts[1],
                raw = parts.drop(2).joinToString(FIELD_SEP.toString()),
                atMillis = atMillis
            )
        }
    }

    private fun encodeEntry(entry: CaptureDiagEntry): String = buildString {
        append(entry.atMillis)
        append(FIELD_SEP)
        append(entry.result)
        append(FIELD_SEP)
        // 原文里的分隔符在写入前替换掉：宁可少一个不可见字符，也不能让一条脏数据
        // 把整个历史列表的解析带偏（原文被拆成两截，后面所有记录跟着错位）
        append(
            entry.raw
                .take(CAPTURE_RAW_MAX_CHARS)
                .replace(RECORD_SEP, ' ')
                .replace(FIELD_SEP, ' ')
        )
    }
}
