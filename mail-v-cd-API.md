# mail.v.cd 开放 API 逆向文档

> 官方尚未发布文档。本文档由 Shizuku Domain Box 项目在对接过程中逆向整理，
> 实测于 2026-10，仅供参考。服务端随时可能调整，请自行验证。

## 基本信息

| 项 | 值 |
|---|---|
| 基址 | `https://mail.v.cd/api/open` |
| 认证 | `Authorization: Bearer <token>` |
| 前端 | Cloudflare |
| 后端 | Go（错误信息为 Go 风格） |
| CORS | **未开放**，只能服务端/App 调用 |

说明：`/v1/` 前缀与无前缀等价，两种写法都能用。

## 令牌

用户令牌形如 `lq_` + 43 字符 base64url，共 46 字符。在邮箱管理页面生成。

**令牌只绑定一个邮箱**，无法跨邮箱使用。管理员级接口（`/domains`、`/aliases`、`/mailboxes`、`/mailboxes/{id}`）一律返回 403。

## ID 前缀约定

| 前缀 | 含义 |
|---|---|
| `mail_` | 邮件 |
| `snd_` | 发信任务 |
| `mbx_` | 邮箱 |
| `fld_` | 文件夹 |
| `att_` | 附件 |
| `uid_` | IMAP UID |

## 端点一览

### 发送邮件

```
POST /api/open/send
```

请求体字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `to` | string[] | 收件人，**必须是数组** |
| `cc` | string[] | 抄送 |
| `bcc` | string[] | 密送 |
| `from` | string | 发件地址，必须是本人已授权地址 |
| `fromName` | string | 发件人显示名 |
| `subject` | string | 主题 |
| `text` | string | 纯文本正文 |
| `html` | string | HTML 正文 |
| `attachments` | array | 附件引用 |

**明确不支持**的字段（传了会报 `unknown field`）：
`replyTo` / `sender` / `headers` / `template` / `tags` / `metadata` / `priority` / `sendAt` / `track` / `pgp` / `body` / `content`

响应 201：

```json
{
  "id": "snd_xxx",
  "queueStatus": "delivered"
}
```

`from` 必须是本人已授权地址，否则 403 `sender address is not authorized`。

### 限流

**每分钟最多 5 封**。

超限返回错误文本：`smtp send rate limit exceeded: per-minute limit 5`

注意：**不返回 `Retry-After` 头**，客户端只能自己数时间戳做本地限流。

### 发信队列

```
GET  /api/open/send?limit=20
GET  /api/open/send/{id}
```

用于查询投递状态。

### 邮件列表

```
GET /api/open/mailboxes/{mailboxId}/messages?folder=Inbox&limit=20
```

`folder` 常用值：`Inbox`、`Sent`。

响应：

```json
{
  "items": [
    {
      "id": "mail_xxx",
      "mailboxId": "mbx_xxx",
      "folderId": "fld_xxx",
      "folder": "Inbox",
      "messageUid": "uid_xxx",
      "imapUid": 12,
      "imapModseq": 13,
      "messageId": "<xxx@smtp.mail.v.cd>",
      "threadId": "xxx@smtp.mail.v.cd",
      "threadCount": 1,
      "subject": "...",
      "from": "...",
      "to": ["..."],
      "cc": [],
      "bcc": [],
      "sentAt": "...",
      "receivedAt": "...",
      "sizeBytes": 3072,
      "seen": false,
      "attachments": [...],
      "bodyText": "",   // 列表里可能为空
      "bodyHtml": ""
    }
  ]
}
```

**重要**：列表接口返回的 `bodyText` / `bodyHtml` 常为空，要看正文必须单独请求详情。

### 邮件详情

```
GET /api/open/messages/{messageId}
```

返回完整邮件，含：

- `bodyText` / `bodyHtml`
- `authentication: { spf, dkim, dmarc }`（多数情况为 `unknown`）
- `attachments[]`：`{ id, filename, contentType, sizeBytes }`

### 下载附件

```
GET /api/open/attachments/{attachmentId}
```

返回二进制流。

**附件不能内联上传**：`attachments` 字段只接受已存在的附件引用，不接受 base64。
上传端点未找到（`POST /attachments/upload` 返回 405，路径存在但方法不对）。

## 已知限制

- **无写操作**：标记已读 / 星标 / 移动 / 删除 —— 全部 404 或 405
- **无草稿**：草稿接口不存在
- **无法内联附件**
- **无 CORS**：浏览器直连会被拦，必须走后端代理

## 调用示例

```bash
TOKEN="lq_xxx"
MBX="mbx_xxx"

# 列收件箱
curl -H "Authorization: Bearer $TOKEN" \
  "https://mail.v.cd/api/open/mailboxes/$MBX/messages?folder=Inbox&limit=10"

# 发信
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "to": ["someone@example.com"],
    "from": "you@v.cd",
    "subject": "Hello",
    "text": "Hi there"
  }' \
  "https://mail.v.cd/api/open/send"
```

## 实现建议

1. **本地限流**：记录最近 60 秒内的发送时间戳，超过 5 条就排队等待
2. **正文懒加载**：列表页只显示主题/发件人/摘要，点进详情再拉完整正文
3. **令牌加密存储**：移动端用 Keystore / 桌面用 DPAPI
4. **输入规范化**：中文输入法会把 `_` 打成 `-`、`@` 打成 `＠`，务必修正
