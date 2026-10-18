// 创建 GitCode Release 并上传 APK（两步上传：预签名 URL + PUT）
const fs = require('fs');
const path = require('path');

const TOKEN = process.env.GC_TOKEN;
const OWNER = 'gcw_YiSvwnI0';
const REPO = 'ShizukuDomainBox';
const TAG = 'v2.1.0';
const APK = 'D:/dnshe/dist/ShizukuDomainBox-Android-v2.1.0.apk';
const API = 'https://api.gitcode.com/api/v5';

const BODY = `## 邮箱功能来了

这次把 [mail.v.cd](https://mail.v.cd) 的开放 API 逆向出来，做成了完整的邮箱客户端。

官方还没出文档，接口是我们自己摸出来的，整理在仓库根目录的 \`mail-v-cd-API.md\`。

### 新增

- **邮箱标签页** —— 收件箱 / 已发送分栏，支持搜索
- **写邮件** —— 收件人 / 抄送 / 密送 / HTML 正文
- **邮件详情** —— 完整正文、附件下载、SPF / DKIM / DMARC 验签结果显示
- **多邮箱账号** —— 令牌用 Android Keystore 加密保存
- **本地限流提示** —— 服务端限制每分钟 5 封，发送前就能看到还剩几封
- **更新说明弹窗** —— 版本变化时自动展示更新内容

### 修复

- 邮箱编辑对话框被 Scaffold 遮挡，弹不出来
- 点「添加邮箱」传 null 导致不弹框
- 邮件详情页正文空白（改为详情页单独拉取完整邮件）
- 中文输入法把 \`_\` 打成 \`-\`、\`@\` 打成 \`＠\` 导致令牌 / 地址错误
- 保存按钮被键盘遮挡点不到（按钮移到对话框顶部）

### 说明

- 签名与旧版一致，可覆盖升级
- 逆向下标：邮件写操作（标记已读 / 移动 / 删除）和草稿接口服务端不存在
- 附件只能下载，上传端点未找到
`;

async function api(method, url, body) {
  const res = await fetch(API + url, {
    method,
    headers: {
      'PRIVATE-TOKEN': TOKEN,
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'User-Agent': 'shizuku-dsh'
    },
    body: body ? JSON.stringify(body) : undefined
  });
  const text = await res.text();
  if (!res.ok) {
    const err = new Error(`${method} ${url} -> ${res.status}: ${text.slice(0, 400)}`);
    err.status = res.status;
    throw err;
  }
  return text ? JSON.parse(text) : {};
}

(async () => {
  console.log('=== 1. 检查 Release ===');
  let release;
  try {
    release = await api('GET', `/repos/${OWNER}/${REPO}/releases/tags/${TAG}`);
    console.log(`  已存在: id=${release.id}`);
  } catch (e) {
    if (e.status === 404) {
      console.log('  创建中…');
      release = await api('POST', `/repos/${OWNER}/${REPO}/releases`, {
        tag_name: TAG,
        name: 'v2.1.0 · 邮箱功能来了',
        body: BODY,
        target_commitish: 'main',
        prerelease: false
      });
      console.log(`  已创建: id=${release.id}`);
    } else {
      throw e;
    }
  }

  console.log('\n=== 2. 上传 APK ===');
  const name = path.basename(APK);
  const stat = fs.statSync(APK);
  console.log(`  ${name} (${(stat.size / 1048576).toFixed(2)} MB)`);

  // 第一步：拿预签名 URL
  const up = await api('GET',
    `/repos/${OWNER}/${REPO}/releases/${TAG}/upload_url?file_name=${encodeURIComponent(name)}`);
  console.log(`  预签名 url: ${up.url ? up.url.slice(0, 80) + '…' : '(无)'}`);

  // 第二步：PUT 上传
  const data = fs.readFileSync(APK);
  const putRes = await fetch(up.url, {
    method: 'PUT',
    headers: {
      ...(up.headers || {}),
      'Content-Type': 'application/octet-stream'
    },
    body: data
  });
  console.log(`  PUT 状态: ${putRes.status}`);
  if (!putRes.ok) {
    const t = await putRes.text();
    throw new Error(`上传失败 ${putRes.status}: ${t.slice(0, 300)}`);
  }
  const putText = await putRes.text();
  console.log(`  响应: ${putText.slice(0, 200)}`);

  console.log(`\n✓ 完成: https://gitcode.com/${OWNER}/${REPO}/releases/tag/${TAG}`);
})().catch(e => {
  console.error('\n✗ 失败:', e.message);
  process.exit(1);
});
