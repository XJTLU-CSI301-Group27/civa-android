# C 库存模块开发与验证交接

更新：2026-11-22，Asia/Shanghai。记录 C-V01 后端实现、接入及验证。

## taskc_version5：最终保留源码

保留冻结源码中的输入、搜索排序、分类与提示优化；详情见 [taskc_version5](c-history/taskc_version5.md)。
本次构建通过，Android 47 项报告无失败，lint 无错误、19 条警告。此前完整 UI 回归的筛选切换检查未通过记录仍保留，
未加入主目录的筛选清空草稿，也未重新安装 APK。五阶段索引见 [版本记录](c-history/README.md)。

## taskc_version4：汇总、单位与分类

同标准食材与单位合并卡片，详情保留各批日期；数量同行 g / kg / pcs、常见食材分区、用尽确认及零库存隐藏。
完整对照与验证见 [taskc_version4](c-history/taskc_version4.md)。本次 Android 45 项、服务端 47 项报告无失败，
lint 无错误、19 条警告；未重新运行旧版 UI 点击专项。下方保留此前版本说明。

## taskc_version3：入口与英文录入

修复添加入口状态提示，支持直接英文名称/别名及新标准食材创建；同时接入本阶段首屏完整冰箱与窄屏详情调整。
差异与验证见 [taskc_version3](c-history/taskc_version3.md)。本次服务端 47 项、Android 35 项报告无失败，
构建通过，lint 无错误、20 条警告；未重新运行旧版 UI 点击流程。下方保留已上传版本的阶段说明。

## taskc_version2：初步前端

本阶段在第一版后端上增加原生冰箱外壳、冷藏/冷冻开关门、批次数量、添加及消耗表单。
与已上传第一版的差异、历史截图和本次验证见 [taskc_version2](c-history/taskc_version2.md)。
操作说明见 [C_ANDROID_UI.md](C_ANDROID_UI.md)。本次 Android 构建及契约检查通过，30 项单元测试报告均无失败，lint 无错误、9 条警告；未重新运行旧版 UI 点击流程。
下述后端结果保留为第一版阶段记录。

## 第一版后端交付与范围

本次完成 Issue #9 的七个库存后端接口及包内库存分配计算，真实 PostgreSQL 测试已通过。没有实现 Android 库存页面，没有实现做饭最终扣减事务，不能据此宣称整个 C 平板功能已完成。

### 完成情况一览

| 工作 | 当前结论 |
| --- | --- |
| 标准食材查询及别名搜索 | 已实现并验证 |
| 库存批次录入、查询、位置与到期日期修改 | 已实现并验证 |
| 同种食材分批次、临期与过期筛选 | 已实现并验证 |
| 消耗、丢弃、数量修正及操作记录 | 已实现并验证 |
| 家庭权限、幂等、并发版本和负库存保护 | 已实现并验证 |
| 菜谱库存匹配和内部批次分配计算 | 已实现并验证，只读计算 |
| C 后端代码与测试说明 | 本文件及 TEST_PLAN.md 已维护 |
| Android 库存页面与平板操作验收 | 未完成 |
| 分配能力对 BD/EF 的公共接入 | 提案待审阅，当前仅包内实现 |
| 最终确认做饭的原子扣减与烹饪记录 | 未完成，需 D/E 统一事务接入 |

关联任务：[Issue #9](https://github.com/XJTLU-CSI301-Group27/civa-android/issues/9)。

原始实现基于 `7508531`；本版基于主线 `a0943b1` 接入，保留 BD 已有代码、字典迁移、菜谱读取及导航。没有更改 OpenAPI、数据库结构或 Android 页面。

## 已实现行为

| 操作 | 行为 |
| --- | --- |
| listIngredients | 登录后读取活动标准食材；NFKC、空白及大小写标准化后按别名包含查询；别名返回同一标准 ID；稳定分页 |
| createInventoryBatch | 保存标准食材、数量、标准单位、位置和可空到期日；同食材每次新建独立批次；幂等重试返回原结果 |
| listInventoryBatches | 仅当前家庭；按到期日升序、未知日期最后、createdAt/id 升序；支持食材和到期状态过滤及分页 |
| updateInventoryBatch | 只改位置和到期日，必须匹配 expectedVersion，成功递增 version；不能通过此接口改数量 |
| changeInventory | CONSUME / DISCARD 必须减少；CORRECT 可增减；拒绝负数和超精度；同事务更新余额、版本和 InventoryMovement |
| listInventoryMovements | 仅当前家庭，可按批次过滤；createdAt 降序、id 升序；有 before/after、原因、操作者及请求 ID |
| matchRecipes | 同一只读快照内读取菜谱与库存；各菜独立匹配，不预留或扣库存 |
| 内部 preview | 返回既有 CookingPreview/CookingAllocation/MatchLine；最早到期批次优先，缺货明确列出；不写库存 |

数量使用 BigDecimal 和既有 G/ML/COUNT，不使用浮点数。输入最多三位小数；需求按份数缩放并向上取三位小数；输出去掉多余尾零。未知日期保持 null。

到期判断使用家庭时区：EXPIRED 是早于今天；SOON 是今天至三天后（含端点）；VALID 是全部未过期及未知到期日，包含 SOON；ALL 不按日期排除。数量为零的批次仍可在库存列表看到，但不参与匹配。

只有 trackStock=true 的配方行参与匹配。可用数量合并同一标准食材及单位的未过期正库存。ENOUGH 表示足量；MISSING 同时覆盖缺少和部分不足，通过 available/required 区分，不增加新的 PARTIAL 枚举。历史未知需求保留 UNKNOWN，不当作零。

coverage 为足量行数/需库存行数，HALF_UP 到三位，无需库存行时为 1。earliestExpiry 为相关可用批次最早已知到期日。分配按 expiresOn（未知最后）、createdAt、batchId 排序；shortages 非空时部分分配只是解释结果，不是可提交扣减的许可。

## 安全与并发

所有家庭读写核验活动成员；从未加入者返回 404 NOT_FOUND，被移除者返回 403 HOUSEHOLD_ACCESS_REVOKED。批次查找同时限制 household_id 和 batch id，不能用本家庭路径修改其他家庭批次。

新建与数量操作复用 CanonicalRequestDigest、IdempotencyExecutor、JsonbCodec。重复 key 同内容只执行一次，异内容返回 409；重放仍检查成员权限。

写入遵守统一家庭锁顺序；锁定 household 后核验成员，再读取/锁定批次。版本不符返回 409，不覆盖并发更新。锁等待上限为本事务内 5 秒；PostgreSQL 55P03 映射 503 TEMPORARILY_UNAVAILABLE 和 Retry-After: 1，整个事务回滚。

## 文件与学习顺序

### 初学者如何观察效果和判断成功

先用浏览器打开 `server/build/reports/tests/test/classes/edu.xjtlu.civa.server.inventory.InventoryHttpIntegrationTest.html`。当前实际报告为 11 tests、0 failures、0 ignored、100% successful；下面每一行应为 passed。它展示接口/数据库验收结果，不是 Android 页面。

实现过程是：阅读现有接口和数据库 → 写统一输入校验 → 写库存查询与批次保存 → 写事务内数量变动及记录 → 写只读菜谱匹配和批次分配 → 从原占位控制器移走七个路由 → 构建并验证 → 更新文档。没有另建工程或数据库表。

以 `quantityChangesAreTraceableIdempotentAndNeverNegative` 为例，真实测试依次执行：

1. 创建数量 4 的批次。
2. 提交 CONSUME，要求剩余数量 3；同一个请求再发一次，返回同一条变动记录。
3. 用旧版本修改，预期 409；提交负数、消耗后反而增多或不减少，预期 400。
4. 提交 DISCARD，剩余 2。
5. 提交 CORRECT，修正为 5。
6. 查询接口应只有 3 条成功数量变动；直接查真实数据库，余额应为 5.000。

对应断言：`assertEquals(期望值, 实际值)` 和 `.andExpect(...)`。例如 `.andExpect(status().isBadRequest())` 表示“这个非法请求应该被拒绝”；得到 400 是本项测试成功，不是功能出错。若非法请求反而成功，断言会失败，报告就不再是绿色 passed。

不同层次的证据不能混用：编译/bootJar 成功说明程序能构建；纯单元测试说明已覆盖的计算边界正确；HTTP 集成测试通过模拟请求进入真实认证/控制器并连接 PostgreSQL，验证接口响应和保存结果；平板点击、横竖屏和页面交互必须等 Android 页面接入后单独验收。测试通过只覆盖已列出的场景，不等于绝对没有任何缺陷。

| 文件（均在 server/src 下） | 阅读重点 |
| --- | --- |
| main/java/.../inventory/IngredientNames.java | 为什么搜索前统一全角、大小写和空白；同义词仍靠字典，不靠字符串猜测 |
| main/java/.../inventory/InventoryRules.java | 数量、日期、分页、版本及精度校验 |
| main/java/.../inventory/InventoryController.java | 七个现有 HTTP 接口如何取得认证用户并调用业务层 |
| main/java/.../inventory/InventoryService.java | 查询、权限、批次读写、变动记录和共享幂等事务 |
| main/java/.../inventory/InventoryCalculator.java | 纯匹配与分配算法，不访问数据库、不修改传入库存 |
| main/java/.../inventory/InventoryMatchingService.java | 一次一致快照读取真实菜谱与库存，再调用算法 |
| main/java/.../inventory/InventoryConfiguration.java | 复用现有连接、事务、JSON 和幂等组件进行装配 |
| main/java/.../inventory/InventoryErrors.java、InventoryFailure.java | 不泄露数据库详情的业务错误映射 |
| test/java/.../inventory/*Test.java | 输入边界、匹配、HTTP、权限、幂等、并发及真实数据库测试 |

UnimplementedController 移除了 InventoryApi 和对应七个占位方法，避免重复路由。SecurityFoundationIntegrationTest 中食材接口的断言从旧 501 更新为真实 200 和数组检查，原认证和媒体占位检查保留。

## 构建与验证

需要 JDK 17、项目 Wrapper 指定的 Gradle 8.13 和独立 PostgreSQL `_test` 数据库。
数据库连接由 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 提供，不能指向应用日常使用的数据。

在 `server` 目录执行：

```powershell
.\gradlew.bat test unitTest testCandidateContract bootJar --offline --no-daemon
```

`unitTest` 不依赖数据库；完整 `test` 会先检查数据库名称和实际连接。
测试包含原项目认证、家庭、幂等、路由覆盖，以及 C 库存测试，不能将全部用例归为 C 新增。

报告在本机运行后生成，不提交 Git：

- `server/build/reports/tests/test/index.html`：完整服务端测试。
- `server/build/reports/tests/test/classes/edu.xjtlu.civa.server.inventory.InventoryHttpIntegrationTest.html`：C 的 11 项 HTTP/数据库测试。
- `server/build/reports/tests/unitTest/index.html`：独立单元测试。
- `server/build/reports/tests/testCandidateContract/index.html`：候选契约测试。

原始开发阶段记录：完整服务端 37 项、独立单元任务 18 项、候选契约任务 4 项均无失败，`bootJar` 成功。
任务间存在重叠，不能相加。基于最新主线的重新验证结果见 [C-V01 版本记录](c-history/C-V01-库存后端基础实现.md)。

测试通过实际认证和 HTTP 控制器连接 PostgreSQL；fixture 按自身 UUID 隔离清理。
生产代码读取真实数据，没有硬编码假库存或假成功响应。

## 已知限制及后续接口

1. 本版尚无原生冰箱页面，不包含 Android 录入、开门及批次详情交互。
2. 库存分配仅为包内只读计算；公共接入提案 `contracts/changes/IC-9.md` 仍为 PROPOSED。
3. `previewCooking` 和 `confirmCooking` 由 D/E 提供；最终提交需要统一事务重验权限、版本、单位、过期、份数及分配数量。
4. 复用 BD 主线的标准字典迁移；本版不新增字典、数据库表或食材创建接口。
5. 拍照/OCR、推荐排序及个人食记不属于本版 C 后端交付。C 提供库存匹配，B 决定推荐，A 提供菜谱，D/E 协调做饭。
