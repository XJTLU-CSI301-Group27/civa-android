# BD MVP 开发与联调交接

维护组：BD。时区：Asia/Shanghai（+08:00）。本页是开发交接，不是课程提交报告。后续 BD 更新此页，避免和 AC/EF 的交接页互相覆盖。

## 当前目标

2026-11-20 用户确认先完成小范围 MVP，再逐步扩展。B/D 两名同学可以先共用一台电脑，在同一 feature 分支合作，分别负责排餐和食记的验收、问题记录与代码理解。以后换电脑用 Git 分支继续，不复制两套工程。

- B：按日期和早午晚餐手动排餐、份数、移除、随机生成空餐次、预览确认、锁定保护。
- D：个人食记月历、历史日期详情、手填外食、从菜谱预填、补录、修改删除与记录条数回顾。
- 暂缓界面：热量录入/展示、库存匹配推荐、做饭扣库存、图片上传。部分扩展代码仍保留，但不宣称已完成端到端验收。

## 开工基线与必读

仓库：https://github.com/XJTLU-CSI301-Group27/civa-android 。基线 main：`7508531c6495fe20ce9441a8e48459f0ea4dc84f`。工作分支：`feature/bd-planning-records`。

修改前已阅读根 AGENTS.md、PROJECT_DOC.md、OPEN_QUESTIONS.md，以及 docs 中 PRODUCT_RULES、DATA_CONTRACT、UI_RULES、INTEGRATION_RULES、TEST_PLAN、HANDOFF、LOCAL_RUN_HANDOFF、QUICK_START 和 verification/BOOTSTRAP。

沿用 Java 17、XML Views、View Binding、ViewModel/LiveData、生成 Retrofit API、ApiCalls、PendingWriteStore、Spring Boot、PostgreSQL。未新建第二个 Android 工程，未手改生成代码，未新增 App 第三方运行库。feature-bd 仅补用仓库已锁定的 AndroidX/Material/JUnit 依赖。

## 关键文件与各组边界

| 位置 | 用途与接手方式 |
| --- | --- |
| feature-bd/.../planning | B 的页面、ViewModel 与纯 Java 随机/推荐规则 |
| feature-bd/.../records | D 的日历、记录编辑、预留做饭界面及 ViewModel |
| feature-bd/.../BdHost.java | app 提供 api/calls/sessions 和既有 NavHost 的唯一依赖边界 |
| feature-bd/.../BdModel.java | 请求取消、错误、待确认写入与显式原 key 重试 |
| feature-bd/.../BdPayload.java | 本地待确认请求序列化，不新建 HTTP 客户端或第二个网络 Gson |
| server/.../planning | 真实计划读取、版本检查、锁定、整批提交与 BD 参数检查 |
| server/.../records | 真实个人食记读写、汇总与家庭烹饪记录读取 |
| server/.../recipe/RecipeCatalogController.java | 支持 BD MVP 的最小共享菜谱读取适配，AC 接手时扩展或替换唯一控制器 |
| app/.../MainActivity.java、AppNavigator.java | 最小页面装配，不重写 EF 登录/家庭流程 |
| contracts/changes/IC-20261120-BD-01.md | 新页面入口、导航参数、菜谱读取适配和字典导入提案，保持 PROPOSED，待 AC/EF 核对 |

新增 Java 已用 google-java-format 1.27.0 工具整理；工具仅存在忽略的 build 目录，不属于 App 依赖。不得通过改测试或门禁使自己通过。

### F 的导航接入

- TODAY 暂显示排餐 MVP，PLAN 同样显示排餐页。F 后续恢复完整今日首页时只替换 TODAY，保留 PLAN 入口。
- DIARY 打开食记，entityId 可空或 YYYY-MM-DD；编辑保存后回到该记录日期。
- MEAL_EDIT 参数为 YYYY-MM-DD，编辑已有记录时为 YYYY-MM-DD|entry UUID。
- COOKING_CONFIRM 参数为 recipe UUID|servings|plan slot UUID，末项可空；MVP 不暴露入口，等待 EF 做饭事务。
- BdHost 由 MainActivity 实现；页面无参构造，参数放 Bundle，支持 Fragment 恢复。
- 继续使用 core-ui 的公共主题和 Material 控件。未复制 Civa 图片、Logo 或其他专属素材。当前尚不是参考 App 的逐像素复刻。

### AC 的菜谱接入

仅有 RecipeCatalogController 持有生成 RecipeApi 路由；它真实实现 listRecipes/getRecipe/listWishlist。菜谱写入仍为明确 501，AC 不要同时新建另一套相同路由。

BD 全部通过现有 ApiProvider/ApiCalls 读取共享菜谱，不调用 feature-ac 内部页面、存储或网络实现。原始 OpenAPI 和 DTO 不变。后续 AC 提供完整实现时，BD 无须改变 HTTP 调用。

### EF 的做饭接入

当前下载基线没有 inventory service 或 CookingCoordinator。previewCooking/confirmCooking 继续明确返回 501，MVP 无入口，也不写虚构烹饪记录。AC/EF 接入后，应由唯一 CookingCoordinator 原子完成扣库存、烹饪快照和幂等响应。个人食记永远不扣原料。

## 开源食谱与显式导入

来源：[Anduin2017/HowToCook](https://github.com/Anduin2017/HowToCook)，许可证为仓库的 Unlicense。六道菜为西红柿炒鸡蛋、酸辣土豆丝、可乐鸡翅、鸡蛋羹、蛋炒饭、糖拌西红柿。

server/src/main/resources/bd-starter 保存原始文本、许可证和 manifest。manifest 记录源 URL、下载日期、每个文件 SHA256、整理后的标签与时间。来源保留在导入菜谱 steps 中，排餐页可查看原文。运行 App 不需要访问第三方食谱服务。

这些是实际可读食谱内容，不是伪造的“做过”或“吃过”记录。没有未经验证的照片或热量。每道菜导入为一个计划单位，不代表统一的一人份；实际人数和用量以原文为准。

原文用量有范围、备选和不同计量方式，首版没有精确标准化。导入食材线 trackStock=false、quantity=0 是既有契约的未跟踪占位，界面和 steps 明确说明，不能用于证明库存齐全、自动扣减或计算热量。AC 后续补齐真实数量后才能开启跟踪。

追加的 V20261120134000__BD_starter_ingredient_dictionary.sql 仅增加固定标准食材和别名；已有别名映射优先，不修改原映射。已应用迁移不能重写。请 AC 在合并前核对该字典。

显式导入命令（项目根目录）：

```powershell
# 复用启动器的 DB_URL / DB_USERNAME / DB_PASSWORD，不将密码写入文档或提交。
python scripts/bd_import_recipes.py --household <现有家庭UUID> --actor <该家庭活动OWNER的用户UUID> --print-sql
python scripts/bd_import_recipes.py --household <现有家庭UUID> --actor <该家庭活动OWNER的用户UUID>
```

需要 psql；Windows 自动检查 PostgreSQL 16 安装目录，也可用 CIVA_PSQL 指定。密码只从环境传给子进程。脚本事务内检查活动 OWNER，只导入指定家庭，失败回滚。UUID 由家庭和源路径稳定派生。重复导入不覆盖菜谱修改，也不重新加回用户已删除的食材线。

如果修改了打包源文件，显式运行 python scripts/bd_prepare_starter.py 重建元信息。若对应字典迁移已应用，不要运行生成器覆盖旧迁移，应改成新迁移。

## 已实现的业务规则

- 计划与个人用餐独立；随机预览不写服务器，确认后一次提交。
- 默认仅填空位，已锁定或已有菜的餐次保留；每餐最多10种菜。
- 读范围最多21天，生成/提交最多连续14天、42个餐次。
- 首次餐次 expectedVersion=0；锁定后必须先解锁才能改菜；并发旧版本拒绝。
- commitPlan 使用现有 IdempotencyExecutor，整批冲突回滚，不产生半份计划。
- 写入先持久化 PendingWrite，超时后冻结原意图，显式相同 key 和 payload 重试。
- 新账号读不到其他人的食记；新关联菜谱/烹饪记录检查当前家庭访问。
- 历史食记名称与摄入基准不因菜谱修改改变；删除食记不恢复库存。
- 新 MVP 食记采用 UNKNOWN 热量，不将未知计为零。既有服务端热量分支保留测试，界面暂缓。

## 验证与环境记录

每次结论必须以实际日志/XML为准，不把本节的计划当成通过。

- 已验证：最初 BD 服务端定向集成测试通过；加入共享读取适配后，全服务端回归 21项、0失败、0错误（后续新增食谱导入测试还需再跑）。
- 已验证：BD Java 编译成功，曾生成 debug APK。最终源码修改后仍需重新跑完整 Android 检查。
- 首次整套测试受 Windows 中文路径影响，出现 GradleWorkerMain ClassNotFoundException；不是业务断言通过。改用指向同一工作区的英文 junction 后服务端通过。
- Android 迁移到英文路径时混用了原构建缓存，Dex 报不同盘符根路径；已清理各 Android 模块产物并重跑。未删除根 build 中的工具链或数据库。
- 当前本机仅有 JDK21/23；下载 Microsoft JDK17 到忽略目录 build/jdk17。Gradle8.13官方包 SHA256 与仓库 Wrapper 固定摘要一致。
- Android SDK 使用本机 E:/Android/SDK，首次按现有许可证补齐 Platform36和Build Tools35，local.properties 未提交。
- Git 直连失败，使用 Windows 已配置代理后 fetch 成功；所有改动保留在 feature 分支，未覆盖 main。
- Docker Desktop 已尝试启动，但引擎未就绪；本机用已安装 PostgreSQL16 在项目忽略目录建立独立集群，55437端口。测试库 civa_bd_test，演示库 civa_bd_demo，和其他数据库分离。未关闭防火墙或修改其他数据库。
- 数据库密码、演示账号、进程记录和日志仅在忽略的 build 下，不得推送。

后续每次修改追加：时间、修改文件、行为、接口影响、实际命令、真实结果、剩余问题。最终验证结果将追加在本页末尾。

## 两人今日合作与后续扩展

一个人核对 B：日期切换、选菜、份数、移除、随机预览、锁定和保存。另一个人核对 D：补录、历史月历、编辑删除、选菜预填、账号隔离。共用当前电脑时仍按这两部分拆提交和记录，不能伪造两个人的提交身份。

第一阶段先完成上述流程和三台设备上的共享读取。第二阶段再接 AC 库存和用户偏好推荐。第三阶段配合 EF 补做饭事务。最后才恢复热量 UI、照片和更细视觉打磨。不要同时另建菜谱库、网络层或全局 UI。

## 最终验证与交付记录

记录时间：2026-11-20 14:16 +0800。

| 检查 | 实际执行 | 结果 |
| --- | --- | --- |
| Android 与契约 | gradlew.bat validateContract generateClient generateServerApi checkContracts assembleDebug testDebugUnitTest lint --no-daemon --console=plain --max-workers=4 | BUILD SUCCESSFUL；306 个任务；26 个 Android 测试，失败、错误、跳过均为 0；lint 通过 |
| 后端及打包 | server/gradlew.bat test bootJar --no-daemon --max-workers=4 --console=plain | BUILD SUCCESSFUL；22 个测试，失败、错误、跳过均为 0；使用独立 civa_bd_test 数据库 |
| 原有 Python 脚本 | python -m unittest discover -s scripts -p test_*.py | 17 个通过 |
| 契约与 IC 结构 | python scripts/check_contracts.py；load_ics | 通过；结构校验不代表 AC/EF 已批准变更 |
| 空白检查 | git diff --check | 通过 |

构建使用 JDK17、Gradle8.13、Platform36，并通过英文 junction C:/Users/admin/AppData/Local/Temp/civa-bd-20261120 避免中文路径下的 Java 测试 worker 故障。PyYAML6.0.2 安装在忽略的 build/bd-pylibs，通过 PYTHONPATH 使用。没有禁用测试、降低 lint 要求或更换原有依赖版本。preflight.ps1 未整体运行，上表列出的必要任务已分别执行。

Windows 首次 lint 发现 local.properties 的 sdk.dir 转义不正确，修正后全量任务通过。切换英文路径导致的旧 Dex 缓存问题通过清理各 Android 模块产物修复。

本轮还将 commitPlan 的成功状态修正为现有 OpenAPI 约定的 200，并校验空 plan slot。未为迁就实现而修改 OpenAPI。

模拟器实际操作：

- 登录家庭账号，读取已导入的 6 道食谱及原始做法。
- 添加早餐菜谱、保存计划、刷新确认保留；生成预览后再确认。
- 新增食记、在日历看到记录、编辑份量从 1 到 2、删除记录。
- 切换月份并补录 2026-11-19，保存后仍停留在 11 月 19 日并显示该记录。
- 检查 1080×2400 窄布局与 1600×1000 宽布局；宽屏为日历和详情双栏，切换尺寸后日期保持。
- 截图位于 docs/verification/screenshots/bd-plan-wide.png、bd-diary-wide.png、bd-diary-narrow.png。上述为 Android 模拟器验证，三台小米平板尚未接入测试。

本地演示使用独立 civa_bd_demo 数据库。演示账号和密码仅保存在忽略的 build/bd-demo-access.json，不随代码推送。运行中的 JAR 使用独立副本 build/bd-demo-runtime.jar，避免再次打包覆盖正在运行的 JAR。临时进程须核对 PID 及命令行后再停止。

剩余范围：

- 本版沿用 EF 的公共主题与组件，尚未做到 Civa 的完整视觉还原。
- AC 菜谱写入和库存业务、EF 做饭事务仍由对应负责人实现；本版入口不暴露未实现的做饭流程。
- 热量界面、照片、库存匹配、复杂偏好推荐后置。未知热量没有按零处理。
- 普通页面旋转由 ViewModel 保留状态；尚未承诺进程被系统杀死后恢复全部编辑草稿。弹窗中的未确认输入也不保证恢复。
- 本地验证通过不代表远端 CI 通过。提交 feature 分支供 AC/EF 审阅，不直接合并 main；公共接口和食材字典变更仍为 PROPOSED。

## GitHub 交付

2026-11-20：实现提交 4d418ad 已推送到 feature/bd-planning-records。
草稿 PR：https://github.com/XJTLU-CSI301-Group27/civa-android/pull/10 。
main 未被合并或覆盖；请 AC/EF 在 PR 中核对 IC、菜谱控制器和食材字典后再按小组流程合并。远端 CI 状态以 PR 实时显示为准。

本机可用 Android Studio 打开当前项目，安装 app/build/outputs/apk/debug/app-debug.apk；演示后端已更新为最终通过测试的 JAR。其他电脑不要复制本机忽略目录内的账号、数据库和绝对路径，应按仓库启动说明建立自己的环境，并在已有家庭下显式导入菜谱。
