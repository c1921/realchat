# AGENTS.md

## Git 提交规范

提交信息使用 Conventional Commits，确保日志可读、便于生成变更记录。

### 提交粒度

- 单次提交只做一类变更（功能/修复/文档）
- 提交前先整理改动，避免混入无关格式化或临时调试
- 每个提交应可构建、可运行，方便回滚
- 大改动拆分为多个可审查的小提交

### 提交信息格式

```
<type>: <summary>

[body]

[footer]
```

- type 建议：feat、fix、docs、refactor、test、chore、build（其他场景按需）
- summary 使用中文、动词开头，长度 ≤ 50 字，不加句号
- 需要时在正文补充动机、影响或迁移方式

### 提交类型

| 类型          | 说明                             |
| ------------- | -------------------------------- |
| `init`     | 项目初始化                       |
| `feat`     | 新功能                           |
| `fix`      | 错误修复                         |
| `docs`     | 文档变更                         |
| `style`    | 代码格式化（不影响代码逻辑）     |
| `refactor` | 代码重构（不新增功能或修复错误） |
| `perf`     | 性能优化                         |
| `test`     | 测试相关                         |
| `build`    | 构建系统或外部依赖               |
| `ci`       | CI配置相关                       |
| `chore`    | 构建过程或辅助工具的变动         |
| `revert`   | 撤销提交                         |

### 破坏性变更

- 在 type 后添加 `!`，或在正文写明 `BREAKING CHANGE: ...`
- 明确写出受影响范围与升级指引

### 提交流程

- 本项目为 Android/Gradle 项目，提交前校验以 Gradle 命令为准
- `git status` 确认改动范围
- `git add <files>` 仅添加相关文件
- `git diff --cached --stat` 复核本次提交内容
- 代码改动默认至少执行 `./gradlew lintDebug`
- 涉及业务逻辑、状态、仓储或网络时执行 `./gradlew testDebugUnitTest`
- 涉及 Compose UI 或 `androidTest` 变更时执行 `./gradlew compileDebugAndroidTestKotlin`
- 文档或纯说明类改动可按影响范围酌情减少检查
- `git commit -m "..."` 完成提交
- `git push` 后发起 PR（如需）

## Android 模拟器联调

### 基本原则

- agent 不依赖 Android Studio GUI 控制设备，优先通过 `adb` 连接已启动的模拟器
- `adb` 与 `emulator` 路径优先从仓库根目录 `local.properties` 的 `sdk.dir` 推导，不假设系统已配置 PATH
- 只有在设备真正启动完成后再运行 `connectedDebugAndroidTest`，不要只看模拟器窗口是否已经出现

### 解析 SDK、adb 与 emulator 路径

```powershell
$raw = (Get-Content local.properties | Where-Object { $_ -match '^sdk\.dir=' } | ForEach-Object { $_ -replace '^sdk\.dir=', '' })
$sdkDir = $raw.Replace('\\\\', '\').Replace('\:', ':')
$adb = Join-Path $sdkDir 'platform-tools\adb.exe'
$emulator = Join-Path $sdkDir 'emulator\emulator.exe'
```

### 检查在线设备与可用 AVD

```powershell
& $adb devices -l
& $emulator -list-avds
```

- 如果 `adb devices -l` 已看到 `device` 状态的模拟器，直接复用，不要重复启动
- 如果没有在线设备，再从 `-list-avds` 结果中选择 AVD 启动

### 启动模拟器

```powershell
Start-Process -FilePath $emulator -ArgumentList '-avd', 'Pixel_8'
```

- 本机已验证可用的 AVD 名称为 `Pixel_8`
- 启动后等待设备注册到 `adb devices -l`

### 判断设备是否真正可用

```powershell
& $adb shell getprop sys.boot_completed
& $adb shell getprop init.svc.bootanim
& $adb shell settings get secure user_setup_complete
& $adb shell wm size
```

- 期望结果：
  - `sys.boot_completed = 1`
  - `init.svc.bootanim = stopped`
  - `user_setup_complete = 1`
- 如需解锁屏幕：

```powershell
& $adb shell input keyevent 82
```

### 运行本项目常用测试命令

```powershell
./gradlew compileDebugAndroidTestKotlin
./gradlew connectedDebugAndroidTest
./gradlew testDebugUnitTest
./gradlew lintDebug
```

- 涉及设备侧测试时优先使用 `./gradlew connectedDebugAndroidTest`
- 只验证 `androidTest` 是否能编译时使用 `./gradlew compileDebugAndroidTestKotlin`

### 手动拉起应用

```powershell
& $adb shell am start -n io.github.c1921.realchat/.MainActivity
```

### 当前已验证经验

- `emulator -list-avds` 可见 `Pixel_8`
- `adb devices -l` 可见在线设备 `emulator-5554`
- 当前设备 Android 版本为 `15`
- `./gradlew connectedDebugAndroidTest` 已在模拟器上跑通并通过 5 个测试

### 常见故障排查

- `adb devices -l` 没有设备：
  - 先确认 Android Studio 模拟器是否已启动
  - 再执行 `& $emulator -list-avds`
- 设备状态为 `offline`：

```powershell
& $adb kill-server
& $adb start-server
& $adb devices -l
```

- 已看到设备但测试仍失败：
  - 先重新检查 `sys.boot_completed`、`bootanim`、`user_setup_complete`
  - 不要只根据模拟器窗口已经出现就认为设备可用
