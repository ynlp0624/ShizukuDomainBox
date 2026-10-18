# mail.v.cd Token API

> 本文档整理自官方 Token API 说明（2026-10）。接口如有变动以官方为准。

## 基本信息

| 项 | 值 |
|---|---|
| 基址 | `https://mail.v.cd/api/open/v1` |
| 认证 | `Authorization: Bearer YOUR_API_TOKEN` |
| 错误格式 | `{"error":"error message"}` |
| CORS | 未开放，只能服务端 / App 调用 |

> `/api/open/...` 与 `/api/open/v1/...` 两种写法目前都能用，但**建议统一用 `/v1`**。

网页登录态**不能**代替 Token。

## 令牌与 Scope

令牌形如 `lq_` + 43 字符 base64url（共 46 字符），仅在创建时显示一次。

| Scope | 用途 |
|---|---|
| `mailboxes:read` | 列出自己的邮箱、拿 mailboxId |
| `messages:read` | 读邮件、读发送记录、下载附件 |
| `messages:send` | 发信 |
| `messages:manage` | 重试 / 取消队列发送 |

Scope 不能提升账户权限，管理员接口仍仅限管理员。

## 端点一览

| 方法 | 路径 | 说明 | Scope |
|---|---|---|---|
| GET | `/me/mailboxes` | 列出我的邮箱及 ID | `mailboxes:read` |
| GET | `/mailboxes/{id}/messages` | 读取邮箱邮件列表 | `messages:read` |
| GET | `/messages/{id}` | 读取邮件详情 | `messages:read` |
| GET | `/attachments/{id}` | 下载邮件附件 | `messages:read` |
| POST | `/send` | 提交发送邮件 | `messages:send` |
| GET | `/send` | 列出发送记录 | `messages:read` |
| GET | `/send/{id}` | 查询发送状态 | `messages:read` |
| GET | `/send/{id}/events` | 查看投递事件 | `messages:read` |
| POST | `/send/{id}/retry` | 重试发送 | `messages:manage` |
| POST | `/send/{id}/cancel` | 取消队列发送 | `messages:manage` |

域名、全站邮箱、别名、DNS 管理接口**仅限管理员**；`/mailboxes`（不带 `/me`）不是普通用户的邮箱列表。

## 1. 列出我的邮箱

```
GET /me/mailboxes?limit=50&cursor=...
```

| 参数 | 说明 |
|---|---|
| `limit` | 每页数量，默认 50，最大 100 |
| `cursor` | 上一页返回的 `nextCursor`，URL 编码 |

响应：

```json
{
  "items": [
    {
      "id": "mbx_example",
      "domainId": "dom_example",
      "address": "hello@example.com",
      "displayName": "My mailbox",
      "quotaMb": 1024,
      "status": "active",
      "createdAt": "2026-10-18T00:00:00Z"
    }
  ],
  "nextCursor": ""
}
```

只返回 Token 所属账户拥有的 **active 本地邮箱**（含奖品邮箱），不含共享、外部、锁定、已删除。

**`items[].id` 就是后续请求要用的 `MAILBOX_ID`。** `nextCursor` 为空表示最后一页。

### 实测

一个令牌可以绑定**多个**邮箱。示例令牌返回了 `shizuku@cn.mt` 和 `shizuku@v.cd` 两个。

## 2. 读邮件列表

```
GET /mailboxes/{MAILBOX_ID}/messages?folder=Inbox&limit=30&q=invoice&cursor=...
```

| 参数 | 说明 |
|---|---|
| `folder` | 默认 `Inbox`；`all` 表示全部文件夹 |
| `q` | 搜索词 |
| `limit` | 默认 30，最大 100 |
| `cursor` | 该列表的 `nextCursor` |

响应里每项含 `id` / `mailboxId` / `folder` / `subject` / `from` / `to` / `sentAt` / `sizeBytes` / `attachments` 等。

> 注意：列表里的 `bodyText` / `bodyHtml` 通常为空，且 `attachments` 可能缺省。要看正文和附件必须调详情接口。

## 3. 邮件详情

```
GET /messages/{id}
```

返回完整字段，含：

- `bodyText` / `bodyHtml`
- `authentication: { spf, dkim, dmarc }`（多数为 `unknown`）
- `attachments[]`：`{ id, messageId, filename, contentType, sizeBytes, createdAt }`

## 4. 下载附件

```
GET /attachments/{id}
```

返回二进制流。

## 5. 发送邮件

```
POST /send
Idempotency-Key: <可选，≤128 字符，保留 24 小时>
Content-Type: application/json
```

```json
{
  "mailboxId": "mbx_xxx",
  "to": ["recipient@example.com"],
  "cc": [],
  "bcc": [],
  "subject": "Hello",
  "text": "纯文本正文",
  "html": "<p>HTML 正文</p>",
  "attachments": [
    {
      "filename": "note.txt",
      "contentBase64": "aGVsbG8=",
      "contentType": "text/plain"
    }
  ]
}
```

| 字段 | 说明 |
|---|---|
| `mailboxId` | **必填** |
| `to` / `cc` / `bcc` | 地址数组，合计 1~100 个不同收件人 |
| `subject` | 主题 |
| `text` / `html` | 建议同时提供 `text` |
| `attachments` | 最多 20 项，每项 `filename` + `contentBase64`（+ 可选 `contentType`） |

### 幂等

- 每个新请求用**新的** `Idempotency-Key`
- 同键同内容重试 → 不重复入队
- 同键不同内容 → **409**

### 返回值

```json
{
  "id": "mail_xxx",
  "queueId": "snd_xxx",
  "queueStatus": "queued",
  "messageId": "mail_xxx",
  "mailboxId": "mbx_xxx",
  "recipients": ["..."],
  "maxAttempts": 5,
  "nextAttemptAt": "...",
  "createdAt": "..."
}
```

返回已入队 **不等于** 收件方已收到，要用发送记录 ID 查状态。

### 限制

发信仍受账户权限、域名禁止发信、Web/API 发信开关、容量和频率限制。SMTP 客户端开关与 Web/API 发信开关**分别生效**。

## 6. 投递事件

```
GET /send/{id}/events
```

```json
{
  "auditEvents": [
    {
      "id": "audit_xxx",
      "queueId": "snd_xxx",
      "sentMessageId": "mail_xxx",
      "source": "open_api",
      "event": "queued",
      "status": "queued",
      "mailFrom": "...",
      "headerFrom": "...",
      "recipients": ["..."],
      "createdAt": "..."
    }
  ]
}
```

## 频率限制

实测发信限流为 **每分钟 5 封**，超限返回 `smtp send rate limit exceeded: per-minute limit 5`。

服务端**不返回 `Retry-After` 头**，客户端需要自己记录时间戳做本地限流。

## 状态码

| 码 | 含义 |
|---|---|
| 200 / 201 | 成功 / 已创建或已入队 |
| 400 | 参数或游标格式错误 |
| 401 | Token 缺失、无效、过期、已禁用，或账户不可用 |
| 403 | 缺少 Scope / 账户权限 / 管理员身份，或发信被策略禁止 |
| 404 | 资源不存在、不属于当前账户或不可用 |
| 409 | 幂等键冲突（同键不同内容） |
| 429 | 请求过于频繁 |
| 5xx | 服务端错误 |

## 已知的坑

- **`GET /send` 列表接口实测会超时**（2026-10 观测，curl 60 秒无响应）。查状态优先用 `/send/{id}/events`。
- 列表接口不带 `attachments`，附件要从详情接口读。
- 附件用 JSON 内联 base64，单个太大可能被账户容量限制拒绝。

## 调用示例

```bash
TOKEN="lq_xxx"

# 1. 拿 mailboxId
curl --get 'https://mail.v.cd/api/open/v1/me/mailboxes' \
  --header "Authorization: Bearer $TOKEN" \
  --data-urlencode 'limit=50'

# 2. 列收件箱
curl --get 'https://mail.v.cd/api/open/v1/mailboxes/MBX_ID/messages' \
  --header "Authorization: Bearer $TOKEN" \
  --data-urlencode 'folder=Inbox' \
  --data-urlencode 'limit=10'

# 3. 发信（带附件）
curl 'https://mail.v.cd/api/open/v1/send' \
  --header "Authorization: Bearer $TOKEN" \
  --header 'Content-Type: application/json' \
  --header 'Idempotency-Key: unique-request-id-001' \
  --data '{
    "mailboxId":"MBX_ID",
    "to":["recipient@example.com"],
    "subject":"Hello",
    "text":"Hello from DNSHE API",
    "attachments":[{"filename":"note.txt","contentBase64":"aGVsbG8=","contentType":"text/plain"}]
  }'
```

## 实现建议

1. **先调 `/me/mailboxes`** 拿 ID，不要手填。
2. **令牌可能绑多个邮箱**，做好选择界面。
3. **本地限流**：记录最近 60 秒发送时间戳，超过 5 条排队等待。
4. **正文懒加载**：列表只显示主题/发件人/摘要，进详情再拉完整内容。
5. **令牌加密存储**：移动端 Keystore / 桌面 DPAPI。
6. **输入规范化**：中文输入法会把 `_` 打成 `-`、`@` 打成 `＠`，务必修正。
7. **幂等键**：每次发送生成新 UUID；网络重试复用它。
