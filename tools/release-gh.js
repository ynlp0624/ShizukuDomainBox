// 创建 GitHub Release 并上传 APK
const fs = require('fs');
const path = require('path');

const TOKEN = process.env.GH_TOKEN;
const OWNER = 'ynlp0624';
const REPO = 'ShizukuDomainBox';
const TAG = process.env.REL_TAG || 'v2.2.0';
const APK = process.env.REL_APK || 'D:/dnshe/dist/ShizukuDomainBox-Android-v2.2.0.apk';

const BODY = process.env.REL_BODY || '';

async function api(method, url, body, isRaw = false) {
  const headers = {
    Authorization: 'Bearer ' + TOKEN,
    Accept: 'application/vnd.github+json',
    'User-Agent': 'shizuku-dsh'
  };
  if (!isRaw) headers['Content-Type'] = 'application/json';
  const res = await fetch('https://api.github.com' + url, {
    method,
    headers,
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
  console.log('=== 1. 检查 Release 是否已存在 ===');
  let release;
  try {
    release = await api('GET', `/repos/${OWNER}/${REPO}/releases/tags/${TAG}`);
    console.log(`  已存在: ${release.html_url}`);
  } catch (e) {
    if (e.status === 404) {
      console.log('  创建新 Release…');
      release = await api('POST', `/repos/${OWNER}/${REPO}/releases`, {
        tag_name: TAG,
        name: process.env.REL_NAME || TAG,
        body: BODY,
        draft: false,
        prerelease: false
      });
      console.log(`  已创建: ${release.html_url}`);
    } else {
      throw e;
    }
  }

  console.log('\n=== 2. 上传 APK ===');
  const stat = fs.statSync(APK);
  const name = path.basename(APK);
  console.log(`  ${name} (${(stat.size / 1048576).toFixed(2)} MB)`);

  // 先删旧的同名资产
  const assets = await api('GET', `/repos/${OWNER}/${REPO}/releases/${release.id}/assets`);
  for (const a of assets) {
    if (a.name === name) {
      console.log(`  删除旧资产: ${a.name}`);
      await api('DELETE', `/repos/${OWNER}/${REPO}/releases/assets/${a.id}`);
    }
  }

  // 上传
  const uploadUrl = `https://uploads.github.com/repos/${OWNER}/${REPO}/releases/${release.id}/assets?name=${encodeURIComponent(name)}`;
  const data = fs.readFileSync(APK);
  const res = await fetch(uploadUrl, {
    method: 'POST',
    headers: {
      Authorization: 'Bearer ' + TOKEN,
      Accept: 'application/vnd.github+json',
      'Content-Type': 'application/vnd.android.package-archive',
      'User-Agent': 'shizuku-dsh'
    },
    body: data
  });
  if (!res.ok) {
    const t = await res.text();
    throw new Error(`上传失败 ${res.status}: ${t.slice(0, 300)}`);
  }
  const asset = await res.json();
  console.log(`  ✓ ${asset.name} (${(asset.size / 1048576).toFixed(2)} MB)`);
  console.log(`  下载: ${asset.browser_download_url}`);

  console.log(`\n✓ Release: ${release.html_url}`);
})().catch(e => {
  console.error('\n✗ 失败:', e.message);
  process.exit(1);
});
