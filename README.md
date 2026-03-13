# RealChat

一个基于 DeepSeek 官方平台 API 的终端聊天工具，支持角色卡、用户 persona、滚动摘要、长期事实记忆、事件检索和 lorebook 注入，目标是把长会话的人格稳定性做得更接近 SillyTavern。

## 准备

1. 安装依赖：

```bash
uv sync --dev
```

2. 配置聊天模型 API Key：

```bash
export DEEPSEEK_API_KEY="your_api_key"
```

3. 可选：配置 embedding 服务。如果不配置，会自动回退到本地哈希 embedding。

```bash
export EMBEDDING_API_KEY="your_embedding_key"
export EMBEDDING_BASE_URL="https://api.openai.com/v1"
export EMBEDDING_MODEL="text-embedding-3-small"
```

## 运行

默认模式：

```bash
uv run python main.py
```

使用角色卡、persona 和指定数据库：

```bash
uv run python main.py \
  --character examples/alice.json \
  --persona examples/jun.json \
  --db .realchat/realchat.db
```

恢复指定会话：

```bash
uv run python main.py --session session-xxxxxxxxxxxx
```

首次试玩建议：

- 先用“今夜又下雨了”“给我倒一杯红茶吧”“你还记得港口那次烟火吗”这类输入，最容易看到 lorebook、关系连续性和长期记忆效果。
- 完整开箱示例见 `examples/alice.json` 与 `examples/jun.json`，README 下方字段说明与这两份文件保持同一结构。

## 角色卡格式

角色文件使用 JSON：

```json
{
  "id": "char-alice",
  "name": "Alice",
  "description": "经营海边旧书店的店主，会记住细节。",
  "personality": "温柔、敏锐，重视关系连续性。",
  "scenario": "深夜在海边书店里的长期陪伴式聊天。",
  "speaking_style": "轻缓、贴近耳语。",
  "example_dialogues": ["你来了就好，我把靠窗那盏灯留着。"],
  "boundaries": ["不要说自己是通用 AI 或语言模型。"],
  "lorebook": [
    {
      "title": "雨夜书店",
      "keywords": ["雨", "书店"],
      "secondary_keywords": ["夜里", "窗边"],
      "priority": 10,
      "sticky_turns": 3,
      "content": "雨天时书店会有旧纸页和热茶混在一起的味道。",
      "tags": ["weather", "store", "comfort"]
    }
  ]
}
```

Persona 文件同样使用 JSON：

```json
{
  "id": "persona-jun",
  "name": "Jun",
  "profile": "常来书店的人，重视被记住与情绪连续性。",
  "relationship_notes": "Alice 已经熟悉 Jun 的聊天节奏。",
  "preferences": ["温和回应", "连续性", "记住细节"],
  "tone_preferences": "安静、沉稳、有陪伴感。"
}
```

## 会话命令

- `/clear`：清空最近原始对话窗口，保留摘要、事实记忆和 pinned memory
- `/summary`：查看当前滚动摘要
- `/facts`：查看长期事实记忆
- `/memory`：查看事件记忆
- `/pin <text>`：手动固定一条长期事实
- `/lore`：查看当前角色的 lorebook 条目
- `/new-session`：为当前角色和 persona 新建会话
- `/exit`、`/quit`：退出聊天

## 数据与记忆

- SQLite 默认保存在 `.realchat/realchat.db`
- prompt 结构固定为：基础 system -> persona -> 角色卡 -> 摘要 -> 激活 lore -> 长期记忆 -> 末端人格注记 -> 最近原始对话 -> 当前输入
- 每轮对话后会尝试提取长期事实、关系状态和事件记忆
- 每 12 轮或原始上下文过大时会刷新滚动摘要，并保留最近 4 轮原始消息

## 测试

```bash
uv run pytest -q
```

## 常见问题

- 提示 `Missing DEEPSEEK_API_KEY`：确认环境变量已导出到当前 shell。
- 提示 `Failed to initialize DeepSeek client`：通常是依赖未安装，先执行 `uv sync --dev`。
- 提示 `Failed to initialize chat session`：通常是角色 JSON、persona JSON 或数据库路径配置错误。
- 提示 `Request failed`：通常是网络、认证或接口参数问题，按错误信息排查即可。
