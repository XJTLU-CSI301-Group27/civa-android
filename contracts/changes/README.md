# 接口变更登记（IC）

本目录是跨组接口变更的统一登记入口。接口包括：跨模块 Java 方法/DTO、HTTP 路径/方法/参数/返回字段、公共事件/导航参数、数据库共享语义、错误码、单位/日期/权限/事务含义——不仅是名为 `interface` 的文件。

私有辅助方法、模块内未被其他组引用的实现类不必登记。

## 正常变更流程

提出方建立 `IC-<GitHub-Issue号>.md`（无 Issue 用 `IC-YYYYMMDD-组别-序号`），记录需求、现有接口为什么不能满足、旧/新契约、影响方、测试和迁移。

状态：`PROPOSED → ACCEPTED → IMPLEMENTING → VERIFIED → CLOSED`，可 `REJECTED`。EF 负责契约决策；涉及业务语义时由提供组和受影响调用组核对。Agent 不能自行把变更标成 EF 已接受。

## 发现私自增加接口时

1. 补建 IC，标记 `UNREGISTERED`，状态 `DETECTED`。
2. 保留原 PR、分支和完整 commit SHA；不执行 reset/强推/删除他人分支。
3. 文档归档仅存脱敏 diff 摘要、哈希、日志和决策，不把旧源码复制进 `app/src` 或 `server/src`。
4. 搜索全部调用方、DTO、HTTP 路由、迁移和测试，给出机器可读影响报告。
5. EF 与受影响组选择处置：内部化 / 复用已有接口 / 兼容新增 / 破坏性变更 / 临时 adapter / 撤回。
6. 修复后跑完整门禁；原始违规与最终修复结果都留在同一 IC。

异常状态：`DETECTED → TRIAGED → ACCEPTED_FOR_REWORK / REJECTED / NOT_PUBLIC → FIXED → VERIFIED → CLOSED`。

YAML front matter 是机器权威字段，正文供人解释；机器字段 `type/status` 与正文不得矛盾。`contract_guard.py` 的 `digest` 按提案文件计算，不能手填。

## 可复制模板

```markdown
---
id: IC-123
type: NORMAL / UNREGISTERED
status: PROPOSED / DETECTED
allowedOperations: []
allowedSchemas: []
allowedSymbols: []
contractDigest: null
baseSha: null
headSha: null
---
# IC-编号 接口变更标题
类型：NORMAL / UNREGISTERED
状态：PROPOSED / DETECTED / ...
提出组及负责人：
发现时间（带时区）：
关联Issue/PR：
基线commit / 原始head commit：
契约版本（前 / 后）：
涉及operationId / schema / Java完整签名：
原需求及复用现有接口的评估：
旧接口与新接口（包含单位、权限、错误与副作用）：
影响调用方、数据库、缓存及UI：
兼容性判断及未验证项：
原始证据位置与摘要（脱敏）：
决定：采用 / 复用 / 适配 / 撤回 / 内部化
决定者、时间、授权依据和对应commit：
迁移与回退计划：
实际命令、测试报告、失败与修复记录：
最终commit / 正式契约位置：
临时adapter负责人、删除条件及任务：
关闭依据：
```
