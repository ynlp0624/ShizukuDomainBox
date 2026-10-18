// 创建 GitCode Release 并上传 APK（两步上传：预签名 URL + PUT）
const fs = require('fs');
const path = require('path');

const TOKEN = process.env.GC_TOKEN;
const OWNER = 'gcw_YiSvwnI0';
const REPO = 'ShizukuDomainBox';
const TAG = process.env.REL_TAG || 'v2.2.0';
const APK = process.env.REL_APK || 'D:/dnshe/dist/ShizukuDomainBox-Android-v2.2.0.apk';
const API = 'https://api.gitcode.com/api/v5';

const BODY = process.env.REL_BODY || '';

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
        name: process.env.REL_NAME || TAG,
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
