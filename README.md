# RealChat

一个基于 DeepSeek 官方平台 API 的终端聊天工具。

## 准备

1. 安装依赖：

```bash
uv sync --dev
```

2. 激活虚拟环境：

```bash
source .venv/bin/activate
```

3. 配置 API Key：

```bash
export DEEPSEEK_API_KEY="your_api_key"
```

## 运行

默认使用 `deepseek-chat` 模型：

```bash
python main.py
```

自定义模型或 system prompt：

```bash
python main.py --model deepseek-chat --system "You are a concise coding assistant."
```

## 会话命令

- `/clear`：清空当前会话上下文，保留当前 system prompt
- `/exit`：退出聊天
- `/quit`：退出聊天

## 常见问题

- 提示 `Missing DEEPSEEK_API_KEY`：确认环境变量已导出到当前 shell。
- 提示 `Failed to initialize DeepSeek client`：通常是依赖未安装，先执行 `uv sync --dev`。
- 提示 `Request failed`：通常是网络、认证或接口参数问题，按错误信息排查即可。
