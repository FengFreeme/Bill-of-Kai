# 小凯记账 · Bill of Kai

一款 Android 记账 App：**自动解析银行 / 支付类通知与短信完成记账**，配合手动补录、统计图表与预算管理。

---

## 功能

### 自动记账
- 通过 `NotificationListenerService` 监听通知，并可选扫描短信
- 按内置解析规则抽取金额 / 商户 / 时间，去重后落库
- 解析失败或信息不足的记录留待人工确认
- 前台保活服务 + 采集断连预警，尽量避免漏记

### 手动记账
- 数字键盘录入金额，两级分类选择，账户可留空
- 交易时间**精确到分**且可修改，支持补记历史账单
- 可切换「是否计入统计与预算」

### 统计
- 分类构成环形图（主分类 / 子分类两种口径）
- 分类排行、账户维度占比（含「未指定账户」兜底）
- 支出 / 收入趋势折线图、近 5 周对比柱状图
- 时间粒度切换（日 / 周 / 月 / 年）与多条件筛选

### 外观
- 3 套主题配色（青草绿 / 天空蓝 / 淡紫樱花）× 深浅双模
- **自定义背景照片**，并支持调整卡片透明度、底部导航透明度与背景明暗

### 设置
- 分类管理（增删改）、账户管理（归档 / 删除）
- 预算设置、权限与采集引导

---

## 技术栈

| 类别 | 选型 |
|---|---|
| 语言 / 构建 | Kotlin 2.4.20、AGP 9.4.0、Gradle 9.7.1、JDK 17 |
| UI | Jetpack Compose（BOM 2026.08.00）、Material 3 |
| 架构 | 单 Activity + Compose Navigation、MVVM（ViewModel + StateFlow） |
| 依赖注入 | Hilt |
| 本地存储 | Room（账单 / 分类 / 账户 / 预算 / 规则）、DataStore（偏好） |
| 图表 | Vico 3.x |
| 图片加载 | Coil 3.x |
| SDK | minSdk 31 / targetSdk 35 / compileSdk 37 |

---

## 模块结构

```
app                  应用壳：入口、导航装配、DI 绑定（禁止业务逻辑）
├── core:common      通用工具（金额格式化、时间区间等）
├── core:design      设计系统：主题配色、排版、通用组件
├── core:db          Room 数据库、实体、DAO、迁移
├── core:prefs       偏好配置读写（主题、采集状态等）
├── domain           领域层：模型、仓储接口、用例（纯 Kotlin，无 Android 依赖）
├── data             数据层：仓储实现、通知/短信采集与解析
└── feature          所有 UI 页面（按 package 隔离）
```

依赖方向单向收敛：`feature → domain ← data`，`core:*` 提供基础设施；`domain` 不依赖任何 Android 框架。

---

## 构建

需要 **JDK 17 或更高**（AGP 9.4 要求），以及可用的 Android SDK（compileSdk 37）。

```bash
# 编译调试包
./gradlew :app:assembleDebug

# 编译发布包（需要配置签名，见下）
./gradlew :app:assembleRelease
```

产物位于 `app/build/outputs/apk/`。

### 发布签名配置

发布包从项目根目录的 `local.properties`（**已被 .gitignore 排除**）读取签名信息：

```properties
STORE_FILE=/absolute/path/to/your-release-key.jks
STORE_PASSWORD=******
KEY_ALIAS=******
KEY_PASSWORD=******
```

> ⚠️ `local.properties` 与 `*.jks` 均不可提交到版本库。请妥善备份签名密钥 —— 一旦丢失，将无法更新已上架的应用。

---

## 版本号规则

- `versionName`：面向用户的语义化版本，手动维护（当前 `1.1.0`）
- `versionCode`：**由 Git 提交数自动生成**，每次提交自动递增，避免手改遗漏；非 Git 环境下回退为 `1`

---

## 说明

- 本项目为个人学习 / 自用项目，代码与注释均为中文。
- 通知与短信解析规则内置维护，识别不到时可手动补录。
