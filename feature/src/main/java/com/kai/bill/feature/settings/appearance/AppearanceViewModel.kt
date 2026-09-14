package com.kai.bill.feature.settings.appearance

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kai.bill.core.design.theme.AppPalette
import com.kai.bill.core.design.theme.DarkMode
import com.kai.bill.core.prefs.KaiPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * 外观设置页 ViewModel。
 *
 * 直接驱动 `core:prefs` 的 [KaiPrefs]：改主题不写领域模型，也不走 Repository ——
 * 外观是纯本地偏好，没有「跨设备同步」的需求，套一层架构反而碍事。
 * 写入用细粒度方法（[KaiPrefs.updatePalette] 等）而非整包写回，避免并发切换时互相覆盖。
 *
 * 背景图处理：把用户选中的图片**复制进 App 私有目录**再存路径，而不是直接存
 * content:// URI —— URI 的读取授权可能在设备重启后失效，复制后一劳永逸。
 *
 * @property prefs 配置读写入口
 * @property context 应用上下文，用于读取图片与访问私有目录
 */
@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val prefs: KaiPrefs,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val uiState: StateFlow<AppearanceUiState> = prefs.themeConfig
        .map { AppearanceUiState(config = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = AppearanceUiState()
        )

    /** 切换主题配色 */
    fun onPaletteSelected(palette: AppPalette) {
        viewModelScope.launch { prefs.updatePalette(palette) }
    }

    /** 切换深色模式策略 */
    fun onDarkModeSelected(darkMode: DarkMode) {
        viewModelScope.launch { prefs.updateDarkMode(darkMode) }
    }

    /**
     * 选择或清除自定义背景图。
     *
     * @param uri 用户选中的图片；null 表示恢复默认纯色背景
     */
    fun onBackgroundSelected(uri: Uri?) {
        viewModelScope.launch {
            if (uri == null) {
                withContext(Dispatchers.IO) { clearBackgroundDir() }
                prefs.updateBackgroundUri(null)
                return@launch
            }
            val path = withContext(Dispatchers.IO) { importBackground(uri) }
            if (path != null) {
                prefs.updateBackgroundUri(path)
            }
        }
    }

    /**
     * 调整卡片透明度（0.3~1.0）。
     *
     * 由页面在「松手」时提交，避免拖动过程中高频写 DataStore。
     */
    fun onCardAlphaChanged(alpha: Float) {
        viewModelScope.launch { prefs.updateCardAlpha(alpha.coerceIn(MIN_CARD_ALPHA, 1f)) }
    }

    /**
     * 调整背景图遮罩深浅（0~0.8）。
     *
     * 照片过亮时加大，过暗时调小；同样在「松手」时提交。
     */
    fun onBackgroundDimChanged(dim: Float) {
        viewModelScope.launch { prefs.updateBackgroundDim(dim.coerceIn(0f, MAX_BACKGROUND_DIM)) }
    }

    /**
     * 把选中的图片复制进私有目录并返回文件路径。
     *
     * 文件名带时间戳：路径变化可让 Coil 的缓存自然失效，
     * 否则换图后仍可能显示上一张的缓存。
     */
    private fun importBackground(uri: Uri): String? = runCatching {
        val dir = File(context.filesDir, BACKGROUND_DIR)
        clearBackgroundDir()
        dir.mkdirs()
        val target = File(dir, "bg_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        target.absolutePath
    }.getOrNull()

    /** 清理背景图目录（换图或清除背景时调用，避免私有目录积累垃圾文件） */
    private fun clearBackgroundDir() {
        File(context.filesDir, BACKGROUND_DIR).listFiles()?.forEach { it.delete() }
    }

    private companion object {
        const val BACKGROUND_DIR = "background"
        const val MIN_CARD_ALPHA = 0.3f
        const val MAX_BACKGROUND_DIM = 0.8f
    }
}
