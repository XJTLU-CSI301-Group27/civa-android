# taskc_version2：初步冰箱前端

## 版本定位

在已上传的第一版 `3e30fa2969f99989cbc8d45b6343f62856542385` 库存后端基础上，增加 Java / XML 原生冰箱页面。
原始前端源码来自历史项目树 `796bc6cd9677d8a2ab4609866d6e9783af2a06a2`。
本版保留第一版已经接入的主线 `a0943b1`，仅补充 C 前端及必要的 app 装配，不改写 BD / EF 已有功能。

## 相较已上传第一版的优化

| 功能 | 已上传第一版 | 第二版新增或优化 | 用户可见效果 |
| --- | --- | --- | --- |
| 冰箱入口 | 库存后端接口已实现；原生页面为占位 | 原 Route.INVENTORY 打开 InventoryFragment | 点击冰箱入口进入库存页面 |
| 冰箱外观 | 没有 C 原生冰箱设计 | 加入冰箱外壳、冷藏层与冷冻抽屉 | 食材按冰箱内部空间展示 |
| 开关冰箱门 | 无前端交互 | 冷藏、冷冻门可分别开关，也可一起开关 | 点击门或按钮查看内部食材 |
| 食材数量 | 数据通过后端接口查询 | 食材图标、名称、剩余数量及标准单位 | 直接看到家中库存；肉类按 G 显示克，COUNT 显示个，COUNT 奶类显示瓶 |
| 多批次详情 | 后端已保存独立批次 | 点选批次查看位置、服务器入库时间、到期日及余额 | 页面能核对每一批库存 |
| 添加库存 | 需要直接调用接口 | 增加添加食材表单，选择已有标准食材、数量、位置、到期日 | 在 App 内录入，保存后重新读取真实余额 |
| 到期筛选 | 后端支持 ALL / VALID / SOON / EXPIRED | 增加全部、未过期、临期、已过期入口 | 点击筛选查看对应批次 |
| 消耗、丢弃、修正 | 后端已支持数量变动及记录 | 增加操作表单、预计余额和保存后刷新 | 输入消耗量，确认后看到库存减少 |
| 修改库存资料 | 后端支持位置、到期日修改 | 增加编辑入口，提交 expectedVersion | 在 App 内编辑，版本冲突时提示核对 |
| 宽窄布局 | 没有库存页面适配 | 初步宽屏详情并排、窄屏纵向布局 | 初步支持平板与窄窗口 |
| 错误及重复提交 | 后端已有幂等、权限和并发保护 | 前端复用 ApiCalls、PendingWrite 与显式重试 | 连接失败不伪装成功，未知结果保留原请求 |
| 测试 | 第一版服务端测试 | 新增 4 项 InventoryPresentationTest 和原生 UI 回归脚本 | 覆盖精确小数、超量、日期、单位及位置显示规则 |

## 实现流程与文件

1. 复用第一版的七个 InventoryApi，不创建新的 HTTP API、数据库表或食材标准。
2. InventoryHost 将既有 ApiProvider、ApiCalls、SessionStore 与导航交给库存页面。
3. InventoryViewModel 读取家庭及分页库存，保存批次选中状态、筛选条件和待确认请求。
4. InventoryPresentation 处理精确数量、日期、单位显示和储存位置分层。
5. InventoryFragment 与 XML 绘制冰箱、食材和表单；服务器写入确认后刷新。
6. app 仅登记库存页面，保留排餐、食记、账号和家庭入口。

主要新增文件：

- `feature-ac/src/main/java/edu/xjtlu/civa/feature/ac/inventory/InventoryFragment.java`
- `feature-ac/src/main/java/edu/xjtlu/civa/feature/ac/inventory/InventoryHost.java`
- `feature-ac/src/main/java/edu/xjtlu/civa/feature/ac/inventory/InventoryViewModel.java`
- `feature-ac/src/main/java/edu/xjtlu/civa/feature/ac/inventory/InventoryPresentation.java`
- `feature-ac/src/main/res/layout/ac_fragment_inventory.xml`
- `feature-ac/src/main/res/layout/ac_preview_inventory.xml`
- `feature-ac/src/test/java/edu/xjtlu/civa/feature/ac/inventory/InventoryPresentationTest.java`
- `scripts/test_inventory_ui.py`

配套调整：`app` 库存装配、`feature-ac/build.gradle`、共享入口清单、契约说明、验证文档。
Fragment、AppCompat、Material、ViewModel、LiveData 与 JUnit 复用工程已有版本目录，不引入另一套网络或全局 UI 系统。
新增公共入口仍记录为 `IC-20261122-C-UI-01` 候选提案；没有声称团队已批准。

## 历史界面与验证证据

以下是这一阶段实际保留的截图，并非最终版本效果：

- [关门宽屏](../verification/screenshots/c-fridge-closed-wide.png)
- [开门及批次详情](../verification/screenshots/c-fridge-open-wide.png)
- [消耗后余额](../verification/screenshots/c-fridge-consumed-wide.png)
- [初步窄屏布局](../verification/screenshots/c-fridge-narrow.png)

原阶段记录：隔离测试家庭读取 13 批库存；牛肉 500 克消耗 150 克后，页面与数据库均剩 350 克；
过期版本写入被拒绝；添加后成为 14 批；宽窄窗口切换通过。
截图中的食材是专用测试 fixture；正式业务读取后端真实数据，不硬编码假库存。
这些是原阶段证据，本次未重跑旧版模拟器点击流程，也未安装旧版覆盖现有最终版。

## 本次重新验证

2026-11-22 在第一版上传副本上接入第二版，基于 `a0943b1` 重新构建。

| 本次检查 | 实际结果 |
| --- | --- |
| Android assembleDebug | BUILD SUCCESSFUL，生成 APK，未安装 |
| Android 本地单元测试 | 30 项报告均无失败：app 3、core-data 18、core-ui 1、feature-ac 4、feature-bd 4 |
| 新增 C 前端测试 | InventoryPresentationTest 的 4 项本次执行通过；未变化模块可复用 Gradle UP-TO-DATE 结果 |
| API 候选客户端测试 | 1 项通过报告，本次任务为 UP-TO-DATE |
| lint | 所有模块 0 错误；app 2 条警告，feature-ac 7 条警告 |
| 契约检查 | validateContract、checkContracts 通过，OpenAPI 未变 |
| Python 工具测试 | 17 项，全部通过 |
| Java 格式与 Git 空白检查 | 通过 |
| 历史源码一致性 | 11 个 C 前端源码/资源文件与原始项目树逐字节一致 |
| 其他模块保护 | BD、EF、core-data、core-ui 实现不变；原 public-surface 条目全部保留 |
| 最终版本保护 | 已冻结 APK 与源码 ZIP 的 SHA-256 与原值一致 |

实际执行命令：

```powershell
# 根目录；环境为 JDK 17、SDK 36、项目 Gradle 缓存
.\gradlew.bat validateContract checkContracts assembleDebug testDebugUnitTest lint :api-client:testCandidateClient --offline --no-daemon
python -m unittest discover -s scripts -p 'test_*.py'

# 格式工具位于本机 build/tools；文件列表为本版 C 前端 Java 源码和测试
java -jar <google-java-format-1.27.0.jar> --dry-run --set-exit-if-changed <Java文件列表>
git diff HEAD --check
```

feature-ac 的警告包括 1 条 Overdraw（重复背景绘制）及 6 条 SetTextI18n（运行时文本拼接）；
app 的 2 条既有警告为 debug 明文地址与未设置应用图标。它们不阻塞本次构建，仍属于此阶段的限制。

第一版服务端代码和 OpenAPI 不发生变化；本次没有重跑数据库接口测试，不将第一版 43 项服务端结果算作本次重跑。
未运行旧版 UI 点击脚本、未对当前模拟器安装 APK、未执行 Xiaomi 真机验收。
