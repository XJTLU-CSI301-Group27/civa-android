# taskc_version4：食材汇总、单位选择与自动分类

## 版本定位

在已上传第三版 `3d6847c2ac70242e38014ba6c5c1795baaa16b01` 基础上，优化同类食材显示、单位选择和冰箱分区。
源码依据为保存的 `C_FINAL_FROZEN.bak/earlier-grouping-41-source` 阶段；原归档 SHA-256：
`d8d266a2f35499f4186569e1561695f944a34c758bd4b9138200f6b6850dd675`。
本版只恢复该阶段的 C 页面、测试及预览工具，保留主线已有 BD / EF 功能。

## 相较第三版的优化

| 场景 | 第三版 | 第四版 | 用户可观察结果 |
| --- | --- | --- | --- |
| 同一食材再次入库 | 不同批次分别占一个图标 | 按标准 ingredientId + unit 汇总为一张食材卡片 | 鸡蛋已有 10 个再录入 2 个，全部视图显示 12 个 |
| 分批日期 | 从各批卡片分别查看 | 多批食材点开先显示批次清单 | 各批保留入库日期和时分秒、到期日、位置及数量 |
| 选批消耗 | 对一个批次扣减 | 汇总卡片内选择具体批次，再复用原扣减规则 | 扣减选中批次后，总数与批次数量一起刷新 |
| 多个储存位置 | 同食材可能在多个位置重复占卡片 | 合计只显示一次，选择首个已知冰箱层 | 各批实际位置仍在批次清单中保留 |
| 数量与单位录入 | 单位单独占一行，下拉选择 | 数量输入框右侧放置 g / kg / pcs，液体按条件提供 ml | 表单更紧凑，可直接切换同维度单位 |
| 牛肉等重量食材 | 沿用标准 G | 已识别为 G 的食材选 g，并允许 kg 输入 | 1.25 kg 保存为 1250 G，不新增 KG 库存单位 |
| 苹果等计数食材 | 旧测试字典曾把 apple 定义为 G | COUNT 标准苹果选 pcs；专用测试字典修正 apple 别名 | 已有按克库存仍保留原 ID、G 单位及数量，不直接改成个数 |
| 未收录名称 | 明确选择新食材单位 | 保留明确选择，不猜测质量与个数换算 | 新英文名称仍可创建；不把所有未知水果强制转为 COUNT |
| 冰箱分类 | 英文水果识别范围有限 | 扩大 pear、banana、grapes 等水果及常见蛋奶、肉、海鲜规则 | fresh pear 归冷藏水果，beef 归冷冻肉类，egg 归蛋奶 |
| 最后一次消耗 | 保存 0 后仍显示用尽卡片 | 归零前弹出确认，支持返回修改用量 | 确认前不写入；确认后用尽批次从冰箱显示中消失 |
| 用完后补货 | 旧 0 卡片可能干扰显示 | 加载时排除 0，保存后选中新批次 | 鸡蛋用完后再加 2 个，显示新库存 2 个，刷新不会回到旧 0 |
| 过期与可用量 | 批次级提示 | 汇总详情区分实物合计与未过期可用量 | 含过期批次时明确提示，不把过期库存算为可食用数量 |
| 预览测试数据 | 随机创建名可能留在测试字典 | 测试结束按明确 UUID 和创建记录清理自有 fixture | 不仅凭名称后缀删除，不影响用户家庭或他人引用 |

## 汇总与批次数据规则

`InventoryGroups` 是 feature-ac 内部的只读显示计算，不合并数据库行，不修改批次 ID。
同一标准食材、同一单位的当前筛选结果才可相加；不同 ID 或不同计量维度保持分开。
使用 BigDecimal 精确合计，0.1 + 0.2 显示 0.3；未知数量保持未知。

批次按到期日、入库时间、ID 排序。点选多批食材先查看批次列表，选择一批后显示当前批次与总体数量。
每层首屏最多展示三种食材，其他内容可从层标题进入。
全部视图的实物总量可能包含过期批次，详情另外提供未过期可用量；临期/过期视图只汇总匹配的批次。

隐藏零库存只影响冰箱展示，后端保留旧批次、消耗和丢弃等变动记录。
全部用尽确认区分当前批次与所有存储；存在其他剩余批次时，仍保留该食材的剩余卡片。

## 单位推荐的实际依据

单位首先来自标准食材资料，而非仅靠名称覆盖历史数据。
标准字典中的 apple 为 COUNT 时选 pcs；beef 为 G 时选 g。未收录食材仍由用户明确选择单位。
若标准记录本身为 G，不把它或已有批次改成 COUNT；质量与计数之间不进行推测换算。
ml 仅在相应液体输入场景出现，不把未知瓶容量推算成毫升。

`seed_inventory_demo_catalog.py` 的苹果别名修正只允许显式选择的 `_test` 数据库；没有生产迁移或启动时自动改数据。
测试辅助工具保留旧 G 苹果 ID、单位、批次与变动，将测试字典的 apple / apples 指向 COUNT 标准苹果。

## 实现文件与步骤

1. 新增 `InventoryGroups.java`，在已有批次之上形成只读分组与数量合计。
2. `InventoryFragment` 的冷藏和冷冻卡片读取分组总量；点开后选择批次，显示家庭时区下的日期。
3. `InventoryPresentation` 增加 kg 输入换算、零库存显示规则及水果分类。
4. `InventoryViewModel` 加载时隐藏用尽批次，清除已经失效的选中详情。
5. 归零确认发生在 `InventoryFragment.quantityDialog`，确认后才调用原有写入逻辑。
6. 现有 API、OpenAPI、单位枚举、数据库表、依赖和 app 导航保持不变。

主要代码与测试：

- `feature-ac/.../inventory/InventoryGroups.java`、`InventoryGroupsTest.java`
- `InventoryFragment.java`、`InventoryPresentation.java`、`InventoryViewModel.java`
- `IngredientChoicesTest.java`、`InventoryPresentationTest.java`
- `feature-ac/src/main/res/values/ac_ids.xml` 及静态预览资源
- `scripts/test_inventory_ui.py`、`test_inventory_catalog.py`、`seed_inventory_demo_catalog.py`
- `scripts/inventory_test_fixtures.py`、`open_inventory_preview.py`

## 原阶段界面与验证记录

以下为本阶段保存的真实截图，不覆盖第一至三版图像：

- [宽屏食材汇总](../verification/screenshots/taskc_version4/c-fridge-grouped-wide.png)
- [宽屏批次清单](../verification/screenshots/taskc_version4/c-fridge-grouped-batches-wide.png)
- [窄屏食材汇总](../verification/screenshots/taskc_version4/c-fridge-grouped-phone.png)
- [窄屏批次清单](../verification/screenshots/taskc_version4/c-fridge-grouped-batches-phone.png)

原阶段记录包括：鸡蛋 10 + 2 汇总、日期与位置保留、选批扣减及刷新；归零确认前无写入、返回修改、确认后隐藏；
用完后重新录入显示新库存；数量和单位同行、kg 精确换算、apple COUNT 与 pear 水果层。
这些是原阶段留存证据，本次没有运行旧版模拟器 UI 点击流程。

## 本次重新验证（2026-11-23）

JDK 17、Gradle 8.13、SDK 36；独立 PostgreSQL 数据库 `civa_v04_verification_test`。

| 检查 | 实际结果 |
| --- | --- |
| Android assembleDebug | 构建成功，未安装 |
| Android 本地测试 | 45 项报告无失败：app 3、core-data 18、core-ui 1、feature-ac 19、feature-bd 4 |
| C 前端测试变化 | 从第三版 9 项增加到 19 项；包括 5 项 InventoryGroupsTest |
| lint | 0 错误；app 2 条警告，feature-ac 17 条警告 |
| 候选客户端测试 | 1 项通过报告；未变化任务可复用 UP-TO-DATE 结果 |
| OpenAPI 校验与契约检查 | 通过，公共 HTTP / schema 与第三版一致 |
| 服务端完整 test | 强制重跑 47 项，0 失败、0 错误 |
| 服务端 unitTest / 候选契约测试 | 强制重跑 18 / 4 项通过，任务有重叠，不能相加 |
| 服务端 bootJar | 构建成功 |
| Python 工具测试 | 17 项通过 |
| 苹果测试字典专项 | 通过：COUNT 别名、旧 ID/单位及库存/变动保持、重复修正幂等 |
| Java 格式 / Git 空白检查 | 通过 |

实际命令：

```powershell
# 根目录；配置 JDK、SDK 和 Gradle 缓存
.\gradlew.bat validateContract checkContracts assembleDebug testDebugUnitTest lint :api-client:testCandidateClient --offline --no-daemon
python -m unittest discover -s scripts -p 'test_*.py'

# server 目录；配置专用测试库连接及 CIVA_PSQL
.\gradlew.bat test unitTest testCandidateContract bootJar --rerun-tasks --offline --no-daemon

# 根目录；配置 PGHOST、PGPORT、PGUSER、PGPASSWORD、PGCLIENTENCODING
python -X utf8 scripts/test_inventory_catalog.py --psql D:/CSI301Eating/build/postgresql/pgsql/bin/psql.exe --test-db civa_v04_verification_test
java -jar <google-java-format-1.27.0.jar> --dry-run --set-exit-if-changed <C前端Java与测试文件列表>
git diff HEAD --check
```

数据库首次连接因本机测试实例关闭而失败；恢复该实例后使用独立第四版库。
常规 Gradle 测试任务最初显示 UP-TO-DATE，因此增加 --rerun-tasks，实际执行全部 12 个服务端任务后取得以上结果。

## 当前验证边界

本次未安装第四版 APK、未重新运行 UI 点击专项、未做 Xiaomi 真机验收。
lint 19 条警告仍保留。服务端实现未修改，原生前端、脚本和单元测试来自本阶段保存的源码。
测试工具只操作明确选择的专用测试库；当前最终版源码、APK 及用户预览库存保持不变。
