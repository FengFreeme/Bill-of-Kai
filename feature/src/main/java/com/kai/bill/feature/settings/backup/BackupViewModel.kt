package com.kai.bill.feature.settings.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kai.bill.domain.model.ImportSummary
import com.kai.bill.domain.repository.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

/**
 * 数据备份页 ViewModel。
 *
 * 文件的读写走 SAF（调用方传入用户选中的 `content://` URI），因此本类需要 Context；
 * 备份内容的组装与解析则完全下沉到 [BackupRepository]（data 层）。
 *
 * @property backupRepository 备份导出/导入入口
 * @property context 用于按 URI 读写用户选择的文件
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    /** 建议的导出文件名：带日期，避免多次导出互相覆盖 */
    fun suggestedFileName(): String = "xiaokai-backup-${LocalDate.now()}.json"

    /**
     * 导出到用户选定的位置。
     *
     * @param uri SAF 返回的目标文件 URI
     */
    fun exportTo(uri: Uri) {
        if (_uiState.value.isWorking) return
        _uiState.value = BackupUiState(isWorking = true)
        viewModelScope.launch {
            val result = runCatching {
                val json = backupRepository.export()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("无法写入所选位置")
                }
            }
            _uiState.value = result.fold(
                onSuccess = { BackupUiState(message = "导出成功，文件已保存") },
                onFailure = { BackupUiState(message = it.message ?: "导出失败", isError = true) }
            )
        }
    }

    /**
     * 从用户选定的文件导入。
     *
     * 同一份文件可以反复导入：重复账单会被 `dedupHash` 唯一索引挡下并计入「跳过」。
     *
     * @param uri SAF 返回的源文件 URI
     */
    fun importFrom(uri: Uri) {
        if (_uiState.value.isWorking) return
        _uiState.value = BackupUiState(isWorking = true)
        viewModelScope.launch {
            val result = runCatching {
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    } ?: error("无法读取所选文件")
                }
                backupRepository.import(json)
            }
            _uiState.value = result.fold(
                onSuccess = { BackupUiState(message = summaryText(it)) },
                onFailure = { BackupUiState(message = it.message ?: "导入失败", isError = true) }
            )
        }
    }

    /** 用户点掉提示后清空 */
    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private fun summaryText(summary: ImportSummary): String = buildString {
        append("导入完成：新增 ${summary.billsImported} 笔")
        if (summary.billsSkipped > 0) {
            append("，跳过 ${summary.billsSkipped} 笔（已存在或分类缺失）")
        }
        if (summary.categoriesCreated > 0) append("，新建分类 ${summary.categoriesCreated} 个")
        if (summary.accountsCreated > 0) append("，新建账户 ${summary.accountsCreated} 个")
        if (summary.budgetsRestored > 0) append("，恢复预算 ${summary.budgetsRestored} 条")
    }
}
