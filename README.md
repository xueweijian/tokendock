# TokenDock

桌面小组件：一眼看清 **OpenCode Go** 与 **智谱 GLM Coding Plan** 的额度用量。

深色卡片显示 5 小时 / 周 / 月窗口百分比、进度条、重置倒计时、MCP 工具用量与最后同步时间，后台定时自动刷新。

## 截图

（TODO：补一张真机小组件截图）

## 功能

- **OpenCode Go**：5h 滚动窗口 / 周 / 月 用量百分比 + 重置时间
- **GLM Coding Plan**：5h Token 窗口、月度 MCP 配额（联网搜索 / 网页读取 / zread 分项）、套餐等级
- 深色卡片小组件，随时缩放；点按进入配置页
- WorkManager 定时后台同步（间隔可调 15–720 分钟），无后台服务常驻
- API Key 仅存本机 DataStore，**不经过任何第三方服务器**，请求直连官方端点
- OpenCode 端点内置浏览器 UA（绕过 Cloudflare 对非浏览器客户端的拦截）

## 数据来源（均为未公开文档接口）

| Provider | 端点 | 认证 |
|---|---|---|
| OpenCode Go | `GET https://opencode.ai/zen/go/v1/usage` | `Authorization: Bearer <key>` |
| GLM Coding Plan | `GET https://open.bigmodel.cn/api/monitor/usage/quota/limit` | 同上 |

字段语义注意：GLM `TIME_LIMIT` 中 `usage`=上限、`currentValue`=已用、`usageDetails[].usage`=已用（与顶层相反）。

## 下载安装

1. 到 [Releases](../../releases) 下载最新 `app-release.apk`
2. 手机安装（允许未知来源）
3. 打开 TokenDock，粘贴两个 API Key → 保存并同步
4. 桌面长按 → 小组件 → TokenDock，拖到桌面

## 从源码构建

```bash
./gradlew assembleRelease
```

或直接推仓库，GitHub Actions 自动构建；打 tag（`v*`）自动签名发 Release。

### 自助签名（CI 用）

```bash
keytool -genkeypair -v -keystore tokendock.jks -alias tokendock \
  -keyalg RSA -keysize 2048 -validity 10950
base64 -w0 tokendock.jks > keystore.b64
```

将以下值配到仓库 **Settings → Secrets → Actions**：

| Secret | 值 |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `keystore.b64` 文件内容 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 密码 |
| `ANDROID_KEY_ALIAS` | 别名（如 `tokendock`） |
| `ANDROID_KEY_PASSWORD` | key 密码 |

## License

MIT
