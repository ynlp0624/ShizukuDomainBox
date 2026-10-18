// 通过 GitHub REST API（Git Data API）全量推送 D:\dnshe -> ynlp0624/ShizukuDomainBox
// 本机 git:// 协议不稳，走 API 更可靠
const fs = require('fs');
const path = require('path');

const TOKEN = process.env.GH_TOKEN;
const OWNER = 'ynlp0624';
const REPO = 'ShizukuDomainBox';
const ROOT = 'D:/dnshe';
const BRANCH = 'main';

if (!TOKEN) {
  console.error('GH_TOKEN missing');
  process.exit(1);
}

async function api(method, url, body) {
  const res = await fetch('https://api.github.com' + url, {
    method,
    headers: {
      Authorization: 'Bearer ' + TOKEN,
      Accept: 'application/vnd.github+json',
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

const SKIP_DIRS = new Set([
  '.git', 'build', '.gradle', '.kotlin', 'out', 'node_modules',
  '.idea', '.vscode', 'dist', 'screens', 'screenshots'
]);
const SKIP_EXT = new Set(['.apk', '.aab', '.exe', '.jar', '.jks', '.keystore', '.log']);
const SKIP_FILES = new Set(['keystore.properties', 'local.properties']);

function walk(dir, base, out) {
  for (const name of fs.readdirSync(dir)) {
    const full = path.join(dir, name);
    const rel = base ? base + '/' + name : name;
    let st;
    try { st = fs.statSync(full); } catch { continue; }
    if (st.isDirectory()) {
      if (SKIP_DIRS.has(name)) continue;
      walk(full, rel, out);
    } else {
      if (SKIP_FILES.has(name)) continue;
      const ext = path.extname(name).toLowerCase();
      if (SKIP_EXT.has(ext)) continue;
      // gradle-wrapper.jar 例外
      if (ext === '.jar' && name === 'gradle-wrapper.jar') { /* keep */ }
      out.push({ rel, full, size: st.size });
    }
  }
}

(async () => {
  console.log('=== 1. 收集文件 ===');
  const files = [];
  walk(ROOT, '', files);
  console.log(`  ${files.length} 个文件`);

  // 过滤体积过大的（GitHub 单文件 100MB 限制，实用上限 40MB）
  const big = files.filter(f => f.size > 40 * 1024 * 1024);
  if (big.length) {
    console.log('  跳过超大文件:');
    big.forEach(f => console.log(`    ${f.rel} (${(f.size / 1048576).toFixed(1)} MB)`));
  }
  const use = files.filter(f => f.size <= 40 * 1024 * 1024);

  console.log('\n=== 2. 检查仓库 ===');
  let repoOk = false;
  try {
    const r = await api('GET', `/repos/${OWNER}/${REPO}`);
    console.log(`  仓库存在: ${r.full_name} (default: ${r.default_branch})`);
    repoOk = true;
  } catch (e) {
    if (e.status === 404) {
      console.log('  仓库不存在，创建…');
      await api('POST', '/user/repos', {
        name: REPO,
        description: 'DNSHE 免费域名多账号管理工具 + mail.v.cd 邮箱客户端（安卓 + Windows）',
        private: false,
        has_issues: true,
        has_wiki: false,
        auto_init: false
      });
      console.log('  已创建');
      repoOk = true;
    } else {
      throw e;
    }
  }
  if (!repoOk) throw new Error('仓库不可用');

  console.log('\n=== 3. 上传 blob ===');
  const tree = [];
  let done = 0;
  for (const f of use) {
    const content = fs.readFileSync(f.full);
    const isText = !/\.(png|jpg|jpeg|gif|webp|ico|ttf|otf|woff2?|zip)$/i.test(f.rel);
    let blob;
    if (isText) {
      blob = await api('POST', `/repos/${OWNER}/${REPO}/git/blobs`, {
        content: content.toString('base64'),
        encoding: 'base64'
      });
    } else {
      blob = await api('POST', `/repos/${OWNER}/${REPO}/git/blobs`, {
        content: content.toString('base64'),
        encoding: 'base64'
      });
    }
    tree.push({
      path: f.rel,
      mode: '100644',
      type: 'blob',
      sha: blob.sha
    });
    done++;
    if (done % 25 === 0) console.log(`  ${done}/${use.length}`);
  }
  console.log(`  完成 ${done} 个 blob`);

  console.log('\n=== 4. 创建 tree ===');
  const newTree = await api('POST', `/repos/${OWNER}/${REPO}/git/trees`, {
    tree
  });
  console.log(`  tree: ${newTree.sha}`);

  console.log('\n=== 5. 创建 commit ===');
  let parentSha = null;
  try {
    const ref = await api('GET', `/repos/${OWNER}/${REPO}/git/ref/heads/${BRANCH}`);
    parentSha = ref.object.sha;
    console.log(`  父提交: ${parentSha}`);
  } catch (e) {
    if (e.status !== 404) throw e;
    console.log('  首次提交（无父提交）');
  }

  const commitBody = {
    message: 'feat(mail): 新增 mail.v.cd 邮箱客户端\n\n- 逆向 mail.v.cd 开放 API 并整理成文档（mail-v-cd-API.md）\n- 新增邮箱数据层：MailModels / MailApi / MailboxStore（Keystore 加密令牌）\n- 新增邮箱界面：收件箱/已发送、写邮件、邮件详情、账号管理\n- 底部导航加「邮箱」标签\n- 新增更新说明弹窗（v2.1.0）\n- 修复：对话框被 Scaffold 遮挡、onEdit(null) 不弹框、msg 未定义、\n  详情正文不加载、全角符号导致令牌/地址错误、保存按钮被键盘遮挡\n- PC core 同步 MailModels / MailApi',
    tree: newTree.sha,
    parents: parentSha ? [parentSha] : []
  };
  const commit = await api('POST', `/repos/${OWNER}/${REPO}/git/commits`, commitBody);
  console.log(`  commit: ${commit.sha}`);

  console.log('\n=== 6. 更新分支引用 ===');
  if (parentSha) {
    await api('PATCH', `/repos/${OWNER}/${REPO}/git/refs/heads/${BRANCH}`, {
      sha: commit.sha,
      force: true
    });
  } else {
    await api('POST', `/repos/${OWNER}/${REPO}/git/refs`, {
      ref: `refs/heads/${BRANCH}`,
      sha: commit.sha
    });
  }
  console.log(`  分支 ${BRANCH} -> ${commit.sha}`);

  console.log(`\n✓ 完成: https://github.com/${OWNER}/${REPO}`);
})().catch(e => {
  console.error('\n✗ 失败:', e.message);
  process.exit(1);
});
