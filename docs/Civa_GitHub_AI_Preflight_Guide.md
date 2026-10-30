# Civa GitHub 协作、AI 预检与接口变更处理指南

版本：1.1　更新：2026-10-29　维护：EF

本指南依据同目录的 [PROJECT_DOC.md](PROJECT_DOC.md) v1.1，说明如何分工开发、检查代码、处理未经登记的接口及合并PR。架构文档定义“系统怎样构建”，本指南定义“团队怎样在同一架构上协作”。本文中的脚本、任务和CI是待工程实现的要求，不表示已经创建或运行。

当前文件存放在课程资料目录 `CW/code/docs/`。下文路径和命令均相对**最终Civa Git仓库根目录**；不要直接在课程资料目录执行工程预检。正式仓库将PROJECT_DOC.md置于根目录，本指南可放docs中，届时调整上面的相对链接。已有GitHub仓库继续使用，不重新创建三套工程。

## 1. 权威文件与协作原则

| 文件 | 职责 |
| --- | --- |
| PROJECT_DOC.md | 架构、技术基线、业务不变量、分工与验收规格 |
| contracts/openapi.json | 网络API、DTO、请求/响应、错误与鉴权的唯一权威来源 |
| contracts/public-surface.json | 手写公共Java入口、可空性、消费者及允许依赖 |
| contracts/generator-client.json、generator-server.json、toolchain.properties | 固定代码生成选项和版本 |
| contracts/changes/IC-*.md | 接口变更许可、原始证据、处理决定和验证记录 |
| docs/generated/CONTRACT_INDEX.md | 从正式契约生成的查询索引，不独立定义接口 |
| docs/decisions/ADR-*.md | 有依据的技术选择和必要版本调整 |
| docs/verification/BOOTSTRAP.md、OPEN_QUESTIONS.md | 实际验证证据、未实现项和规格疑问 |
| AGENTS.md | 给所有Coding Agent的操作约束，不能代替CI |

旧指南要求的PRODUCT_RULES、DATA_CONTRACT、UI_RULES、INTEGRATION_RULES、TEST_PLAN如已存在，可以保留为导航或测试说明，但必须引用上述权威来源，不能继续手工维护另一套字段、签名或流程。缺少这些旧辅助文件不构成阻塞；缺少当前架构必需的契约或公共接口则不能自行猜测。

每位成员使用自己的AI完成任务并理解、验证本人代码。AI自评、文档勾选和“能编译”均不证明全部业务正确。普通功能通过确定性检查集成；公共边界改变需要EF与受影响组的可追溯决定，不要求对每次业务PR逐行人工审查。

## 2. 固定分工与允许目录

| 成员 | Android目录 | 服务端业务包 | 核心责任 |
| --- | --- | --- | --- |
| A | feature-ac/recipe | recipe | 菜谱、收藏、归档；客户端读RecordsApi.listCookingEvents展示做过 |
| C | feature-ac/inventory | inventory | 标准食材、批次、到期、匹配及库存变动规则 |
| B | feature-bd/planning | planning | 手动计划、随机/推荐、锁定、预览；按本人偏好 |
| D | feature-bd/records | records | 做饭交互、烹饪快照、个人食记、热量和历史 |
| E | feature-ef、core-data | auth、household、profile、media、application | 账号、家庭、权限、统一访问、图片和跨模块事务 |
| F | app、core-ui、构建与CI | 构建与集成设施 | 公共UI、导航、首页、我的、布局和工程整合 |

Android目录列中的业务子目录是逻辑归属，实际Java源码位于对应模块src/main/java包内。接口契约由EF协调，AC/BD负责自己业务的真实后端读写和测试，不把全部服务端实现交给E。

依赖必须符合架构：app装配三个feature和公共模块；feature只能依赖core-ui、core-data、api-client，不能互相依赖；core-data依赖api-client和core-ui的ImageLoader；core-ui不依赖api-client/core-data；api-client是生成的纯Java库。后端业务只能通过正式service入口协作，不访问别组persistence。

客户端统一Java、XML Views、Groovy Gradle；具体版本以架构及锁文件为准，不再让各组分别选Java/Kotlin或Views/Compose。生成代码位于build/generated，禁止手改或提交。

## 3. 工程与日常开发环境

```text
civa/
  PROJECT_DOC.md / AGENTS.md / README.md
  settings.gradle / build.gradle / gradlew / gradlew.bat / gradle/
  app/ api-client/ core-ui/ core-data/
  feature-ac/ feature-bd/ feature-ef/
  contracts/openapi.json / public-surface.json / toolchain.properties
  contracts/generator-client.json / generator-server.json / fixtures/ / changes/
  server/                      独立Gradle工程和自己的Wrapper
  infra/compose.yaml
  scripts/requirements.txt / contract_guard.py / preflight.ps1 / preflight.sh
  docs/decisions/ / generated/ / verification/
  .github/CODEOWNERS / pull_request_template.md / workflows/ci.yml
```

server不使用includeBuild，不加载Android插件。它自行生成server/build/generated/openapi；根generateServerApi仅调用server的Wrapper，不再生成另一份。服务端测试无需Android SDK，Android构建无需数据库在线。

日常在各自电脑的Android Studio模拟器测试。自己的模拟器连接本机服务使用http://10.0.2.2:8080/；两个账号共享验收必须连接同一后端，不能用两份独立本地数据库冒充共享。学校设备用于后续实机验证。

提交两套Wrapper、构建/锁文件、业务源码、迁移、契约、测试和文档；排除local.properties、.env、密码令牌、私有媒体、缓存、.gradle、build及生成物。服务地址用CIVA_API_BASE_URL Gradle属性，数据库通过application.yml显式映射DB_*环境变量。compose --env-file不替本机bootRun设置变量。

FakeApiProvider只允许src/test，debug和release都接真实实现；未实现业务统一501 CONTRACT_NOT_IMPLEMENTED并在页面标明待接入。禁止用成功Mock完成正式演示或验收。

## 4. EF初始化流程与基线发布

沿用架构第15节四阶段，不再按旧指南把“开放并行开发”排在门禁验证前。

| 阶段 | 内容 | 完成证据 |
| --- | --- | --- |
| A 候选基线 | 完整契约、生成配置、公共面、模块及双方编译 | 候选快照和本地报告，尚未冻结 |
| B 可运行骨架 | 页面壳和九个真实认证/家庭operation | 两账号链路、导航手工结果、501清单 |
| C 集成检查 | 契约和依赖门禁、负例、脚本及CI | 本地必需检查通过，再由EF建立可信main并用测试PR验证 |
| D 并行实现 | AC/BD在同一基线实现业务，EF维护公共设施 | 每个PR运行阶段必需检查，最终全部业务验收 |

九个真实operation：register、login、logout、getMe、createHousehold、createInvitation、joinHousehold、getHousehold、listMembers。

A–C在EF初始化分支连续推进，每阶段留下本地提交和BOOTSTRAP记录，可以在同一会话完成。--bootstrap只用于本地显式初始化；普通PR CI不得自动开启，缺基线直接失败。

空仓库没有可信main时，EF本地审阅初始快照后建立受控种子提交；这是一次性信任初始化，不能宣称它由尚不存在的普通PR保护完成。随后测试PR验证门禁、失败拦截和CODEOWNERS。远程未验证时只称“本地骨架通过”，不能写“强制保护已生效”。

## 5. Issue和任务看板

继续使用现有GitHub Project；没有看板时，普通Issues足以起步，无需为开发额外搭管理系统。Status可用Ready/In Progress/In Review/Done/Blocked；Feature用AC/BD/EF业务域，Priority用P0/P1/P2。

接口变更状态以IC文件为准，看板仅展示或链接该状态，不再用另一套Draft/Agreed字段冒充授权。每个任务至少记录：

```text
Issue及负责人：
任务目标与验收结果：
基线commit：
允许修改的模块/业务包：
使用的operationId、schema、手写公共symbol：
输入/输出样例和错误情形：
是否涉及公共契约或迁移；对应IC：
实际运行检查及手工验证步骤：
未实现、未验证和依赖阻塞：
```

只有依赖的正式接口存在且语义确定，任务才进入Ready；内部实现细节可由负责人决定，不为私有辅助方法建立IC。

## 6. AGENTS约束摘要

以下内容用于生成或更新正式仓库AGENTS.md；不能覆盖已有无关规则。

```text
开始前读取PROJECT_DOC.md、contracts/openapi.json、public-surface.json及本任务IC。
在指定功能分支实现Issue，不另建工程，不覆盖其他人的工作。
复用生成API/DTO、公共主题和组件；feature不调用其他feature实现。
调用统一ApiCalls，认证走AuthRepository，图片走MediaClient；不直调Call.enqueue/execute。
Fake仅在src/test，未实现接口返回统一501，不能伪造成功。
公共字段/签名/路由/单位/权限/错误/导航/迁移变更先登记IC，不能自己批准自己。
正式许可从当前目标分支读取，不能把本分支ACCEPTED字段当作授权。
获取最新main并按统一merge流程集成；保留所有有效改动。
运行预检，记录SHA、命令、退出码、测试数和跳过数；缺环境标UNVERIFIED且非零退出。
不得删测试、扩大白名单、关闭CI或手改生成物制造通过。
规格影响权限、数据含义或公开契约的冲突写OPEN_QUESTIONS，只暂停依赖工作。
默认仅本地操作和任务范围内提交；推送、建PR或改远程规则须已有明确授权。
```

## 7. 开发与同步步骤

首次克隆实际统一仓库；已克隆的成员直接继续使用。开始前检查git status，存在他人或无关修改时先识别，不能覆盖、reset --hard或自动全部stash。

下面是按顺序执行的命令，每一步失败都停止后续步骤并解决实际原因；占位值需替换成真实值。

```powershell
git status --short
git fetch origin
git switch -c feature/123-recipe-search origin/main
```

编码后检查工作区、暂存区和分支完整差异：

```powershell
git status --short
git diff
git diff --cached
git diff --check
git diff --cached --check
```

只暂存本任务的明确文件，审阅暂存差异后做本地提交；不要默认git add .。具体提交路径由任务实际文件确定。正式预检针对committed HEAD，未提交改动先处理；本地提交只是检查快照，不代表允许合并或推送。

```powershell
git fetch origin
git merge origin/main
git status --short
```

发生冲突逐项理解两侧意图，禁止盲选ours/theirs。解决并提交后再运行检查。团队统一使用merge同步，改变为rebase需全组一致，不能擅自重写他人历史。

本地预检通过后，由EF或已经获得对应远程操作授权的成员推送指定分支、创建PR。推送前再次核对分支、工作区和本地报告所对应的headSha。修改源代码、测试、契约或构建后必须重新验证，不能沿用旧commit的绿色结果。

## 8. SHA与检查基线

预检必须记录三个不同值：

- targetSha：本轮最新目标分支提交（通常origin/main）。可信IC和治理规则从这里读取。
- headSha：待检查功能分支提交。
- baseSha：targetSha与headSha的merge-base，用于分析PR自身变更。

不能把初始化commit永久当作base。正式集成前应使目标主线进入当前分支；CI检查被集成的代码树并记录实际检出SHA，不能混淆GitHub临时merge commit与PR head。目标更新后刷新SHA并重跑。

```powershell
git rev-parse origin/main
git rev-parse HEAD
git merge-base origin/main HEAD
git diff --stat origin/main...HEAD
```

main push检查使用事件before/after（前值无效时明确要求审计基线），不能计算merge-base(main,main)后把空差异当通过。CI fetch-depth:0并确保目标SHA可取得；缺提交或缺授权基线失败，不能跳过。

## 9. 正常公共接口变更

新增跨组Java方法、HTTP端点、DTO字段、导航参数，或修改单位、舍入、权限、错误和事务语义都属于接口变更。仅模块内部私有辅助方法不登记。

1. 提出方先核对现有契约，说明为什么不能复用。
2. 准备明确的契约提案和调用方影响，生成IC-<Issue号>.md及摘要；只写“将来可能加个接口”不足以授权。
3. EF和受影响组核对业务语义。EF先把限定范围的ACCEPTED记录合入受控main，提案实现仍在功能分支。
4. 实现PR引用该IC，修改正式OpenAPI/公共面，从规范重新生成，更新调用方、迁移与测试。
5. 门禁比较实际变化与targetSha中许可的范围和digest；范围扩大重新登记。
6. 验证后登记最终commit及证据，关闭IC。临时adapter有负责人和删除条件，未清理不伪称已彻底关闭。

正常状态PROPOSED→ACCEPTED→IMPLEMENTING→VERIFIED→CLOSED，可REJECTED。Agent不得自称EF已接受。紧急修改仍采用先合授权、后合实现，不设“UNVERIFIED但自动合并”的例外。

## 10. 私自新增接口的归档与处置

发现未登记的公共变更时，阻止该PR集成，继续无关工作。`contract_guard.py draft --base SHA --head SHA`在明确输出目录创建UNREGISTERED/DETECTED草稿，不能覆盖旧IC或自动批准。未提交改动先由作者保存到自己的分支，不能替作者提交混杂工作。

归档保留target/base/head完整SHA、PR/Issue、发现时间、作者、旧/新声明、operation/schema/symbol、静态调用方搜索结果、脱敏diff和测试证据。Git提交保存原始代码；不把旧源码复制进src“归档”。分支可移动，不能只存分支名；CI artifact会过期，不能代替Git中的长期记录。发现秘密只记录泄露类型和位置，不复制秘密到IC。

机器元数据与架构一致：

```yaml
---
id: IC-123
type: UNREGISTERED
status: DETECTED
allowedOperations: []
allowedSchemas: []
allowedSymbols: []
contractDigest: null
baseSha: null
headSha: null
---
```

正文记录需求、授权依据、处置决定、迁移/回退方案、实际测试、修复commit及关闭依据。digest由工具生成，覆盖限定变更前后片段和传递引用，具体规范以架构§14.5为准；禁止手填哈希或把本分支自写ACCEPTED当许可。

| 判定 | 处理 |
| --- | --- |
| 内部辅助被误判 | 缩小可见性/内部化，记录NOT_PUBLIC并验证无跨模块调用 |
| 已有能力重复 | 迁回正式入口，移除重复运行时代码，保留历史证据 |
| 合理兼容新增 | 补正式IC授权、契约、生成与测试 |
| 必要破坏性变化 | 同一集成变更更新所有调用方；已发布客户端考虑版本或过渡 |
| 需要临时兼容 | 单向adapter委托唯一实现，禁止双写和两套算法 |
| 不应纳入 | 普通修复提交撤回，不能丢弃别人的有效工作 |

状态DETECTED→TRIAGED→ACCEPTED_FOR_REWORK/REJECTED/NOT_PUBLIC→FIXED→VERIFIED→CLOSED。已经进入main的错误，从当前main建修复分支；判断向前修复或小范围revert，不能强推重写历史。

Flyway初始V1__baseline.sql，后续VYYYYMMDDHHMMSS__组别_说明.sql，版本唯一，outOfOrder=false。共享DDL/数据语义变更必须IC；已应用迁移只追加纠正，不能删改旧文件。代码revert不等于数据回滚。个人开发库重建需明确核对数据库、备份及授权，不由预检自动drop。

## 11. 本地预检入口与退出规则

EF按架构生成scripts/preflight.ps1与preflight.sh。本指南不再提供旧版只运行三个Android任务的脚本，避免被误用为完整兼容证明。

脚本接口要求：显式接受目标分支或SHA及当前阶段；最终参数名称写入脚本help和README后保持一致。不能猜测现有脚本已有参数。stage至少区分bootstrap、feature、final；bootstrap只在EF本地初始化使用，普通PR硬禁。普通feature检查要求非main的干净已提交工作区；main CI使用单独push审计路径而非绕过检查。

| 顺序 | 检查内容 | 失败处理 |
| --- | --- | --- |
| 1 | 仓库、分支、工作区、target/head/base、当前阶段 | 不确定就失败，不检查一份代码却报告另一SHA |
| 2 | JDK17、两套Wrapper、Python≥3.11、PyYAML==6.0.2和阶段必需工具 | 版本不符/缺依赖非零退出 |
| 3 | 完整diff、凭证误提交、生成物、受控文件和IC范围 | 未登记变更归档并失败 |
| 4 | OpenAPI验证、生成、公共面和包依赖检查 | 不能用手改生成代码解决 |
| 5 | Android编译、单测、Lint | 任一失败停止，保留日志 |
| 6 | 独立服务端构建、真实PostgreSQL测试和运行时路由比对 | 测试库缺失不跳过、不替换H2 |
| 7 | 契约门禁自测、实际差异报告和阶段业务测试 | 必需套件跳过数必须0 |
| 8 | 复核HEAD及代码树未改变，汇总报告 | 源码变化使旧结果失效 |

目标任务：

```text
根Wrapper：validateContract generateClient generateServerApi checkContracts
根Wrapper：assembleDebug testDebugUnitTest lint
server Wrapper：test bootJar
Python：contract_guard.py门禁自测，以及针对实际target/base/head的契约比较
```

所有外部进程检查退出码；PowerShell的ErrorActionPreference=Stop本身不能代替检查原生命令LASTEXITCODE。Bash也必须传播管道中的失败。报告PASS/FAIL/UNVERIFIED；缺环境、无权限、必需套件跳过均不是PASS，完整预检非零退出。分组构建可在修复期间单跑，但提交前必须完成整套阶段必需检查。

格式检查仅当仓库实际配置后列为必需，不引入面向Kotlin的ktlint。Fake只在src/test，不为检查它强制执行要求HTTPS地址的release构建；扫描main/debug确认没有生产引用。模拟器导航/旋转是单独手工证据，不能从Gradle成功推定。

## 12. 检查命令与测试数据库

下列命令在生成工程后使用，不能在当前仅有文档时声称通过。正式脚本还需添加上节SHA、阶段和门禁检查，不以复制命令清单替代完整脚本。

```powershell
# Civa仓库根目录；任一步非零退出都必须停下
python --version
python -m pip install -r scripts/requirements.txt
.\gradlew.bat validateContract generateClient generateServerApi checkContracts
.\gradlew.bat assembleDebug testDebugUnitTest lint
# server目录；先将本终端环境变量设置为专用测试库
.\gradlew.bat test bootJar
```

macOS/Linux使用./gradlew，其余工作目录不变。Windows统一python，不在失败时悄悄切py。根generateServerApi委托server Wrapper，server目录也可独立构建。

测试用独立名称以_test结尾的数据库。测试在迁移/清理前校验URL并核对current_database，错库、缺库或连不上即失败；Flyway cleanDisabled=true。清理只在已核验测试库回滚事务或TRUNCATE测试表。不要用本人的正常开发库跑破坏性测试。

DB_URL、DB_USERNAME、DB_PASSWORD、CIVA_MEDIA_DIR由当前shell或已说明的启动配置提供；README按架构列出application.yml占位符。Docker Compose的.env只影响对应容器，不自动传入本机Gradle进程。CI用postgres:16服务容器、独立测试库及仅该job使用的临时凭证，不上传环境变量转储。

## 13. GitHub Actions与仓库保护

工作流统一为.github/workflows/ci.yml，分清Android、服务端、契约及最终汇总检查；不要保留仅Android编译成功就允许合并的旧android-ci门禁。生成时验证实际任务及状态名，再配置必需状态，不能引用不存在的名称。

| CI部分 | 实施要求 |
| --- | --- |
| 触发 | PR到main；main push也检查，分别使用正确基线 |
| 权限 | permissions: contents: read；无部署凭证、无写token；checkout关闭persist-credentials |
| 源码 | fetch-depth:0，取得精确target/head；记录实际检出或合并测试SHA |
| 可信规则 | 检测器与IC授权来自可信target；PR源码和可信规则分目录，禁止PR通过import路径替换检测器依赖 |
| 构建 | JDK17；Android安装基线SDK，用根Wrapper；server用自己的Wrapper |
| 数据库 | postgres:16独立测试服务；数据库名以_test结尾，启动前健康检查 |
| 门禁 | schema及签名差异、依赖边界、Spring路由双向比较、IC范围/digest、自测负例 |
| 汇总 | 只有全部必需job成功且无必需测试跳过才PASS；failed/cancelled/skipped/missing均不得变绿 |
| 证据 | 测试XML、Lint、门禁JSON/Markdown、阶段清单；上传前脱敏；失败也尽量保存日志 |

普通PR禁止--bootstrap。不能用pull_request_target带凭证执行PR代码。读取可信脚本并不能单独抵抗PR修改workflow本身，仍需要受控路径的强制审核。

main保护应配置：必须PR、实际必需检查、按团队要求更新主线、禁止强推及未经许可绕过；受控文件必须CODEOWNERS审核，开启新提交撤销旧批准。实际owner使用具有权限的EF账号/team，不能写不可解析的“EF”占位符。受控范围与架构一致：.github、scripts、contracts、AGENTS.md、PROJECT_DOC.md及根/server构建配置。

CODEOWNERS文件本身只表达归属，不等于阻止合并。套餐或权限不支持强制状态/owner审核时，记录“提示型检查”，EF按实际权限控制合并或人工核验。不要为了获得免费功能擅自改变仓库公开性。

EF发布初始基线后用测试PR验证：正常变更可通过；未登记API、越界调用、绕路Controller、修改白名单自我授权均被拦截。检查失败不能靠把必需job变为可选来解决。CI不替代代码贡献与业务理解。

## 14. 阶段测试矩阵

| 范围 | 责任 | 通过时机 |
| --- | --- | --- |
| 规范验证、双方生成、公共面和依赖、门禁负例 | EF/F | 骨架C及后续每次PR |
| 九个真实账号家庭接口、成员查询与非成员拒绝 | EF/E | 骨架B–C |
| ApiCalls旧回调隔离、离线、Fake排除、501清单 | EF/E/F | 骨架C |
| 食材标准、trackStock、数量/单位、批次与匹配 | AC | 相关功能PR及最终集成 |
| 计划查询21天、生成保存14天、锁定及原子提交 | BD/B | 相关功能PR及最终集成 |
| 做饭两批次扣减、并发不足库存、相同key重试 | AC+BD/D+EF/E | 做饭功能PR及最终集成 |
| 食记隐私、快照、热量、周月汇总 | BD/D | 相关功能PR及最终集成 |
| 移除成员、权限撤销、缓存清理 | EF/E | removeMember实现及最终集成 |
| PendingWrite进程恢复、相同key核实、不自动重放 | EF/E+BD/D | 相关功能PR及最终集成 |
| 模拟器导航、宽窄布局、旋转、字体与键盘 | 各页面负责人、F集成 | 页面PR手工证据；最终完整流程 |

骨架未实现的业务可以501并列清单，不能把这些功能对应的未运行测试写成通过；也不要把所有未来测试提前放进骨架必需套件导致无法判定。真实App交付前全部业务必须验收，不再保留核心功能501。

客户端旧回调和网络错误用MockWebServer+可控主线程Executor测试；ViewModel用core-testing及SavedStateHandle测试旋转相同key、不重复发送。进程死亡另测PendingWrite，重新登录同账号后才能核实；普通草稿允许丢失不代表已提交key也可丢失。

## 15. 每次预检需要特别检查的实现约束

- A通过RecordsApi展示做过；AC提供唯一库存匹配，BD排序不复制算法。
- feature调用ApiCalls，不能直调Retrofit.enqueue/execute；认证只经AuthRepository，媒体只经MediaClient，feature拿不到token或OkHttp媒体类型。
- 响应nullable字段required；请求nullable字段optional且缺失等于null；未知请求字段拒绝，fieldErrors固定数组。数字/时间格式直接遵守OpenAPI，不在组内另改。
- x-idempotent接口的key、规范payload和PendingWrite恢复一致；重试不能重压缩出另一份上传字节，取消请求不等于服务器回滚。
- 库存与CookingEvent同事务、防重由数据库保证；个人食记不扣库存，未知热量不记零；共享写入遵守架构统一锁顺序。
- SessionStore和ApiCalls在主线程回调执行时再次核验generation；换账号清缓存，PendingWrite独立且仅原账号可访问。
- core-ui不接收网络DTO；共享资源civa_、各组ac_/bd_/ef_，ResourceName为Lint error。600dp导航切换及详情显示由AppNavigator管理。
- Flyway迁移版本唯一；已应用文件不重命名或修改；授权和数据变更证据齐全。

上述是预检重点，具体业务规则仍以架构和正式契约为准，本指南不复制维护另一份API字段表。

## 16. 可复制的AI Preflight提示词

```text
你现在对Civa当前功能分支执行AI Preflight，不新增无关功能。
读取PROJECT_DOC.md v1.1、AGENTS.md、contracts/openapi.json、public-surface.json、本任务IC和BOOTSTRAP。
1. 确认Issue、负责人、允许范围及当前阶段；查看工作区，不覆盖其他人改动。
2. 获取最新origin/main，按merge规则集成。记录targetSha/headSha/merge-base，检查完整已提交差异；工作区不干净先说明，不能遗漏未提交文件后宣称整体通过。
3. 检查公共契约/路由/DTO/导航/错误/单位/权限/数据库语义变化。授权从targetSha读取。
4. 发现未登记接口，生成UNREGISTERED/DETECTED草稿、保留原始commit和脱敏证据；不要自己批准，不删除别人分支，不复制旧源码进src。
5. 通过统一脚本运行全部阶段必需检查：规范与生成、公共面、Android构建/单测/Lint、独立server构建及PostgreSQL测试、路由一致性、门禁自测及实际diff。
6. 验证Python/JDK与测试库安全；缺环境、缺授权或必需套件跳过就UNVERIFIED/失败，不能用Mock或H2替代真实验收。
7. 修复本任务引入的问题，重新执行受影响及最终必需检查；不得修改检查规则自我放行。
8. 规格冲突写OPEN_QUESTIONS，暂停依赖部分并继续无关工作。
9. 输出实际命令、退出码、测试计数/跳过数、报告路径、SHA、IC处置、未实现API、手工检查、剩余风险。
10. 分开报告本地预检、远程CI和仓库保护状态；没有权限或未运行就明确写未验证。
默认只进行本地操作和任务范围内提交；推送、创建PR或改变远程规则须当前会话已有明确授权。
```

## 17. PR模板与合并判断

生成.github/pull_request_template.md时采用以下内容，检查框必须有证据支持。

```markdown
## 目标与范围
Issue：
负责人/组别：
涉及模块、业务包与operationId：

## 基线与契约
阶段：bootstrap / feature / final
目标targetSha：
功能headSha：
差异baseSha：
实际测试代码树/commit：
IC编号及target分支中的许可位置（无变化写不适用）：
公共契约、生成选项或迁移变化：

## 真实检查结果
命令 | 退出码 | 测试数/跳过数 | 报告
（填写实际结果，不能只写AI认为通过）

## 功能证据
操作、预期、实际：
模拟器导航/旋转/宽窄窗口证据：
尚未实现的operationId/501：
仍未验证的内容和风险：

## 集成状态
本地预检：PASS / FAIL / UNVERIFIED
远程CI：PASS / FAIL / UNVERIFIED
仓库保护：已验证强制 / 提示型 / 未验证
新提交后是否已重跑：
```

只有当前代码、当前目标基线的必需检查通过，IC合法且受控变更完成规定审核，才满足合并条件。PR提交不代表允许合并；可以创建明确标注阻塞的草稿PR用于协作，但必须先有远程操作授权，且不得绕过失败检查。

检查报告放build/reports，原始代码证据留Git历史，长期决定留contracts/changes。不要为把报告提交进Git而隐式改动已检验代码；如补文档提交导致head变化，最终CI仍需重新运行并绑定新SHA。

## 18. EF日常维护与最终验收

EF维护统一版本、生成器、公共入口和检查规则；业务组负责自己的实现和测试。每次集成检查未实现清单、OPEN_QUESTIONS、未关闭IC和临时adapter，及时解决跨组依赖。

最终验收至少包括：另一电脑干净克隆构建；真实后端和独立测试库可复现；两模拟器不同账号共享一致且个人数据隔离；所有业务必需测试通过、跳过为0；核心功能无501；导航、旋转、宽窄窗口和后续实机结果可追溯；数据库迁移、接口异常和临时兼容的处置有结论。

本文只更新协作规程，不代表脚本、workflow、分支保护或上述检查已实际执行。GitHub规则依据及技术版本来源统一见PROJECT_DOC.md第17节，实施时核对当前仓库权限和真实环境。
