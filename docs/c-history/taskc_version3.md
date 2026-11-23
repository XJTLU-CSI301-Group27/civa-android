# taskc_version3：添加入口与英文食材录入修复

## 版本定位

基于已上传第二版 `312d76a358279ef3e2a0c4bf406f18e2173dcd50`，修复添加食材入口无响应、英文名称无法直接输入保存的问题。
业务源码恢复自历史项目树 `007fcd7ad225365cbd17acaf008629cd1d6153dc`；保留第二版已经接入的 BD / EF 功能。
本文只记录本阶段已实现内容及实际验证，不列后续开发计划。

## 相较已上传第二版的优化

| 场景 | 第二版行为 | 第三版优化 | 可观察结果 |
| --- | --- | --- | --- |
| 点击添加食材 | 缺家庭、加载或待确认状态下静默禁用 | 保留入口，点击后明确说明当前原因 | 缺家庭时可进入家庭设置；加载或待确认时解释状态 |
| 名称输入 | 自动补全下拉框；保存强制要求选择列表名称 | 普通文本框，直接输入英文名称或已有英文别名 | 输入 beef / egg，不点击候选也可复用标准 ID 保存 |
| 未收录英文食材 | 没有对应标准 ID 时不能添加 | 用户明确选择单位后创建标准食材，再保存家庭库存 | 任意有效新英文名均可录入，不伪造 ingredientId |
| 名称搜索 | 部分查询依赖别名行 | 前端名称与别名搜索，服务端同时匹配标准名称 | 没有重复别名行的标准名也能检索 |
| 重复候选 | 同名记录、技术别名可挤占输入区域 | 按名称与单位折叠候选，优先家庭已有 ID，隐藏 c_ui_ 技术别名 | 最多显示三个辅助候选，不强制点选 |
| 已有单位 | 需从列表选择后才同步 | 已有名称唯一匹配后同步其标准单位 | 已有 G 牛肉保留克，COUNT 鸡蛋保留个，不修改旧单位 |
| 创建与库存保存 | 单一库存请求 | 标准食材创建和批次录入分步提交，分别保留待确认请求 | 只创建成功不伪称库存已保存；重复请求复用原 key |
| 首屏空间 | 整页纵向布局，冷藏较大 | 缩减顶部装饰，完整冰箱占首屏，冷藏冷冻等高 | 冷冻区不再排到必须滑动的位置 |
| 窄屏详情 | 详情追加在冰箱下面 | 窄屏独立详情弹层，宽屏保留右侧详情 | 查看批次不挤掉冷冻区域 |
| 冰箱外观 | 初步门板及分层 | 加入渐变门板、温度、到期状态配色及局部样式 | 更贴合原冰箱 demo 的外观 |
| 食材较多 | 页面随批次数量变高 | 每层限制首屏展示数量，点击层名查看其余批次 | 冰箱整体高度稳定 |
| 数量表单 | 保存错误反馈有限 | 数量、日期及提交错误在表单内反馈，保留取消与预计余额 | 非正新增量、超量消耗及错误日期被拒绝 |
| 操作记录 | 仅接口可查询变动 | 读取真实最近变动并展示前后余额 | 刷新后仍从服务器读取操作结果 |

## 实现流程

1. `InventoryFragment.requestAddFood` 根据家庭、加载、恢复和待确认状态给出提示。
2. `IngredientChoices` 统一名称查找、别名匹配、候选去重及同名不同单位区分。
3. 文本框直接解析已有标准食材；未收录名称要求选择 G / ML / COUNT。
4. `InventoryViewModel.createNamed` 通过唯一创建接口获取标准 ID，再调用原批次录入接口。
5. 两步写入复用共享 PendingWrite 与幂等机制；网络结果未知时不重复生成请求。
6. 界面保存成功后加载真实库存并打开对应区域，不在本地伪造余额。

## 公共契约及数据

新增 `POST /api/v1/ingredients`，operationId 为 `createIngredient`，请求为 `CreateIngredientRequest`，返回既有 Ingredient。
接口及请求来自已明确确认的共享食材创建方案，提案为 `contracts/changes/IC-20261122-C-FREE-ENTRY.md`；团队审阅状态仍是 PROPOSED。
已有七个库存操作、单位枚举及库存模型保留，不新增数据库迁移，不批量更改旧食材单位或 ID。
客户端与服务端代码由唯一 OpenAPI 生成，生成文件没有手改。

服务端验证活动家庭成员、名称、单位、幂等重放和并发创建。
相同名称及单位复用已有资料；冲突返回明确错误，不让不同家庭的并发请求创建重复标准食材。

## 主要改动

- `feature-ac/.../inventory/IngredientChoices.java` 及其测试：直接输入、别名查找和候选整理。
- `InventoryFragment.java`、`InventoryViewModel.java`、`InventoryPresentation.java`：入口、表单、两步写入及首屏布局。
- `feature-ac/src/main/res/`：共享页面骨架、预览、冰箱颜色、尺寸及控件 ID。
- `server/.../inventory/InventoryController.java`、`InventoryService.java`、`InventoryErrors.java`：创建和复用标准食材。
- `contracts/openapi.json`、工具生成的契约索引及创建提案。
- `scripts/test_contract_index.py`：接口总数从 41 更新为 42，并新增创建接口归属与请求/响应断言。
- `scripts/test_inventory_ui.py`：保留这一阶段的 UI 回归流程；本次未运行。
- `scripts/open_inventory_preview.py`、`scripts/seed_inventory_demo_catalog.py`：该阶段专用预览工具，不属于生产数据初始化。

## 历史界面证据

以下截图是原始阶段留存，未覆盖第二版截图：

- [完整冰箱宽屏](../verification/screenshots/taskc_version3/c-fridge-open-wide.png)
- [窄屏](../verification/screenshots/taskc_version3/c-fridge-phone.png)
- [平板横屏](../verification/screenshots/taskc_version3/c-fridge-tablet-landscape.png)

原阶段记录包括 beef / egg / milk 直接输入、未收录英文名创建、真实库存操作以及三种窗口首屏等高。
本次恢复没有再次安装和点击旧版 APK；历史截图不作为本次新执行 UI 测试的声明。

## 本次实际验证（2026-11-23）

环境：JDK 17、Gradle 8.13、SDK 36；服务端使用独立 `civa_v03_verification_test` 数据库。

| 检查 | 实际结果 |
| --- | --- |
| 服务端完整 test | 47 项，0 失败、0 错误、0 跳过；C 接口测试 15 项 |
| 服务端 unitTest / testCandidateContract | 18 / 4 项通过，任务有重叠，不能相加 |
| bootJar | 构建成功 |
| Android 本地测试 | 35 项报告无失败：app 3、core-data 18、core-ui 1、feature-ac 9、feature-bd 4 |
| Android assembleDebug | 构建成功，未安装 |
| 候选客户端测试 | 1 项通过 |
| lint | 0 错误；app 2 条、feature-ac 18 条警告 |
| OpenAPI 校验及契约检查 | 通过；索引由 contract_index.py 重新生成 |
| Python 工具测试 | 17 项通过 |
| C Java 格式 / Git 空白检查 | 通过 |

实际执行命令：

```powershell
# server 目录；配置独立测试库连接和 CIVA_PSQL
.\gradlew.bat test unitTest testCandidateContract bootJar --offline --no-daemon

# 根目录；配置 JDK、SDK 和 Gradle 缓存
python scripts/contract_index.py contracts/openapi.json docs/generated/CONTRACT_INDEX.draft.md
.\gradlew.bat validateContract checkContracts assembleDebug testDebugUnitTest lint :api-client:testCandidateClient --offline --no-daemon
python -m unittest discover -s scripts -p 'test_*.py'
java -jar <google-java-format-1.27.0.jar> --dry-run --set-exit-if-changed <C业务和测试Java文件列表>
git diff HEAD --check
```

首次 Python 工具测试发现旧数量断言仍为 41；契约新增 1 个接口后，更新为 42 并增加明确的新增接口断言，17 项重跑通过。
未删除或放松既有归属、媒体类型、索引失效等断言。

## 当前验证范围

本次未运行模拟器 UI 点击回归，也未做 Xiaomi 真机验收；lint 警告仍保留。
业务数据来自真实接口与数据库；截图和工具中的示例食材仅为隔离测试 fixture，不用于正式库存假成功。
当前 Android Studio 最终版及其预览数据未修改或重新安装。BD / EF 和共享模块实现未变。
