# CI 流水线与仓库镜像

> 更新时间：2026-11-18

## 仓库布局

| remote | 地址 | 用途 |
|---|---|---|
| `origin` | `https://github.com/thereYany/btry.git` | CI 主仓库（新账号，push 触发 Actions） |
| `upstream` | `https://ghfast.top/https://github.com/WhereLee/etry.git` | 原仓库，只读保留，不再推送 |

服务器上 GitHub 的 git 协议直连不稳定，`api.github.com` 可达；HTTPS 推送目前可直连成功，失败时可用 `ghfast.top` 代理前缀。

## 工作流

`.github/workflows/ci.yml`，三个 job：

| job | 内容 |
|---|---|
| `data-boundary` | `tools/table_access_inventory.py --baseline` 数据边界棘轮（跨模块表/多表 SQL 只降不升） |
| `backend` | Java 21 + MySQL 8 + Redis 7：`mvn test` → `mvn -Pintegration test` → JaCoCo |
| `frontend` | Node 20：build |

触发条件：**push 到 main**（`branches: [ main ]`）与 PR。初版 `on: push` 会让 tag 推送也跑 CI，已修正为仅 main 分支。

## 盯 CI

```bash
export GH_TOKEN=$(cat ~/.config/btry/gh_token)
python3 tools/ci_watch.py --repo thereYany/btry --sha "$(git rev-parse HEAD)" --watch
```

- 脚本只读 runs/jobs；无 token 也能看公开仓库状态，但失败日志需要 token（Actions: Read）。
- 失败日志：`GET /repos/thereYany/btry/actions/runs/<id>/logs`（zip，需 token）。

## 凭据

- 令牌存放在 `~/.config/btry/gh_token`（600），git 通过 `GIT_ASKPASS=~/.config/btry/git-askpass.sh` 读取，不写进仓库与 git config。
- 当前使用 classic PAT（新账号、作用域仅该账号名下仓库）。**CI 循环结束后应在 GitHub 设置页删除该令牌**，下次需要再临时签发。
- 推送命令：

```bash
export GIT_ASKPASS=~/.config/btry/git-askpass.sh GIT_TERMINAL_PROMPT=0
git push origin main
```

## 首次 CI 结果（2026-11-18）

push `8914a39` → run #5：`data-boundary` / `backend` / `frontend` 三个 job 全部 success
（Boot 4.1.2 + Java 21 + V14–V18 迁移 + RBAC 全部在干净 CI 环境通过）。
