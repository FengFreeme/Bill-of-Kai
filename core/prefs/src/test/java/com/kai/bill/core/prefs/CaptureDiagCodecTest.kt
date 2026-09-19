package com.kai.bill.core.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 诊断历史编解码：这段逻辑写坏一次就会让整段历史错位，且只会在真机上被用户发现，
 * 所以把「条数上限、保序、脏字符」三条不变量钉在单测里。
 */
class CaptureDiagCodecTest {

    private fun entry(at: Long, result: String = "SAVED", raw: String = "支付宝 支付成功 ¥12.00") =
        CaptureDiagEntry(result = result, raw = raw, atMillis = at)

    @Test
    fun `空历史解码为空列表`() {
        assertTrue(CaptureDiagCodec.decode(null).isEmpty())
        assertTrue(CaptureDiagCodec.decode("").isEmpty())
    }

    @Test
    fun `单条写入后原样解出`() {
        val encoded = CaptureDiagCodec.encode(entry(at = 1_700_000_000_000L), existing = null)

        val decoded = CaptureDiagCodec.decode(encoded)

        assertEquals(1, decoded.size)
        assertEquals(1_700_000_000_000L, decoded[0].atMillis)
        assertEquals("SAVED", decoded[0].result)
        assertEquals("支付宝 支付成功 ¥12.00", decoded[0].raw)
    }

    @Test
    fun `新的在前且保序`() {
        var encoded = CaptureDiagCodec.encode(entry(at = 1L, result = "SAVED"), null)
        encoded = CaptureDiagCodec.encode(entry(at = 2L, result = "PENDING"), encoded)
        encoded = CaptureDiagCodec.encode(entry(at = 3L, result = "DUPLICATE"), encoded)

        val decoded = CaptureDiagCodec.decode(encoded)

        assertEquals(listOf(3L, 2L, 1L), decoded.map { it.atMillis })
        assertEquals(listOf("DUPLICATE", "PENDING", "SAVED"), decoded.map { it.result })
    }

    @Test
    fun `超过上限时只保留最近 N 条`() {
        var encoded: String? = null
        repeat(CAPTURE_DIAG_LIMIT + 20) { index ->
            encoded = CaptureDiagCodec.encode(entry(at = index.toLong()), encoded)
        }

        val decoded = CaptureDiagCodec.decode(encoded)

        assertEquals(CAPTURE_DIAG_LIMIT, decoded.size)
        // 保留的是最新的那批：最大值 = 总条数 - 1，最小值正好差出一个上限
        assertEquals((CAPTURE_DIAG_LIMIT + 19).toLong(), decoded.first().atMillis)
        assertEquals(20L, decoded.last().atMillis)
    }

    @Test
    fun `原文里的分隔符不会打乱后续记录`() {
        // 通知原文正常不含控制字符，但用户可改的备注 / 异常数据可能带入，写入前必须被替换
        val dirty = "支付宝 支付成功 ¥12.00\u001E\u001F\n第二行"
        var encoded = CaptureDiagCodec.encode(entry(at = 1L, raw = "正常"), null)
        encoded = CaptureDiagCodec.encode(entry(at = 2L, raw = dirty), encoded)
        encoded = CaptureDiagCodec.encode(entry(at = 3L, raw = "又一笔"), encoded)

        val decoded = CaptureDiagCodec.decode(encoded)

        assertEquals(3, decoded.size)
        assertEquals(listOf(3L, 2L, 1L), decoded.map { it.atMillis })
        assertEquals("又一笔", decoded[0].raw)
        assertEquals("正常", decoded[2].raw)
        assertTrue("分隔符必须被替换掉", !decoded[1].raw.contains('\u001E') && !decoded[1].raw.contains('\u001F'))
    }

    @Test
    fun `原文超过上限被截断`() {
        val encoded = CaptureDiagCodec.encode(entry(at = 1L, raw = "账".repeat(CAPTURE_RAW_MAX_CHARS + 50)), null)

        assertEquals(CAPTURE_RAW_MAX_CHARS, CaptureDiagCodec.decode(encoded)[0].raw.length)
    }

    @Test
    fun `损坏的记录被跳过而不是整段丢弃`() {
        val good = CaptureDiagCodec.encode(entry(at = 7L), null)
        // 掺入两段脏数据：一段缺字段、一段时间戳不是数字
        val broken = listOf("只有一段", "abc\u001FSAVED\u001F原文", good).joinToString("\u001E")

        val decoded = CaptureDiagCodec.decode(broken)

        assertEquals(1, decoded.size)
        assertEquals(7L, decoded[0].atMillis)
    }
}
