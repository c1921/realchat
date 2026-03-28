# RealChat

RealChat 是一个单模块 Android 聊天应用，用于和 OpenAI 兼容接口进行长期角色对话。项目以 Jetpack Compose 构建界面，使用 `ChatViewModel` 统一编排状态与业务流程，结合 Room、DataStore 和 OkHttp 完成会话持久化、配置管理与网络请求。

这份 README 主要面向后续维护者，重点说明项目的工作流程、核心模块职责，以及新增功能时应该从哪里切入。

## 项目概览

当前项目支持的核心能力：

- 配置多个 AI Provider：`DeepSeek`、`OpenAI`、`OpenAI Compatible`
- 管理用户 Persona
- 创建、编辑、复制、导入、导出角色卡
- 创建多会话并持久化消息历史
- 基于角色卡快照进行长期对话
- 可选启用主动消息、导演系统、记忆摘要、情绪更新
- 通过开发者模式查看部分运行时辅助信息

技术栈：

- UI：Jetpack Compose + Material 3
- 状态管理：`ViewModel` + `StateFlow`
- 本地存储：Room、DataStore
- 网络：OkHttp
- 序列化：kotlinx.serialization
- 测试：JUnit、MockWebServer、Compose UI Test、Room Instrumentation Test

## 目录与模块职责

项目是单模块结构，主要代码集中在 `app/src/main/java/io/github/c1921/realchat`。

```text
app/src/main/java/io/github/c1921/realchat
├─ MainActivity.kt
├─ model/
├─ ui/
├─ data/chat/
├─ data/character/
├─ data/settings/
└─ data/agent/
```

### 1. 入口与 UI 层

#### `MainActivity`

- 应用入口
- 创建 `ChatViewModel`
- 收集 `uiState`
- 将所有 UI 事件回调绑定到 `ChatViewModel`

#### `ui/RealChatApp.kt`

- 根组合函数
- 根据 `MainUiState` 决定当前显示：
  - 会话列表
  - 聊天详情
  - 角色卡页
  - 设置页
- 负责底部导航
- 负责角色卡导入导出的系统文件选择器

#### `ui/ChatViewModel.kt`

这是项目的业务中枢。

主要职责：

- 维护整个应用的 `MainUiState`
- 订阅 DataStore 和 Room 数据流并同步到 UI
- 管理会话选择、聊天发送、草稿保存
- 管理角色卡编辑、导入导出
- 管理 Provider 配置与 Persona 保存
- 编排主动消息、导演分析、记忆摘要、情绪更新

可以把它看成一个“总调度器”：

- UI 不直接访问数据库或网络
- Repository 不直接控制界面
- 各类 Agent 能力也通过 `ChatViewModel` 串联进入主流程

#### `ui/ConversationScreen.kt`

- 会话首页：展示会话列表、新建会话、删除会话
- 聊天详情页：展示消息列表、情绪摘要、输入框、发送按钮
- 开发者模式下展示主动消息倒计时和发送计数

#### `ui/CharacterScreen.kt`

- 展示角色卡列表
- 提供新建、编辑、复制、删除、导入、导出入口
- 负责角色卡表单编辑

#### `ui/SettingsScreen.kt`

- 配置 Provider、API Key、模型、Base URL
- 配置用户 Persona
- 配置主动消息、导演系统、记忆摘要
- 配置开发者模式

### 2. `model/`：领域模型

`model` 包定义了项目里的核心数据结构，用于串联 UI、存储和网络层。

关键模型包括：

- `ProviderType`：Provider 类型枚举
- `ProviderConfig`：单个 Provider 的配置，包含 `apiKey`、`model`、`baseUrl`
- `UserPersona`：用户在提示词中的身份信息
- `CharacterCard`：完整角色卡
- `CharacterCardSnapshot`：会话创建时固化的角色快照
- `Conversation`：会话元数据，包含草稿、记忆摘要、情绪状态
- `ChatMessage`：聊天消息，角色为 `System`、`User`、`Assistant`
- `AgentSettings`：主动消息、导演、记忆的总配置
- `EmotionState`：角色对用户的当前情绪状态
- `DirectorGuidance`：导演系统生成的回复指导

其中最关键的一点是：

- 角色卡是可编辑的
- 会话使用的是 `CharacterCardSnapshot`

这意味着修改角色卡不会回写到旧会话，从而保证历史对话的人设连续性。

### 3. `data/chat/`：聊天、持久化与请求链路

#### `AppDatabase.kt`

- 定义 Room 数据库
- 管理三张核心表：
  - `character_cards`
  - `conversations`
  - `conversation_messages`
- 提供 DAO 和数据库迁移

#### `ConversationRepository.kt`

- 抽象会话与消息的读写接口
- `RoomConversationRepository` 负责实际的 Room 实现

主要职责：

- 创建会话
- 观察会话列表
- 观察某个会话的完整消息流
- 更新草稿
- 保存发送成功的用户/助手消息
- 追加主动消息
- 删除会话
- 保存情绪状态
- 用摘要替换旧消息

#### `PromptComposer.kt`

负责把对话请求拼装成最终发送给模型的消息数组。

拼装顺序大致为：

1. system prompt
2. 角色设定块
3. 用户 Persona 块
4. 情绪块
5. 示例对话块
6. 历史消息
7. 导演指示块（可选）
8. 尾部约束指令
9. 主动消息 catalyst（可选）

它是项目里最重要的“提示词装配器”。

#### `ChatProvider.kt` 与 `OpenAiCompatibleChatProvider.kt`

- `ChatProvider` 定义发送聊天请求的抽象接口
- `OpenAiCompatibleChatProvider` 负责具体 HTTP 请求

当前实现特点：

- 统一向 `/chat/completions` 发请求
- 使用 `Authorization: Bearer <apiKey>`
- 兼容 OpenAI 风格请求与响应结构
- 对错误响应做统一消息提取

如果未来要新增新的后端接入方式，这里是主要扩展点之一。

### 4. `data/character/`：角色卡管理

#### `CharacterCardRepository.kt`

- 提供角色卡的增删改查、复制、导入、导出
- `RoomCharacterCardRepository` 使用 Room 落库
- 当数据库为空时，会自动创建一张默认种子角色卡 `通用助手`

#### `CharacterCardJson.kt`

- 负责角色卡 JSON 的解析与生成
- 支持 V1 和 `chara_card_v2`
- 兼容 SillyTavern / TavernAI 风格字段
- 尽量保留未知字段和 `extensions`，避免导入再导出时信息丢失

角色卡模块的设计目标不是“只存一个名字和开场白”，而是尽可能保留角色设定、提示词、扩展字段和导入导出兼容性。

### 5. `data/settings/`：应用设置

#### `AppPreferencesRepository.kt`

- 基于 DataStore 持久化设置
- 保存当前选中的 Provider
- 保存每个 Provider 各自的配置草稿
- 保存 Persona
- 保存当前选中的会话 ID
- 保存 Agent 设置
- 保存开发者模式开关

这里有一个很实用的设计：

- 不同 Provider 各自保留自己的草稿配置
- 切换 Provider 时，表单自动切到对应 Provider 的草稿

这样在 DeepSeek 和 OpenAI 之间切换时，不会互相覆盖参数。

### 6. `data/agent/`：Agent 增强能力

这个包里的能力都不是单独工作的，而是由 `ChatViewModel` 在主发送流程中按条件调用。

#### `ProactiveMessagingController`

- 根据设置的最短/最长间隔随机安排下次触发时间
- 到点后回调 `ChatViewModel`
- 用户回复后会重置主动消息计数

#### `DirectorService`

- 根据最近消息和当前情绪分析“下一条回复应该怎么写”
- 输出结构化 guidance
- guidance 会被加入 `PromptComposer` 结果中

#### `EmotionUpdater`

- 在一轮对话完成后，根据最近消息重新估计角色情绪
- 结果会写回会话并同步到 UI 顶部状态栏

#### `MemorySummarizer`

- 当消息数超过阈值时，对旧消息进行摘要压缩
- 保留最近若干条消息
- 把旧历史替换成一条 `[记忆摘要] ...` 系统消息

这几个模块共享一个特点：

- 接口层抽象清晰
- 当前默认实现都复用了 OpenAI Compatible 的聊天接口

## 核心工作流程

下面按实际运行顺序说明系统是如何工作的。

### 1. 应用启动流程

应用启动后，`MainActivity` 创建 `ChatViewModel`，随后 `ChatViewModel` 会做三件事：

1. `bootstrap()`
2. `observePreferences()`
3. `observeCharacterCards()` 与 `observeConversations()`

具体效果：

- 若没有角色卡，自动创建默认角色卡 `通用助手`
- 若没有会话，自动基于种子角色卡创建默认会话
- 持续监听 DataStore 中的设置变化
- 持续监听 Room 中的角色卡、会话和消息变化
- 根据上次保存的 `selectedConversationId` 自动恢复当前会话

因此 UI 并不是手动拉取数据，而是完全由底层数据流推动刷新。

### 2. 设置保存流程

用户在设置页修改 Provider、模型、Base URL、Persona 或 Agent 设置后，点击“保存设置”会触发：

1. 表单内容归一化
2. 校验 Base URL 是否能拼出合法 `/chat/completions` 地址
3. 组装 `ProviderConfig`、`UserPersona`、`AgentSettings`
4. 写入 DataStore
5. `observePreferences()` 收到变化后回推最新状态到 UI

这里要注意两点：

- `ProviderConfig` 的有效性要求 `apiKey`、`model`、`baseUrl` 都不为空
- 主动消息、导演系统、记忆摘要等运行开关也都来自这里

### 3. 角色卡工作流

角色卡页支持六类操作：

- 新建
- 编辑
- 复制
- 删除
- 导入
- 导出

其中：

- 导入会读取本地 JSON，并解析为 `CharacterCard`
- 导出会把当前角色卡重新生成为 `chara_card_v2` JSON
- 删除最后一张角色卡后，仓库会重新补一张种子角色卡，避免系统进入“无角色可用”状态

### 4. 新建会话流程

用户在会话页点击“新建会话”后：

1. 选择一个角色卡
2. `ConversationRepository.createConversation()` 创建会话
3. 把角色卡转换为 `CharacterCardSnapshot` 存入会话
4. 如果角色卡存在 `firstMes` 或备选开场白，会自动插入第一条助手消息

所以一个会话从创建开始，就已经携带了稳定的人设快照和初始上下文。

### 5. 普通消息发送流程

这是最重要的主链路。

#### 入口

用户在聊天详情页输入内容并点击发送，`ChatViewModel.sendMessage()` 会先检查：

- 当前是否已经在发送中
- Provider 配置是否有效
- 是否存在当前会话
- 草稿是否为空

#### 发送前处理

通过校验后，`sendMessageInternal()` 会：

1. 把当前草稿加入历史消息，形成待发送上下文
2. 在 UI 中做乐观更新：
   - 立即显示用户消息
   - 清空输入框
   - 标记 `isSending = true`
3. 如果启用了记忆摘要，先检查是否需要压缩旧消息
4. 如果启用了导演系统，先调用 `DirectorService` 获取 guidance

#### 提示词组装

随后 `PromptComposer` 会根据：

- 角色快照
- 用户 Persona
- 历史消息
- 导演 guidance
- 主动消息指令（如果本轮是主动触发）

拼出最终消息列表。

#### 网络请求

`OpenAiCompatibleChatProvider` 会把消息列表发往：

`<baseUrl>/chat/completions`

请求成功后返回一条助手消息。

#### 落库与后处理

请求成功后：

- 普通发送：保存用户消息和助手消息
- 主动消息：只保存助手消息
- 更新会话的最后活跃时间
- 若本轮是用户主动发送，则重置主动消息计数
- 若有导演 guidance，在开发者模式下可显示到对应回复旁边
- 异步调用 `EmotionUpdater`，更新情绪状态并写回数据库

如果请求失败或落库失败，UI 会恢复可编辑状态并显示错误信息。

### 6. 主动消息流程

主动消息复用主发送链路，但不再伪装成一条用户输入。

流程如下：

1. `ProactiveMessagingController` 按配置定时检查
2. 到达触发时间后，先调用 proactive 专用导演分析
3. 导演判断当前应当：
   - 延续上一轮未结束的话题
   - 开启一个新话题
   - 暂时等待用户，不主动发送
4. 如果导演决定发送，`PromptComposer` 会追加一条 `system` 级主动消息指令，明确“当前没有用户新输入，需要角色主动发起消息”
5. 主对话模型生成一条助手消息并写回会话
6. 如果导演决定等待用户，则本轮不会请求主对话模型，并暂停后续主动消息，直到用户再次真实回复

因此主动消息和普通消息的主要区别仅在于：

- 是否先经过 proactive 导演决策
- 主动轮次不会新增任何伪装成 `user` 的 catalyst
- 是否保存用户消息不同

### 7. 记忆摘要流程

如果启用了记忆摘要，并且消息数量超过阈值：

1. 取最近 `keepRecentCount` 条消息保留
2. 把更早的消息交给 `MemorySummarizer`
3. 生成摘要文本
4. 用一条系统消息 `[记忆摘要] ...` 替换旧历史
5. 摘要文本同时写入 `Conversation.memorySummary`

这样做的目的，是在保持长期对话连续性的同时，减少发送给模型的历史长度。

### 8. 情绪更新流程

每次成功完成一轮回复后：

1. `EmotionUpdater` 根据最近若干条消息重新评估情绪
2. 返回新的 `EmotionState`
3. 结果保存到会话
4. UI 顶部显示新的好感度和心情

情绪状态也会参与下一轮提示词组装，因此它不仅是展示信息，也会反过来影响模型输出。

## 数据流关系

可以把系统理解成如下链路：

```text
Compose UI
  -> ChatViewModel
    -> Repository / Agent Service / PromptComposer / ChatProvider
      -> Room / DataStore / Network
    -> StateFlow<MainUiState>
  -> Compose UI Recompose
```

更具体地说：

- UI 发出事件
- `ChatViewModel` 负责业务判断与编排
- Repository 负责持久化
- Agent Service 负责增强分析
- `PromptComposer` 负责构造提示词
- `ChatProvider` 负责真正请求模型
- Room 与 DataStore 的变化再反向推动 UI 更新

这是一个典型的“单向数据流 + 多数据源观察”的结构。

## 运行与验证

### 构建

```bash
./gradlew assembleDebug
```

### 常用检查

```bash
./gradlew lintDebug
./gradlew testDebugUnitTest
./gradlew compileDebugAndroidTestKotlin
./gradlew connectedDebugAndroidTest
```

说明：

- 业务逻辑、状态、仓储、网络相关改动优先运行 `testDebugUnitTest`
- Compose UI 或 `androidTest` 相关改动至少编译 `compileDebugAndroidTestKotlin`
- 设备联调和模拟器细节可直接参考仓库内 `AGENTS.md`

## 测试覆盖现状

当前测试大致覆盖以下方面：

- `PromptComposerTest`：提示词组装顺序与内容
- `OpenAiCompatibleChatProviderTest`：接口请求与错误解析
- `CharacterCardJsonTest`：角色卡 JSON 导入导出兼容
- `ProviderConfigTest`：Provider 默认值与配置校验
- `ChatViewModelTest`：ViewModel 的主要业务流程
- `RoomConversationRepositoryTest`：Room 持久化与迁移
- `ConversationScreenTest`、`SettingsScreenTest`：关键 Compose UI 行为

如果新增功能，优先保持这种分层测试方式：

- 纯逻辑放单元测试
- Room 与 Compose 行为放 `androidTest`

## 后续扩展建议

### 新增 Provider

优先关注：

- `model/ProviderType`
- `model/ProviderConfig`
- `data/settings/AppPreferencesRepository.kt`
- `data/chat/ChatProvider.kt`
- `data/chat/OpenAiCompatibleChatProvider.kt`
- `ui/SettingsScreen.kt`

### 调整提示词或对话策略

优先关注：

- `data/chat/PromptComposer.kt`
- `data/agent/DirectorService.kt`
- `data/agent/EmotionUpdater.kt`
- `data/agent/MemorySummarizer.kt`

### 新增 Agent 能力

建议模式：

1. 在 `data/agent/` 定义接口与实现
2. 在 `model/` 增加必要配置或结果模型
3. 在 `SettingsScreen` 暴露开关或参数
4. 在 `ChatViewModel` 主链路中插入调用点

### 调整会话持久化策略

优先关注：

- `data/chat/AppDatabase.kt`
- `data/chat/ConversationRepository.kt`
- `model/Conversation`

## 总结

RealChat 的实现重点不是“做一个简单聊天框”，而是围绕“长期角色对话”组织一套稳定的数据和提示词链路：

- 角色卡提供人设
- 会话快照保证历史稳定
- PromptComposer 统一装配上下文
- ChatViewModel 统一协调 UI、存储、网络和 Agent 能力
- Room 与 DataStore 负责把状态长期保存下来

理解这条主线之后，继续维护这个项目会轻松很多。
