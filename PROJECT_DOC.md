# Civa 架构骨架生成规格与团队接口治理

版本：1.1　更新：2026-10-29　维护：EF

本文供 AI Coding Agent 在空仓库中生成统一工程骨架、接口、数据模型、公共组件和检查文件，再供 AC、BD、EF 分工开发。它取代上一版方向性草案。本文定义的是实现规格；当前尚未生成或验证 Android 工程、后端和 CI，不能把文中的验收标准称为已经通过。

## 1. 决策状态与使用方法

用户已确认：全部功能纳入架构；客户端主语言 Java；统一 GitHub 仓库；AC/BD/EF 分工；优先在各自电脑的 Android Studio 模拟器开发测试；部署预算和公网发布暂不讨论。

为使 AI 能直接生成骨架，本文把其余技术选择定为“架构师默认实施基线”，而非伪称用户已逐项确认：XML Views、View Binding、ViewModel/LiveData、契约优先 REST、单体 Java 后端和 PostgreSQL。生成 Agent 按本基线实施。发现真实的版本、环境或业务冲突，提交有证据的变更记录；不重新自行挑选另一套架构。

阅读顺序：技术与模块 → 数据与 API → 状态和事务 → 骨架文件 → 验收 → 接口异常归档。首次生成统一底座必须由一个 EF 集成任务完成，再通过同一基线提交向所有成员分发。各组不能分别让 AI 根据本文创建三个独立工程。

本文没有创建远程仓库、改变权限、推送代码或部署服务。课程提交报告和演示材料由团队自行完成。

## 2. 产品范围与需求公理

用户状态变化：家庭成员从库存不清、餐食未安排，变成可以共同安排做饭，并各自追溯实际饮食。

| 必须实现 | 首版边界 |
| --- | --- |
| 菜谱 | 新增、编辑、搜索、详情、归档；食材、份数、步骤、口味、图片和可选热量 |
| 想吃与做过 | 收藏归个人；做过从家庭真实烹饪记录查询 |
| 冰箱 | 批次、数量、单位、位置、到期、消耗、丢弃、修正及匹配 |
| 排餐 | 每餐多菜、手动计划、连续最多 14 天、随机生成、规则推荐、预览、锁定和替换 |
| 家庭共享 | 账号、创建家庭、邀请、成员、权限、版本冲突；每个用户先属于一个家庭 |
| 食记 | 个人实际用餐、补录、修改、删除、月历、日期详情、周月回顾 |
| 热量 | 手填或按份/重量估算；未知单独计数；保存历史快照 |
| 公共体验 | 今天、菜谱、冰箱、食记、我的；窄宽窗口、加载、空状态、错误和返回行为 |

输入：菜谱、食材批次、个人偏好、日期餐次、做饭份数及实际用餐份量。处理：匹配、候选排序、库存校验与扣减、权限、同步、热量汇总。持久化：身份、成员关系、共享业务记录、个人记录和历史快照。首版排除拍照识菜、模型训练、自动营养处方、复杂社交、多家庭切换与离线多人写入合并。

固定不变量：

1. 计划不是做饭；做饭不是每个人都吃过。
2. 只在做饭确认或显式库存变动中扣原料；个人食记不操作库存。
3. 库存不为负；过期批次不算可用；未知不是零。
4. 同一食材和单位规则只有一份权威定义；服务端最终复核客户端预览。
5. 家庭数据与个人数据隔离；权限不能仅靠隐藏按钮。
6. 做饭跨表操作原子提交；重试不得重复扣减。
7. 修改菜谱不改写历史；删除食记不恢复原料。
8. 并发写入要检测旧版本；未来日期不能保存为实际用餐。

## 3. 技术基线与依赖冻结

| 项目 | 实施基线 |
| --- | --- |
| JDK / Java | JDK 17；Android 手写业务 Java 17；生成 DTO 使用普通类，不依赖 Android 不支持的 JDK API |
| Android | applicationId `edu.xjtlu.civa`；minSdk 26；compileSdk/targetSdk 36；AGP 8.13.2；Gradle Wrapper 8.13 |
| 构建文件 | Groovy `build.gradle`，客户端不引入 Kotlin 源码、Compose 或 Kotlin 编译插件 |
| UI | XML Views、View Binding；AppCompat 1.7.1、Material 1.12.0、Fragment 1.8.9、Lifecycle 2.9.4、RecyclerView 1.4.0、ConstraintLayout 2.2.1 |
| 网络 | Retrofit 2.11.0、converter-gson 2.11.0、OkHttp 4.12.0、Gson 2.13.2；使用 Java Call/Callback |
| 本地缓存 | Room 2.8.4，Java annotationProcessor；只读快照缓存，不是共享数据的权威库 |
| HTTP 契约 | OpenAPI 3.0.3，JSON 文件；OpenAPI Generator 7.17.0；固定生成配置 |
| 后端 | Spring Boot 3.5.16，Java 17，独立 Gradle 8.13 工程；Web、Validation、Security、JDBC，Spring BOM 管理传递版本 |
| 数据库 | PostgreSQL 16；Flyway 迁移；需要 PostgreSQL 的 Flyway 模块；不使用 H2 替代事务测试 |
| 测试 | Android 本地 JUnit 4.13.2；服务端 Spring Boot Test / JUnit 5；架构检查使用 ArchUnit（生成时解析稳定版本并锁定） |

以上是初始候选组合，不是已构建验证的结论。生成 Agent 必须实际解析、编译、记录完整版本；个别版本不兼容时，依据官方文档调整最小范围，在 `docs/decisions/ADR-001-baseline.md` 记录原因及验证结果。所有新增版本精确固定，禁止 `+`、latest 或 SNAPSHOT。Python 3.11+ 用于契约检查脚本，唯一额外依赖 PyYAML==6.0.2，使用 safe_load，拒绝重复键和未知元数据字段；依赖固定于 scripts/requirements.txt。Windows/macOS/Linux统一用 python；preflight先检查解释器版本和依赖，不合格非零退出，不静默换成py；Android Studio、SDK 与 Python 本机安装说明写入 README。

Android 依赖版本只在 `gradle/libs.versions.toml` 管理；后端版本在 `server/build.gradle` 和依赖锁记录；生成器版本在 contracts/toolchain.properties 中唯一声明，根与server构建均读取。提交 Wrapper、校验信息和依赖锁。Gradle 同一根构建可以有多个模块；后端独立构建避免 Android 插件和服务端任务互相污染。数据库开发镜像在首次成功启动后记录实际 patch 版本和镜像 digest，不能把尚未核验的 digest 写死。

具体设备若低于 API 26，需先调整基线并验证日期 API 与依赖兼容；不在当前讨论中假设学校设备版本。

测试依赖补充：Android testImplementation 使用 mockwebserver:4.12.0 和 androidx.arch.core:core-testing:2.2.0。服务端本机测试库名称必须以 `_test` 结尾，CI使用postgres:16服务容器，首次验证后记录镜像digest；不用Testcontainers。图片首版采用core-data的MediaClient、BitmapFactory抽样解码和有界线程池，不增加图片库；图片缓存暂只用有界内存缓存，登出清空，避免认证图片落入共享磁盘缓存。

## 4. 单仓库与模块依赖

```text
civa/
  PROJECT_DOC.md                 本规格
  AGENTS.md                     可执行的 Agent 约束摘要
  README.md                     全新机器运行步骤
  settings.gradle / build.gradle / gradlew / gradlew.bat / gradle/
  contracts/
    openapi.json                 网络契约唯一权威源
    generator-client.json       客户端生成选项
    generator-server.json       服务端生成选项
    public-surface.json         手写公共 API 与允许依赖登记
    fixtures/                   最小请求、响应、错误样例
    changes/                    接口变更与异常记录（仅文档与脱敏证据）
  app/                          EF/F：Application、装配、导航、首页、我的
  api-client/                   纯 Java 库：生成的 Retrofit API、DTO 与必要支持代码
  core-ui/                      EF/F：公共主题、组件和页面状态
  core-data/                    EF/E：ApiProvider、会话、网络、缓存
  feature-ac/                   A/C：recipe/、inventory/
  feature-bd/                   B/D：planning/、records/
  feature-ef/                   E：auth/、household/、preference/
  server/
    settings.gradle / build.gradle / gradlew / gradlew.bat / gradle/
    src/main/java/edu/xjtlu/civa/server/
      auth/ household/ profile/ recipe/ inventory/ planning/ records/ media/
      application/              跨业务事务协调，不写页面逻辑
      config/ error/
    src/main/resources/db/migration/
    src/test/
  infra/compose.yaml            只启动开发数据库，服务端可在本机直接运行
  scripts/preflight.ps1 / preflight.sh / contract_guard.py
  docs/decisions/ / docs/generated/ / docs/verification/
  .github/workflows/ci.yml
  .github/pull_request_template.md
```

`app` 依赖三个 feature、core-ui、core-data、api-client。feature 只能依赖 core-ui、core-data、api-client，不能依赖其他 feature 或 app。core-data 依赖 api-client 和 core-ui（仅用于实现其ImageLoader接口）；core-ui 不依赖 api-client、core-data 或任何 feature；api-client 不依赖 Android、后端、Room 或 UI。禁止循环依赖。

这里按三组划分 Gradle 模块，是为了编译期限制随意调用其他组实现；不按每个页面再拆模块。后端是一个进程、一个数据库，按业务包组织；不拆微服务，不增加消息队列、服务注册中心或分布式事务。

后端每个业务包分 `web`、`service`、`persistence`，仅对真实需要跨包调用的 service 建立公开入口。`application.CookingCoordinator` 可以调用 auth、household、recipe、inventory、records 的 service，业务 service 不能反向调用 coordinator，也不能访问别组 persistence。库存匹配算法在 inventory service 中只有一份，客户端只展示其结果。

F 维护 AppContainer：创建唯一 ApiProvider、会话和缓存实例，构造各页面所需依赖。采用显式构造器注入；不先引入 DI 框架。ViewModel 用统一 Factory 接收依赖。app 使用一个 MainActivity 和 Fragment 页面；NavHost 接口放 core-ui，feature 只发出固定 Route，不直接实例化别组 Fragment。

server是完全独立的Gradle工程，不使用includeBuild，不加载Android插件。它独立声明generateServerApi，输出固定为server/build/generated/openapi。根的同名任务只用Exec调用server自身Wrapper（Windows用gradlew.bat，其余用./gradlew，工作目录server），转发退出码；不能再生成第二份服务端代码。server的compileJava依赖自己的生成任务，单独bootRun/test无需Android SDK。契约索引与生成选项从仓库contracts读取。

Android模块resourcePrefix：app用app_，core-ui用civa_，core-data用data_，feature-ac/bd/ef分别用ac_/bd_/ef_；每个Android模块lint把ResourceName升为error，CI拒绝命名冲突。core-ui的菜谱卡片只接收名称、图片ID、标签、时长等普通字段，DTO到展示字段由feature转换。core-ui定义ImageLoader.load(String mediaId, ImageView target)及clear(ImageView target)，core-data实现，AppContainer注入；回收View时取消加载并核验目标tag避免错图。

## 5. 分工与服务端责任

| 组 | 客户端与业务 | 服务端责任 |
| --- | --- | --- |
| AC / A | 菜谱、收藏、做过展示、归档 | recipe 的校验、读写、收藏；A客户端直接调用RecordsApi.listCookingEvents展示做过，不另建recipe服务端转发入口 |
| AC / C | 库存批次、到期、匹配、消耗 | inventory 与标准食材、单位换算、批次扣减规则 |
| BD / B | 计划、随机与推荐、锁定与预览 | planning 的保存、版本与锁定校验；排序先在客户端完成 |
| BD / D | 做饭确认、食记、热量、回顾 | records 的烹饪快照、个人用餐、汇总及时间规则 |
| EF / E | 账号、家庭、偏好、同步 | auth/household/profile（Profile tag归E）、权限与公共错误、CookingCoordinator 原子事务、统一配置 |
| EF / F | 工程、公共 UI、导航、首页、我的 | 构建、CI、契约生成和整体集成 |

每组必须交付自己业务的真实读写与测试。EF/E 不代写 AC/BD 全部数据逻辑。跨组事务由 E 组织，AC 和 BD 提供可参与同一事务的服务方法，不在这些方法中独立提交。

排餐只使用发起用户本人的忌口、收藏和偏好，不推断其他成员的偏好；界面标明“按我的偏好”。core-ui提供QuantityInputs.toBase(BigDecimal value, String inputUnit)：KG乘1000为G、L乘1000为ML，其他支持单位原样规范化；返回含amount和Unit名称字符串的本地值对象，不引用api-client的enum。转换后按wire精度校验，禁止截断。

## 6. 契约唯一来源与生成约定

### 6.1 OpenAPI 与生成代码

生成 `contracts/openapi.json`，其 paths、schemas、operationId、tag、参数顺序和错误定义均为正式网络契约。第 8、9 节是首版生成输入。每个 operation 仅一个 tag，operationId 全局唯一；非认证接口默认使用 Bearer 会话认证。

根构建提供 `validateContract`、`generateClient`、`generateServerApi`、`checkContracts` 任务。使用固定版本 OpenAPI Generator，客户端 generator=`java`、library=`retrofit2`、serializationLibrary=`gson`、useRxJava/useRxJava2/useRxJava3=`false`、useBeanValidation=`false`、hideGenerationTimestamp=`true`；包名 `edu.xjtlu.civa.api`、模型包 `edu.xjtlu.civa.api.model`。时间和数值在 wire schema 中用受约束的字符串，不使用生成器默认日期类型。

服务端 generator=`spring`，interfaceOnly=`true`、useSpringBoot3=`true`、useTags=`true`、skipDefaultInterface=`true`、openApiNullable=`false`、hideGenerationTimestamp=`true`；包 `edu.xjtlu.civa.server.api`、模型包 `edu.xjtlu.civa.server.api.model`。实现类实现生成接口，不再重复声明一套不同的路由。服务端字段约束仍需实际验证，不能假设生成注解覆盖全部业务校验。

生成文件写各构建的 `build/generated/openapi/` 并加入 sourceSet；不手改、不提交、不能作为唯一存档。生成只选择 API、model 和必要支持文件，关闭生成测试和文档；不让生成器覆盖手写 Gradle 文件。`compileJava`/Android 编译依赖生成任务。服务端独立运行 Wrapper 时也必须能读取 `../contracts/` 并生成。模板或选项变化也属于契约工具链变更。

生成 Agent 必须验证固定版本是否识别这些选项，检查方法实际返回 `retrofit2.Call<T>`，确认只有一份 Gson 配置和鉴权拦截器。若默认生成依赖不适合 Android，在构建配置中统一修正，不能让各业务组手改生成类。

### 6.2 手写公共入口

以下类型在public-surface登记完整签名、可空性、owner、消费者和线程语义；使用普通Java类。ApiProvider是接口，真实实现RealApiProvider在main，FakeApiProvider只能放src/test，任何APK均不含Fake。debug和release都默认真实实现；测试通过构造器注入Fake，不提供运行时切换开关。

- ApiProvider只公开household/profile/recipe/inventory/planning/records六个返回生成*Api的方法；auth/media仅core-data内部可取，feature不得直接调用AuthApi或MediaApi。
- AuthRepository.register(RegisterRequest, ApiCallback<User>)、login(LoginRequest, ApiCallback<User>)、logout(ApiCallback<Void>)。login由SessionStore原子保存AuthSession及更新generation，feature只收到User；创建/加入家庭成功后刷新getMe，更新SessionStore的householdId。
- SessionStore公开currentUserId()/currentHouseholdId()（可空String）、isLoggedIn()/clear()；token读写和安装会话只在core-data内部。
- ApiCalls.enqueue(Call<T>, ApiCallback<T>)；ApiCallback只有onSuccess(T)和onError(ApiError)。成功204映射Void/null，不作为反序列化失败。
- ApiCalls.enqueueCached(Call<T>, String cacheKey, Class<T> type, ApiCallback<CachedResult<T>>)只用于GET；CachedResult有data、stale、fetchedAt。命中缓存先回调stale=true，网络成功再回调stale=false；网络失败回调onError，页面保留已有缓存并显示错误。普通enqueue最多一次结果；缓存入口允许缓存+网络两次成功。支持的分页DTO是具体类，不用擦除类型的原始List.class。
- 调用入口统一解析ApiError、处理401、登记和取消请求、缓存读写、捕获sessionGeneration。所有用户回调在主线程执行；必须在投递到主线程的Runnable内部再次比较generation，缓存异步写入也检查generation。旧回调直接丢弃。ArchUnit禁止feature调用Call.enqueue/execute或自行创建网络客户端。
- 登录专用包装先验证原generation再安装新会话，并把成功回调绑定新generation，不能因自己安装会话递增而丢弃登录结果。登出立即清理本地会话，服务端撤销请求用捕获的旧token发送，不再向已关闭页面回调。
- MediaClient.upload(Uri source, MediaScope scope, String householdId, String idempotencyKey, ApiCallback<Media>)；ImageLoader委托它的鉴权下载实现。上传前抽样解码，长边最大2048、JPEG质量85，压缩后仍超过5MB则拒绝。文件压缩只做一次，重试复用相同字节和key。MultipartBody/ResponseBody不出core-data。图片下载在后台抽样解码、有界内存与请求并发，取消/账号隔离规则同ApiCalls。
- 唯一Gson开启serializeNulls；生成器的序列化支持类不得创建第二套运行时实例。唯一OkHttpClient设置retryOnConnectionFailure=false；写入是否重试由ApiCalls按显式key和用户操作控制，记录ADR。
- ApiError：code、message、traceId、fieldErrors（非null列表）。UiState<T>：status=LOADING/CONTENT/EMPTY/ERROR、可空data/error、stale、可空fetchedAt。
- NavHost.navigate(Route route, String entityId)：entityId可空；Route固定TODAY、RECIPES、RECIPE_DETAIL、RECIPE_EDIT、INVENTORY、INVENTORY_EDIT、PLAN、COOKING_CONFIRM、DIARY、MEAL_EDIT、PROFILE、LOGIN、HOUSEHOLD、PREFERENCES。

ViewModel通过ApiCalls访问业务API，用postValue更新LiveData；不保留Activity/View引用，onCleared取消Call。HTTP取消不等于服务端回滚。core-data里运行在Android主线程的调度抽象可由单元测试注入Executor，避免MockWebServer本地测试依赖真实Looper。

手写公共面定义为明确导出包或@PublicApi标记的类与成员；实际跨模块引用但未登记的类型视为漏登记。Fragment/ViewModel/Adapter的public修饰符本身不代表跨组接口。app依赖的feature页面入口必须登记，页面内部的public实现则由字节码规则禁止跨模块使用。

## 7. Wire 类型、错误和通用规则

业务ID和Idempotency-Key均为UUID字符串；业务实体ID由服务端生成，幂等key由客户端生成。错误跟踪标识traceId由服务器为每次HTTP请求生成，与幂等key分离。

数量、份数、热量的输入正则 `^(0|[1-9][0-9]{0,14})(\.[0-9]{1,3})?$`（正则引擎表示；写入JSON时由序列化器转义反斜线）。最大值999999999999999.999；份数/摄入amount大于0，库存非负。服务端BigDecimal，数据库NUMERIC(18,3)。输入超精度拒绝。计算先用精确值，最终热量一次HALF_UP到3位；库存需求一次CEILING到3位。输出stripTrailingZeros().toPlainString()，零为"0"，不输出科学计数法；JSON schema允许上述输入形式，规范输出由测试验证。中间计算或汇总超范围报VALIDATION_ERROR，不截断；汇总也受同上限约束。

Unit只有G/ML/COUNT；KG/L仅输入层转换。COUNT可小数；不同计量维度不换算。日期YYYY-MM-DD严格校验日历有效性；事件时间UTC截断到毫秒，固定yyyy-MM-dd'T'HH:mm:ss.SSS'Z'，schema pattern为`^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3}Z$`（正则引擎表示），再用严格时间解析器排除无效时分秒。家庭初始zoneId=Asia/Shanghai；用户zoneId注册时校验，首版不能修改。

响应中的`?`字段：required且nullable=true，必须输出null属性；请求中的`?`字段：nullable=true、不加入required，缺失等同null，完整PUT中二者都表示清空，不表示保留旧值。其余字段required且非空。不要同时声明“required但允许缺失”。生成nullable请求字段不得带@NotNull，生成后用缺失/null/合法值三组样例验证。对象、枚举和数组元素按第8节命名引用，nullable引用若生成器处理不一致，以带nullable字段的显式schema或模板最小修正并测试，不能静默改变语义。

spring.jackson.deserialization.fail-on-unknown-properties=true，配置在application.yml；测试未知字段返回400。响应客户端忽略未知字段。fieldErrors永远为数组，无错误字段时[]。版本非负int64；PUT完整请求加expectedVersion。列表page默认0，size默认20最大100，空列表[]；稳定排序见第9节。note和location允许空串，最长分别2000/120；其他名称字段trim后1–120。密码不trim。

成功读取/修改200，新建201，删除204。错误体固定`{code,message,traceId,fieldErrors:[{field,message}]}`，不暴露SQL/凭证/堆栈。错误映射：VALIDATION_ERROR400、UNAUTHENTICATED401、INVALID_CREDENTIALS401、FORBIDDEN403、HOUSEHOLD_ACCESS_REVOKED403、NOT_FOUND404、VERSION_CONFLICT409、INSUFFICIENT_STOCK409、IDEMPOTENCY_KEY_REUSED409、PLAN_LOCKED409、USERNAME_TAKEN409、ALREADY_IN_HOUSEHOLD409、INVALID_INVITATION400（无效/过期/用过统一）、OWNER_CANNOT_LEAVE400、EXPIRED_BATCH409、PAYLOAD_TOO_LARGE413、UNSUPPORTED_MEDIA_TYPE415、RATE_LIMITED429、CONTRACT_NOT_IMPLEMENTED501、TEMPORARILY_UNAVAILABLE503、INTERNAL_ERROR500。429带Retry-After秒数。每个operation列出适用状态及code枚举，不能只给一个无说明default响应。

login失败固定INVALID_CREDENTIALS，不区分用户不存在/密码错误；全局401清理跳过/auth/*，保留登录页输入。非成员家庭路径和非本人私有对象查询返回404；如果服务端能查到本人被移除的inactive membership，家庭路径返回403 HOUSEHOLD_ACCESS_REVOKED，客户端只清该家庭缓存和家庭状态，不撤销个人登录；已有成员无管理权限返回FORBIDDEN。此区分不向从未加入的用户泄露家庭存在性。

需要Idempotency-Key的operationId精确清单：createHousehold、createInvitation、joinHousehold、createRecipe、createInventoryBatch、changeInventory、commitPlan、confirmCooking、createMealEntry、uploadMedia。均标注x-idempotent:true和必填header；register/login/logout/matchRecipes/previewCooking不需要。按actor_id+key唯一，摘要含方法、规范化路径和规范化请求；同key不同内容409。JSON先按schema补齐可空缺省为null、规范十进制和有序键，再做摘要；业务有序数组不得排序。multipart摘要=文件字节SHA-256+规范化表单字段。

GET允许显式重试。PUT/DELETE使用expectedVersion，结果不确定先刷新；收藏按唯一关系天然幂等。POST超时重试必须相同key和相同规范化内容。成功响应去重记录不自动清理；重放仍校验当前权限。register用用户名唯一约束，重复注册不返回会话；logout已撤销token重复请求返回204，但不能返回账号信息。客户端NETWORK_ERROR/TIMEOUT/CANCELLED是本地错误，不冒充HTTP。

## 8. 领域模型和数据库要求

下列字段直接转为 OpenAPI schema。只有持久化实体User、Household、Membership、Recipe、InventoryBatch、InventoryMovement、PlanSlot、CookingEvent、MealEntry、Media共有 `id:String`、`createdAt:String`、`updatedAt:String`；可编辑实体有 `version:long`。Preference、Ingredient、Invitation仅采用表中专有字段（Ingredient需额外id，Invitation需额外id）；RecipeIngredient、MatchLine、RecipeMatch、RecipeSnapshot、PlanItem、各Summary与AuthSession为值对象，不加公共id/时间/版本。列表响应固定 `items:T[],page:int,size:int,total:long`，生成具体 RecipePage 等 schema，不用无类型 Object。写入请求只能包含本节指定可编辑字段；id、归属、版本和快照不能由用户任意覆盖。

| schema | 专有字段（除通用字段外） |
| --- | --- |
| User | username:String、displayName:String、zoneId:String、householdId:String?；不输出密码及哈希 |
| Preference | userId:String、likedTags:String[]、excludedIngredientIds:String[]；version |
| Household | name:String、zoneId:String；version |
| Membership | householdId:String、userId:String、role:OWNER/MEMBER、active:boolean；version |
| Invitation | householdId:String、code:String、expiresAt:String；code 仅创建时返回 |
| Ingredient | name:String、aliases:String[]、unit:G/ML/COUNT、active:boolean；全局标准只读给普通用户 |
| RecipeIngredient | ingredientId:String、quantity:String、unit:Unit、trackStock:boolean |
| Recipe | householdId:String、authorId:String、name:String、steps:String[]、ingredients:RecipeIngredient[]、yieldServings:String、durationMinutes:int、tags:String[]、imageId:String?、totalKcal:String?、archived:boolean；version |
| InventoryBatch | householdId:String、ingredientId:String、quantity:String、unit:enum、location:String、expiresOn:String?；version |
| InventoryMovement | householdId:String、batchId:String、beforeQuantity:String、afterQuantity:String、reason:COOK/CONSUME/DISCARD/CORRECT、actorId:String、cookingEventId:String?、requestId:String |
| MatchLine | ingredientId:String、required:String?、available:String?、unit:Unit、status:ENOUGH/MISSING/UNKNOWN；UNKNOWN 不能当足够 |
| RecipeMatch | recipeId:String、recipeVersion:long、servings:String、lines:MatchLine[]、coverage:String、earliestExpiry:String?、checkedAt:String |
| PlanItem | recipeId:String、servings:String；同 slot 不允许重复菜谱，合并份数后提交 |
| PlanSlot | householdId:String、date:String、mealType:BREAKFAST/LUNCH/DINNER、items:PlanItem[]、locked:boolean；version |
| RecipeSnapshot | recipeId:String、recipeName:String、recipeVersion:long、yieldServings:String、totalKcal:String?、ingredients:RecipeIngredient[] |
| CookingEvent | householdId:String、actorId:String、cookedAt:String、servings:String、recipe:RecipeSnapshot、planSlotId:String?、requestId:String；不可编辑删除 |
| MealEntry | userId:String、date:String、zoneId:String、mealType:BREAKFAST/LUNCH/DINNER/SNACK、name:String、recipeId:String?、cookingEventId:String?、amount:String、basis:SERVING/GRAM/MANUAL/UNKNOWN、basisKcal:String?、totalKcal:String?、note:String、imageId:String?；version |
| DaySummary | date:String、knownKcal:String、unknownCount:int、entryCount:int |
| PeriodSummary | from:String、to:String、knownKcal:String、unknownCount:int、averageKnownKcal:String、days:DaySummary[] |
| Media | ownerUserId:String、householdId:String?、scope:PERSONAL/HOUSEHOLD、mimeType:String、sizeBytes:long |

表中所有写入请求短名均补全Request后缀，例如Register即RegisterRequest、CreateHousehold即CreateHouseholdRequest。标签数组去重、每项1–40字符最多30项；菜谱食材1–100项且ingredientId唯一；durationMinutes取1–1440整数；location最长120；Recipe.steps至少1条；图片ID必须属于可访问范围且scope匹配。请求中的enum不允许任意字符串。标准食材由受控迁移维护固定UUID和别名，普通用户从其选择；新增标准食材作为AC的字典变更提交，不允许各客户端生成不同的标准ID。

请求 schema 命名规则：新建 `CreateXRequest` 含实体的可编辑字段，更新 `UpdateXRequest` 在相同字段上增加 expectedVersion；具体清单如下，避免 Agent 把 DTO 整个复制为请求：

- CreateHousehold：name；UpdateHousehold 暂无。CreateInvitation：无 body。JoinHousehold：code。
- Register：username/displayName/password/zoneId；Login：username/password。UpdatePreference：likedTags/excludedIngredientIds/expectedVersion。
- CreateRecipe：name/steps/ingredients/yieldServings/durationMinutes/tags/imageId/totalKcal；UpdateRecipe：同上+expectedVersion；归档单独 ArchiveRecipeRequest：archived/expectedVersion。
- CreateInventoryBatch：ingredientId/quantity/unit/location/expiresOn；UpdateInventoryBatch：location/expiresOn/expectedVersion（数量走变动接口，不直接 PUT）。InventoryChangeRequest：afterQuantity/reason/expectedVersion；reason 仅 CONSUME/DISCARD/CORRECT，前两者要求 after<before。
- MatchRequest：entries:[{recipeId,servings}]，1–100 条；同菜谱最多一条。每道菜匹配独立计算，不承诺合起来库存够用。
- ReplacePlanSlotRequest：items/expectedVersion；SetPlanLockRequest：locked/expectedVersion。PlanCommitRequest：slots:[{date,mealType,expectedVersion,items}]，最多 42 个不同 slot。
- CookingPreviewRequest：recipeId/servings/planSlotId?；CookingPreview：recipeVersion/servings/planSlotId?/allocations:[{batchId,expectedVersion,ingredientId,quantity,unit}]/shortages:MatchLine[]/checkedAt。
- ConfirmCookingRequest：recipeId/recipeVersion/servings/planSlotId?/cookedAt/allocations（同 preview 结构）；请求不携带可信 actorId 或库存最终余额。
- CreateMealEntry：date/mealType/name/recipeId?/cookingEventId?/amount/basis/basisKcal?/note/imageId?；UpdateMealEntry：同上+expectedVersion。totalKcal 由服务端计算，不能直接传。
- AuthSession：token/expiresAt/user:User；注册返回 User，随后显式登录。

数据库通过 Flyway `V1__baseline.sql` 建 users、sessions、households、memberships、invitations、preferences、ingredients、ingredient_aliases、recipes、recipe_ingredients、wishlists、inventory_batches、inventory_movements、plan_slots、plan_items、cooking_events、meal_entries、media、idempotency_requests。子行有明确外键与顺序；历史快照用 JSONB 存不可变内容；不要依赖被修改的菜谱实时联表重算历史。

约束：非负库存 CHECK；成员活动关系 userId 唯一（一个家庭）；收藏唯一；标准名与别名使用统一归一化策略且不能映射到不同食材；slot 唯一(householdId,date,mealType)；request 唯一(actorId,key)。每次查询以已验证 actor 和 household 范围限制，不能只按传入资源 ID 查库。历史外键不能级联删除历史；菜谱归档而非物理删除。为 householdId+日期、userId+日期、batch ingredientId+expiry 建查询索引。

InventoryMovement.requestId和CookingEvent.requestId保留为业务幂等key的回显，不等于错误traceId；客户端仅用它关联已提交意图。AuthSession token只由AuthRepository内部接收，禁止日志和通用缓存。数量和时间的所有OpenAPI regex在JSON中正确转义，契约测试必须编译解析后的pattern，写入JSON时使用序列化器，不手工重复或遗漏反斜线转义。

### 8.1 生成器和数据约束补全

所有枚举定义为命名components并用$ref：Unit、PlanMealType、EntryMealType、MovementReason（含COOK）、ChangeReason（不含COOK）、KcalBasis、Role、MediaScope、MatchStatus、ExpiryStatus。数组对象定义MatchEntry(recipeId,servings)、CookingAllocation(batchId,expectedVersion,ingredientId,quantity,unit)、PlanSlotWrite(date,mealType,expectedVersion,items)，不让生成器自创inline类名。表中的枚举字面量是这些组件的值，不是重复定义。

RecipeIngredient与InventoryBatch.unit必须等于Ingredient.unit。trackStock为必填，无默认值；false表示菜谱保留该食材但不纳入库存匹配、分配或扣减，预览明确展示不扣库存的食材。单位不匹配/缺少必需数量拒绝保存，不把坏数据归为UNKNOWN。MatchLine额外允许status=UNKNOWN仅表示历史迁移数据缺少无法计算的需求；对应required/available改为可空String，客户端不能视为足够，confirmCooking拒绝未知的需库存行。正常新数据不会产生这种状态。

coverage=已足量的trackStock=true食材行数/全部trackStock=true行数，HALF_UP保留3位；UNKNOWN计入分母不计分子；没有需库存行时coverage="1"。earliestExpiry是这些食材中quantity>0、未过期且可用于匹配的库存最早已知到期日，无则null；排序用数值coverage，earliestExpiry按日期升序且null最后。所有批次同食材可用数量求和；不预留未来库存。

User.username输入先Locale.ROOT转小写，再校验[a-z0-9_]{3,32}，存储/唯一约束同一形式。食材标准名和别名统一NFKC→trim→合并连续空白为一个空格→Locale.ROOT小写；ingredient_aliases同时存标准名与别名，一个normalized_name唯一索引防冲突。

MealEntry.zoneId来自User，不允许请求覆盖。若同时提供recipeId和cookingEventId，必须等于烹饪快照recipeId；只给cookingEventId时服务端补其recipeId。关联项必须当前可访问，已有个人历史快照不因退出家庭而被删除。Recipe图片必须同家庭HOUSEHOLD；食记图片必须本人PERSONAL。RecipeSnapshot由真实菜谱产生，recipeId/version必填；外食只进个人食记，不创建无菜谱CookingEvent。

Recipe.steps存JSONB有序字符串数组；totalKcal是整个yieldServings产出的热量，不是每份。slot最多10种菜谱。active=false食材不可用于新配方/新批次，已有引用仍可展示、消耗、做饭和原样保留；新加该食材引用拒绝。DaySummary包含请求区间所有自然日，无记录为0/0/0。averageKnownKcal=knownKcal/区间自然日天数，HALF_UP到3位，标注“已知摄入日均”，未知条数同时展示，不宣称完整营养平均。

User.householdId由active membership查询，不在users冗余保存；活动成员唯一索引用UNIQUE(user_id) WHERE active，保留inactive历史。plan_items带position、UNIQUE(slot_id,recipe_id)和UNIQUE(slot_id,position)。为高频外键查询建索引。去掉未使用WishlistItem wire schema，数据库wishlists关系仍保留。

idempotency_requests列：actor_id、key、operation_digest、http_status（事务占位时可null）、response_body JSONB（占位/204可null）、created_at；PK(actor_id,key)。占位和最终成功记录在一个事务，禁止提交未完成占位。JSONB序列化由config中的单一JsonbCodec处理，业务persistence复用，不各写一份字符串转义。

## 9. HTTP API 首版清单

所有路径以 `/api/v1` 开头。`H=/households/{householdId}`。表中前缀简写必须在 openapi.json 展开。path 参数在生成方法中放在 body 之前；所有列表支持第 7 节分页。`operationId` 即生成方法名，tag 即 API 类前缀。Idempotency-Key只为第7节规定的已登录新建写入操作生成必填header；register/login/logout、只读matchRecipes/previewCooking不添加此参数。

| Tag / owner | 方法与路径 | operationId | 输入 → 返回 |
| --- | --- | --- | --- |
| Auth / E | POST /auth/register | register | RegisterRequest → User |
| Auth / E | POST /auth/login | login | LoginRequest → AuthSession |
| Auth / E | POST /auth/logout | logout | 无 body → 204 |
| Profile / E | GET /me | getMe | → User |
| Profile / E | GET、PUT /me/preferences | getPreferences、updatePreferences | → Preference；PUT UpdatePreferenceRequest |
| Household / E | POST /households | createHousehold | CreateHouseholdRequest → Household |
| Household / E | GET H | getHousehold | → Household |
| Household / E | GET H/members | listMembers | → MembershipPage |
| Household / E | POST H/invitations | createInvitation | → Invitation，OWNER |
| Household / E | POST /households/join | joinHousehold | JoinHouseholdRequest → Membership |
| Household / E | DELETE H/members/{userId} | removeMember | expectedVersion query → 204，OWNER |
| Inventory / C | GET /ingredients | listIngredients | q? query → IngredientPage |
| Recipe / A | GET H/recipes | listRecipes | q?,tag?,ingredientId?,archived=false query → RecipePage |
| Recipe / A | POST H/recipes | createRecipe | CreateRecipeRequest → Recipe |
| Recipe / A | GET、PUT H/recipes/{recipeId} | getRecipe、updateRecipe | → Recipe；PUT UpdateRecipeRequest |
| Recipe / A | PUT H/recipes/{recipeId}/archive | setRecipeArchived | ArchiveRecipeRequest → Recipe |
| Recipe / A | GET /me/wishlist | listWishlist | → RecipePage，只查本人当前家庭 |
| Recipe / A | PUT、DELETE /me/wishlist/{recipeId} | addWishlist、removeWishlist | 无 body → 204；均幂等 |
| Records / D | GET H/cooking-events | listCookingEvents | recipeId?,from?,to? query → CookingEventPage；A 用它构建做过列表 |
| Inventory / C | GET H/inventory-batches | listInventoryBatches | ingredientId?,expiryStatus:ALL/VALID/SOON/EXPIRED? → InventoryBatchPage |
| Inventory / C | POST H/inventory-batches | createInventoryBatch | CreateInventoryBatchRequest → InventoryBatch |
| Inventory / C | PUT H/inventory-batches/{batchId} | updateInventoryBatch | UpdateInventoryBatchRequest → InventoryBatch |
| Inventory / C | POST H/inventory-batches/{batchId}/movements | changeInventory | InventoryChangeRequest → InventoryMovement |
| Inventory / C | GET H/inventory-movements | listInventoryMovements | batchId? query → InventoryMovementPage |
| Inventory / C | POST H/recipe-matches | matchRecipes | MatchRequest → RecipeMatch[]，只读计算，无副作用 |
| Planning / B | GET H/plan-slots | listPlanSlots | from/to query，最多21天 → PlanSlotPage |
| Planning / B | PUT H/plan-slots/{date}/{mealType} | replacePlanSlot | ReplacePlanSlotRequest → PlanSlot |
| Planning / B | PUT H/plan-slots/{date}/{mealType}/lock | setPlanSlotLock | SetPlanLockRequest → PlanSlot |
| Planning / B | POST H/plan-commits | commitPlan | PlanCommitRequest → PlanSlot[]，整批原子保存 |
| Records / D+E | POST H/cooking-previews | previewCooking | CookingPreviewRequest → CookingPreview，只读计算 |
| Records / D+E | POST H/cooking-events | confirmCooking | ConfirmCookingRequest → CookingEvent |
| Records / D | GET、POST /me/meal-entries | listMealEntries、createMealEntry | GET from/to→MealEntryPage；POST CreateMealEntryRequest→MealEntry |
| Records / D | PUT、DELETE /me/meal-entries/{entryId} | updateMealEntry、deleteMealEntry | PUT UpdateMealEntryRequest→MealEntry；DELETE expectedVersion→204 |
| Records / D | GET /me/meal-summary | getMealSummary | from/to query，最多366天 → PeriodSummary |
| Media / E | POST /media | uploadMedia | multipart file+scope+householdId? → Media |
| Media / E | GET /media/{mediaId}/content | getMediaContent | → JPEG/PNG 二进制，校验资源权限 |

API 中初始版本规则：不存在的计划 slot 视为 version=0，首次写入 expectedVersion=0，创建后 version=1；已存在 slot 从 1 递增。其他实体创建 version=1；getPreferences 在注册时已初始化 version=1。不存在 slot 不必在 GET 中返回占位对象，客户端补空位时标明 version=0。

Register、Login 不要求已有登录；/ingredients 要求登录；家庭接口都核验成员；/me 从会话取用户；移除不能删除 OWNER 自己。普通成员可维护家庭菜谱、库存和计划。所有路径、请求、成功/失败响应、示例和参数上限都写入 OpenAPI，不允许留下 additionalProperties 的无类型业务模型。

列表排序覆盖第7节通则：listPlanSlots按date升序、BREAKFAST/LUNCH/DINNER顺序、id升序；listMealEntries按date降序、餐次顺序、createdAt降序、id升序；inventory按expiresOn升序null最后、createdAt/id升序；ingredients按name/id升序；其余按createdAt降序、id升序。21天读取仅为14天规划加前7天去重参考；生成和commit仍最多14个连续日期、42个slot，服务器校验实际跨度。

logout由生成AuthApi对应Controller实现。关闭Spring Security默认logout、formLogin、httpBasic；仅Bearer API不使用cookie认证，CSRF策略显式配置并测试。路由比对唯一框架排除路径为/error，其他必须登记，Spring自动HEAD/OPTIONS按规范归一化后比较，不能用宽泛通配排除业务路由。

## 10. 关键业务流程的确定语义

### 10.1 做饭、锁顺序与幂等

所有家庭写入采用同一顺序：认证及基本输入校验 → 事务内声明幂等行（如该操作需要） → 锁定household行 → 核验成员 → 按id锁recipe → 按date/mealType锁plan_slots → 按id锁inventory_batches → 修改和成功响应记录 → 提交。无需的锁跳过；禁止反向获取。首版用household行锁串行化同家庭写入，降低六人项目中的跨服务锁复杂度；吞吐不足时再升级细粒度锁并重做并发测试。个人食记只锁本人目标行。加入/移除也遵守家庭锁顺序，跨家庭加入冲突由活动成员唯一索引映射ALREADY_IN_HOUSEHOLD。

相同actor/key并发：第二次插入在唯一索引等待第一次结束。第一次提交则第二次在事务外捕获唯一冲突，开启新事务读取成功响应并复核当前权限后重放；第一次回滚则第二次可正常插入执行。禁止在失败事务中继续SQL。数据库锁等待超时返回TEMPORARILY_UNAVAILABLE503（Retry-After秒数），按可重试暂时失败处理，不宣称操作失败回滚已经被客户端证明；客户端仍按相同key查证/重试。

预览按家庭今天排除expiresOn早于今天的批次，日期相同按createdAt/id，未知到期日最后。临期为今天起3天含端点。仅trackStock=true的食材参与，按份数缩放并CEILING到0.001，用户确认分配，shortages未解决不能提交。

提交重新验证成员、家庭范围、菜谱版本、批次版本、实际份数、单位、未过期、每个需库存食材的分配总量以及库存足够；分配不能包含不需库存的食材、负数或别家庭批次。缺货INSUFFICIENT_STOCK，过期EXPIRED_BATCH，旧版本VERSION_CONFLICT。

在同事务扣库存并增加版本、写InventoryMovement、CookingEvent和幂等成功结果。任一步异常全部回滚，不提交占位行。planSlotId只表示来源，不把整餐标记完成、不删计划、不自动生成食记；烹饪时关联slot需同家庭且包含对应菜谱。允许同菜谱分次做饭，每次用户新操作有新key。

重试复用原内容与key；修改内容重新生成key。日志使用traceId关联本次HTTP请求，并可记录脱敏幂等key用于排查，不能打印凭证或全请求。

### 10.2 排餐、推荐和预览

B 先用发起用户本人的口味、时长、忌口过滤候选，再调用 AC 的批量匹配。候选缺少已知食材信息时不能承诺符合忌口。规则推荐按以下顺序稳定排序：个人收藏优先、覆盖率高优先、可利用临期食材优先、最近7天计划出现次数少优先、recipeId 升序打破平局。规则随机采用相同过滤后的候选，允许测试注入种子，不需要大模型服务。

预览仅存当前页面状态，最多14天×3餐，默认只填空位；每餐可手动加多菜。重新生成保留锁定内容；候选不足明确显示未填，不能放宽忌口。客户端把所有待写 slot 的原始 expectedVersion 一起提交。服务器锁定这些 slot、验证菜谱家庭和归档状态；任何冲突或锁定返回409，整批不保存。首次并发创建相同 slot 的唯一冲突映射 VERSION_CONFLICT。

已锁定 slot 的 items 不可修改，先单独解锁再编辑；锁定 API 仅改 locked，不能顺便覆盖 items。清空餐次使用 items=[]，保留 slot 版本。库存匹配只是当前参考，不预留原料；未来计划不承诺实际做饭时仍有库存。

### 10.3 食记与热量

SERVING：amount 是份数，basisKcal 是每份热量，total=amount×basisKcal；GRAM：amount 克，basisKcal 为每100克，total=amount×basisKcal÷100；MANUAL：amount=1，basisKcal 为本条总热量；UNKNOWN：basisKcal/totalKcal=null，amount>0。所有保存的计算基准形成快照。

用户从菜谱/烹饪记录预填后仍需确认自己实际吃了多少。编辑食记使用本人记录版本，默认保留已有快照，可显式改写摄入字段，但不能通过改菜谱联动历史。汇总只含本人未删除的记录，输出已知热量总和和未知条数。周界限周一到周日；按日历月统计，客户端传入明确 from/to（两端含）。删除只影响个人食记及统计。

### 10.4 会话、家庭和图片

使用 Spring Security 处理认证上下文，密码用 BCrypt（请求长度至少10字符且UTF-8不超过72字节），不手写哈希算法。登录生成安全随机256位 opaque token，数据库仅保存 token SHA-256、用户和24小时过期时间；登出撤销服务端会话。令牌首版仅存客户端内存，进程退出后重新登录。登录失败统一提示，不泄露用户名是否存在，添加可配置的失败尝试限速并测试。

邀请仅 OWNER 创建，随机128位一次性 code，24小时失效，数据库存哈希。加入家庭时锁定邀请并原子消费；用户已属于家庭则拒绝。移除成员撤销其家庭访问，客户端收到权限失效清掉家庭缓存；不能声称远程抹除已导出资料。

每次切换登录态取消旧请求、递增 sessionGeneration、清理账号缓存；旧回调如 generation 不匹配必须丢弃。缓存 key 含 userId+householdId+请求规范化参数，避免串号。

图片首版一张，JPEG/PNG、最大5MB；服务端检查实际文件格式和尺寸，使用随机文件名存受控 media 目录，不接受客户端路径；限制解码尺寸防止极端图片。家庭菜谱图 scope=HOUSEHOLD，食记图默认PERSONAL。使用鉴权下载端点，不能直接开放静态目录。图片上传和菜谱保存是独立操作，未关联图片可在后续清理任务中回收，失败不能阻断已有文本数据读取。

邀请码使用128位随机值的Base32大写无填充编码（26字符）；输入移除空白并转大写，解码后校验长度及规范重编码，哈希规范值。通过复制/系统分享传递。注册可通过USERNAME_TAKEN暴露占用情况；登录统一错误只减少登录接口的用户名枚举，不宣称全系统隐藏用户名存在性。

服务端检查图片解码像素不超过1600万后重编码JPEG/PNG，去除EXIF/GPS，不直接转存原始文件。客户端压缩和服务端验证都不可省略。MainActivity发现没有内存会话时先进入LOGIN，不先恢复受保护页面；普通旋转保留草稿，进程死亡后的未提交输入允许丢失，待确认的写入恢复例外见第11节。

## 11. UI、缓存和错误处理

F 提供 `Theme.Civa`、统一 colors/dimens/styles、`civa_view_loading.xml`、`civa_view_empty.xml`、`civa_view_error.xml`、`civa_item_recipe.xml`、表单错误和确认对话框；各 feature 使用前缀 ac_/bd_/ef_ 命名自身资源，共享资源用 civa_，避免资源合并冲突。主入口路由名称固定；首页读取计划、临期批次和个人摘要，不复制业务数据表。

窄窗口默认单栏；可用宽度达到600dp使用侧边导航，列表详情页面按空间扩展；可用资源限定符及运行时宽度共同实现，禁止锁屏幕方向。旋转、分屏、字体放大、键盘弹出时保留日期、筛选、选中项和未提交输入；SavedStateHandle 保存小型页面状态，不能存整张图片和巨大列表。页面重建不重复发起确认写入。

Room的只读缓存表为CacheEntry：cacheKey 主键、userId、householdId?、payloadJson、fetchedAt；序列化缓存对应已验证 DTO，不允许网络 DTO 直接充当全部业务数据库 Entity。进入页/手动刷新从服务器取最新，失败可显示缓存和时间。断网或服务不可达时不进入“保存成功”，不排队共享写入。首版不做后台实时订阅，成员之间通过刷新看到变化。

网络 connectTimeout=10s、readTimeout=20s、writeTimeout=20s。GET 错误允许用户重试；401 统一清理会话进入登录；409 保留用户输入、展示刷新后重试选项，不直接覆盖；写请求超时显示“结果待确认”，使用相同 key 查证/重试。HTTP仅在debug开发配置允许，release 禁止明文传输。

导航：小于600dp用BottomNavigationView，达到600dp用NavigationRailView；菜谱、冰箱、食记支持列表/详情两栏。app.AppNavigator根据窗口宽度决定详情在右栏或全屏，feature只请求Route。普通旋转/配置变更保存状态；进程死亡触发重新登录后清掉未提交草稿，不把SavedStateHandle承诺为持久存储。

所有需幂等的确认页在首次创建时生成key放SavedStateHandle；输入改变后使旧key失效，提交后待结果时锁定payload，结果未知时不允许用新key重做同一意图。成功后丢弃。为防进程死亡后重复扣库存，第一次提交前在本机私有Room新增PendingWrite表，存userId、householdId?、operationId、key、规范化payload、createdAt（不含token/密码）；上传图片需保留同一压缩文件的私有临时引用和哈希。此表只记录已经发起的请求，不是离线写入队列。重启先登录同一账号、getMe复核权限，再提示用户核实/使用相同key重试；不自动重放。不同账号不能读该表；权限失效则标记人工核查，不偷偷用新key重试。成功或用户确认放弃后的清理策略要记录，放弃不等于服务端回滚。PendingWrite不能按普通缓存TTL自动删除。

CacheEntry分页key包含page/size和全部过滤排序参数；登出清空该用户CacheEntry及图片内存缓存。启动清理7天前缓存；PendingWrite独立保存，仅同账号恢复，不能显示为新账号草稿。全局401跳过/auth/*，非认证接口401才清登录；HOUSEHOLD_ACCESS_REVOKED只清家庭状态与缓存。

## 12. EF 必须生成的文件与交付标准

| 文件/任务 | 必须包含 | 验收 |
| --- | --- | --- |
| contracts/openapi.json | 第7–9节所有 schemas、operation、错误、鉴权及示例 | 验证通过、双方可生成并编译 |
| generator-*.json | 固定选项和包名 | 重复生成输出一致 |
| public-surface.json | schemaVersion、每个手写公共symbol完整签名/owner/consumers，以及allowedDependencies | 改签名和越界依赖负例检查失败 |
| Android Gradle模块 | 第4节唯一依赖图、Manifest、Java目录、公共资源、五入口 | `assembleDebug`成功，模拟器能导航 |
| core-data公共文件 | ApiProvider、SessionStore、统一鉴权/错误、账号隔离缓存 | mock服务器响应与会话切换测试通过 |
| server | 生成接口实现、认证/家庭真实最小链路、迁移、统一错误、权限和事务入口 | 两账号家庭读写可以验证 |
| docs/generated/CONTRACT_INDEX.md | 从契约生成 operationId→path→owner→request/response 索引 | CI重新生成无差异 |
| contracts/changes/README.md | 第14节规则与可复制模板 | 团队有统一异常登记入口 |
| scripts/contract_guard.py | 本节及第14节契约门禁 | 至少有新增/修改/漏登记/绕路路由等负例 |
| scripts/preflight.ps1/.sh | fail-fast，执行第15节所有已配置任务，保留退出码 | 任一失败非零退出，不能假成功 |
| .github/workflows/ci.yml | 契约、构建、测试、报告；明确权限与失败状态 | PR和main实际触发并观察结果 |
| README/.env.example | 本机运行、配置项、模拟器、常见错误、命令 | 另一成员干净克隆能复现 |
| docs/verification/BOOTSTRAP.md | 命令、环境、实际结果、未实现与未执行项 | 不把Mock或501写成完成 |

骨架覆盖所有接口和页面入口，但不要求在架构初始化阶段实现全部业务。未实现的服务接口显式返回501 CONTRACT_NOT_IMPLEMENTED，页面显示“功能待接入”；禁止返回假成功。测试fixture和FakeApiProvider只放src/test，不装配进debug或release。生产路径不得加载FakeApiProvider。骨架先实现 register/login/logout/getMe/createHousehold/createInvitation/joinHousehold/getHousehold/listMembers 的真实最小链路；其余按职责接入。

生成根 AGENTS.md 明确：先读规格和 contracts；只能通过正式 API 跨组访问；禁止改生成代码；新增跨组 API 必须走第14节；禁止削弱门禁和用测试绕过。各组任务指令必须包含基线 commit、Issue、允许目录、operationId、样例、验收标准。

补充交付：统一NotImplementedResponses辅助函数返回501，测试枚举未实现operationId并生成README和BOOTSTRAP中的机器维护列表；手写代码不能散落假成功。模拟器导航为手工验收，记录步骤、环境、实际结果，未执行标UNVERIFIED。

生成.github/CODEOWNERS仅覆盖受控路径：/.github/、/scripts/、/contracts/、/AGENTS.md、/PROJECT_DOC.md和根/server构建配置；实际EF GitHub账号或team需由仓库信息确认，不把文字“EF”当有效owner。CI设置permissions:contents:read，checkout fetch-depth:0，Android/server分别用各自Wrapper，server job使用独立postgres:16数据库service。无法获取owner或配置规则就报告缺口，不编造保护已生效。

## 13. 本地运行与环境

每人可在自己的电脑启动服务、数据库和模拟器；只有共享测试需两个客户端访问同一服务。模拟器本机地址http://10.0.2.2:8080/，其他电脑使用可达地址。不要求学校设备或公网部署。

application.yml必须显式引用，不依赖DB_URL的自动宽松映射：

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jackson:
    deserialization:
      fail-on-unknown-properties: true
  flyway:
    clean-disabled: true
    out-of-order: false
server:
  port: ${SERVER_PORT:8080}
civa:
  media-dir: ${CIVA_MEDIA_DIR}
```

compose --env-file只给容器变量，本机bootRun不会自动读.env。选用启动shell显式设置变量；.env.example列清数据库和媒体目录变量，实际.env忽略提交。PowerShell在本机设置$env:DB_URL、$env:DB_USERNAME、$env:DB_PASSWORD、$env:CIVA_MEDIA_DIR（值取本人配置）；macOS/Linux用export。README不把真实密码写入命令样例或日志。

```powershell
# 根目录，先设置本人环境变量并配置SDK
python --version
python -m pip install -r scripts/requirements.txt
.\gradlew.bat validateContract generateClient generateServerApi
.\gradlew.bat assembleDebug testDebugUnitTest lint
docker compose --env-file .env -f infra/compose.yaml up -d
# server目录：先使用开发库
.\gradlew.bat bootRun
# 另一个终端设置DB_URL等指向独立civa_test后，在server目录运行
.\gradlew.bat test bootJar
```

macOS/Linux用./gradlew替换.\gradlew.bat；各命令保持对应工作目录。前置说明必须覆盖本机JDK17、SDK、Python、Docker Compose或本机PostgreSQL安装。以上命令是目标运行方式，本次文档修改未执行构建。

CIVA_API_BASE_URL通过用户~/.gradle/gradle.properties或-P传入，local.properties仅SDK路径；使用该值的Android模块开启buildFeatures.buildConfig=true，通过AppContainer传递给core-data。debug默认模拟器地址；release任务强制显式HTTPS地址，缺少/明文就失败。src/debug/AndroidManifest.xml引用仅src/debug/res/xml存在的网络安全配置，base-config允许开发明文；main/release禁止明文且不引用debug文件。README说明仅在受信任本机/局域网联调，Windows防火墙仅为私有网络和需要的来源放行8080，不关闭防火墙或暴露数据库端口。

测试启动在任何迁移或清理之前解析连接URL并查询current_database，要求实际库名以_test结尾，缺库/错库/无法连接即失败；测试连接可用TEST_DB_*配置映射以避免覆盖开发库。Flyway cleanDisabled=true，测试只在已验证测试库事务回滚或TRUNCATE测试表。CI同样执行此保护，不能仅凭profile名称放行。服务端测试不需要Android SDK，Android编译不需要数据库在线。

本地迁移与主线不一致：先检查flyway_schema_history和当前脚本，未应用迁移可重新编号；已应用的共享迁移用追加纠正迁移，不改历史。个人可丢弃开发库在备份并明确确认后重建，只操作本人指定数据库；禁止脚本自动drop数据库。没有确认或含需保留数据时采用前向迁移。

## 14. 未登记接口的发现、归档和处理

### 14.1 什么需要登记

接口包括：跨模块Java方法/DTO、HTTP路径/方法/参数/返回字段、公共事件/导航参数、数据库共享语义、错误码、单位/日期/权限/事务含义。不仅是名为 interface 的文件。

私有辅助方法、模块内未被其他组引用的实现类不必登记。新增对外方法即使旧代码仍能编译，也属于契约扩展。改字段含义、排序、舍入或错误语义即使签名没变也属于变更。feature包里绕开正式目录新开HTTP请求同样违规。

### 14.2 正常变更：先提议再并行依赖

提出方建立一个 `contracts/changes/IC-<GitHub-Issue号>.md`，记录需求、现有接口为什么不能满足、旧/新契约、影响方、测试和迁移。无Issue时用IC-YYYYMMDD-组别-序号，之后补Issue关联。

状态：PROPOSED → ACCEPTED → IMPLEMENTING → VERIFIED → CLOSED；可以REJECTED。EF负责契约决策，涉及业务语义时由提供组和受影响调用组核对。无需每次常规功能都人工逐行审代码；只有公共边界变化需要可追溯的决定。Agent不能自行把自己提出的变更标成EF已接受。

ACCEPTED记录作为受控基线先进入main，标明允许改变的operationId/schema/symbol和规范化摘要；随后实现PR引用它。记录只有授权的EF决策可接受；如果GitHub配置无法技术限制，由EF在合并时核验，不能把自填状态当授权。紧急变更同样先单独合入授权IC，后合实现，不提供跳过门禁的同PR例外。

### 14.3 发现已经私自增加接口时

立即记录DETECTED并暂停该PR的集成；其他不依赖它的工作继续。保留原PR、分支和完整commit SHA，记录 base SHA、head SHA、发现时间、作者、涉及文件和调用方。不要执行reset --hard、强推或删除他人分支来“修好主线”。

1. 在该PR补建IC记录，标记UNREGISTERED，状态DETECTED；文件放contracts/changes，包含修改前后声明、具体证据及需求来源。
2. 原始实现保留在Git提交历史，记录稳定的commit与文件链接；分支名会移动，不能只记录分支。必要时EF保留指向该提交的archive引用，推送仍遵守仓库权限。对未提交修改先由作者保存到自己的工作分支；Agent不得自动提交混杂的无关工作。
3. 文档归档仅存脱敏diff摘要、哈希、日志和决策。不要把旧Java源码复制到app/src或server/src下“归档”，否则仍会编译；不要复制令牌、数据库数据或私人信息。包含密钥时单独处置泄露、撤销凭证，不能把秘密写进归档。
4. 搜索全部调用方、DTO、HTTP路由、数据库迁移和测试，给出机器可读影响报告；标记签名兼容/语义兼容/数据兼容，未知就写未验证。
5. EF与受影响组按下表选择处置；记录原因和目标commit。未经决策不得把新接口推广给其他组。
6. 修复后跑完整门禁；原始违规检测和最终修复结果都留在同一IC，不用删除记录“恢复干净”。

| 分类 | 处理方案 | 验收 |
| --- | --- | --- |
| 只是内部辅助 | 缩小可见性/移回模块内部，登记NOT_PUBLIC结论 | 无跨模块调用或网络暴露 |
| 已有接口能完成 | 调用已有接口，删除重复的运行时代码 | 所有调用方迁回正式入口，原需求仍满足 |
| 合理的兼容新增 | 补正式契约、owner和schema，从契约重生成 | 旧调用方不变，新行为测试通过 |
| 破坏性但必要 | 同一集成变更更新所有本仓调用方；已发布客户端另给v2或兼容过渡 | 迁移、旧客户端行为和数据保留有证据 |
| 暂时必须兼容两套 | 单向adapter委托到唯一实现，写明移除条件和负责人 | 无双写、循环调用或两套业务算法 |
| 无实际需要/风险不可接受 | 不纳入main；以普通修复提交撤回该改动 | 需求决策和未实施理由归档，其他人的工作保留 |

异常状态：DETECTED → TRIAGED → ACCEPTED_FOR_REWORK/REJECTED/NOT_PUBLIC → FIXED → VERIFIED → CLOSED。CLOSED必须记录最终处置、原始和修复commit、测试证据、正式契约版本；延期适配未清理不能假称已彻底关闭。

### 14.4 已经混入main的情况

先判断是否影响构建、权限、数据正确性。立即停止依赖受影响接口的新集成。修复分支从当前main建立，选择小范围向前修复或普通revert；revert混合提交前先拆清其他人的有效改动，不能盲目整体撤销。

数据库迁移一旦应用，不得删除或修改旧迁移伪装未发生；追加纠正迁移，并说明数据备份和验证。代码revert不等于数据库已回滚。是否撤销数据必须按实际环境和授权另行决定。修复成功后同步各组并要求重新基于最新main验证。

### 14.5 归档模板

```markdown
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

IC的YAML front matter是机器权威字段，正文供人解释；机器字段type/status与正文不得矛盾。contract_guard.py digest根据提案文件计算，不能手填。摘要输入是按allowedOperations/schemas/symbols选中的变更前后片段及传递$ref闭包、security/公共参数等受影响定义，新增/删除用显式哨兵表示；key排序、UTF-8无空白JSON后SHA-256。去掉只有description/summary/example变更的纯说明项但保留所有行为约束，digest格式sha256:<64hex>。先准备最终契约提案（保留在提出分支或JSON附件），再提交ACCEPTED元数据到main；实现必须匹配片段摘要。范围外变化不能只靠扩大列表自我授权。

contract_guard.py draft --base SHA --head SHA自动生成UNREGISTERED/DETECTED草稿，填SHA、operation/schema/symbol diff、静态调用方搜索证据（不保证穷尽）；不覆盖已有IC，不替人填写决定或ACCEPTED。只在明确输出目录写脱敏结果。

Flyway：初始V1__baseline.sql保留；后续命名VYYYYMMDDHHMMSS__ac_description.sql（组别写description不进入版本号），秒级时间戳仍可能碰撞，CI检查所有版本唯一及新增版本大于主线最大已应用版本，冲突时仅给尚未合并/应用的迁移重新编号。outOfOrder=false。所有DDL/共享数据语义迁移都需要IC；单纯修复本模块测试数据不需要。已应用脚本不可重命名；本地旧分支提前应用更大版本时按第13节恢复。

不采用“紧急同PR仅UNVERIFIED但仍自动合并”的例外：UNVERIFIED不能满足必需状态检查。紧急变更仍由EF先把授权IC合入受保护main，再运行实现PR；EF的Approve是决策证据，不能直接代替检查。开启新提交撤销旧批准。若仓库不能强制分支保护，只能标提示型，由EF人工核验。

首次空仓库没有可信main基线时，EF先在本地完成A–C并审阅候选快照，再由EF建立受控的初始化main提交；记录此举是一次性信任初始化，不宣称普通PR规则已经保护它。随后用独立验证PR证明普通门禁及CODEOWNERS生效。外部/普通PR在无基线时必须失败，不自动开启bootstrap。

### 14.6 自动门禁的具体实现要求

生成Agent实现以下检查，不能只输出一份“请遵守”的说明：

- 从目标分支基线到当前提交比较OpenAPI的paths/methods/operationId/参数/请求响应schema/security。递归解析引用，新增operation也必须报告，不能只检测删除。规范化JSON用于摘要，排除纯格式变化。
- public-surface检查编译后的手写公开类/方法与登记签名，并验证Gradle依赖图和ArchUnit包依赖；Java自动生成部分由OpenAPI检查覆盖。
- 启动服务端测试，枚举Spring实际RequestMapping（排除明确列出的框架路由），与OpenAPI方法/路径双向比较。多了返回FAIL，少了也FAIL；避免在非约定文件夹偷偷添加Controller绕过路径检查。
- Retrofit网络接口只能来自生成包；feature不能依赖OkHttp、直接URL连接、Socket、WebView网络桥或反射调用其他feature。包/字节码检测和构建依赖检查一起执行；不得把简单rg结果当成完整安全证明。
- 每个契约变化必须匹配基线已接受的IC许可范围和摘要。修改IC记录为ACCEPTED但基线没有授权，不得自动通过。实现范围扩展要重新登记。
- 主线里的检查规则、生成器选项、CI、public-surface、契约基线和AGENTS是受控文件。普通功能PR不能通过同时改检测器/白名单使自身通过。CI要从可信目标分支执行门禁逻辑，PR代码只在无凭证隔离job中构建测试；不要用pull_request_target带写权限执行PR脚本。
- 输出build/reports/contracts/report.json和report.md，字段含base/head SHA、变更类别、受影响symbol、IC编号、PASS/FAIL/UNVERIFIED、证据路径。上传CI artifact；有保留期的artifact不能替代Git中长期IC摘要及commit。

至少有以下门禁自测：新增端点无IC失败；把既有数量样例的g语义改成kg时，业务回归测试失败；字段重命名失败；新增Controller未登记失败；feature越界依赖失败；私有辅助方法新增允许；授权兼容扩展加测试通过；随PR改白名单不能自我授权。语义变化无法完全自动识别，因此必须保留行为测试和EF对公共边界的判断。

仅在AGENTS或CODEOWNERS中写名字不会自动阻止合并。仓库具备权限时配置main必须PR、必需状态检查、更新到主线、禁强推，公共契约与治理文件由EF负责。若启用CODEOWNERS审核，必须在分支规则中同时要求它；普通业务不额外要求逐行人工审查。若套餐不能强制门禁，明确记录“提示型检查”，由EF维护合并权限或人工核验，不宣称已有强制保护。

受控文件的实际防线是目标分支已有CODEOWNERS + require code owner review + 必需状态检查。仅从base读取检查脚本不能防PR修改workflow本身。仓库能力不足时这套流程不是技术上的不可绕过保护，必须报告提示型状态。CI不持有写token或secret，不执行不受信任的pull_request_target代码。

比较基线分开记录：origin/main当前目标提交targetSha，feature提交headSha，二者merge-base为baseSha（分析PR自身差异）；授权只从targetSha读取，不能从head自读。CI fetch-depth:0；目标更新后重新运行并验证集成结果。本地显式传目标引用及head。main push任务用push before/after或明确审计基线，不能merge-base(main,main)导致空差异；首次初始化单独bootstrap。

## 15. 生成顺序、命令与完成定义

### 阶段A：准备候选基线

生成完整OpenAPI、fixtures、生成器配置、public-surface、ADR和模块构建；提供根任务 `validateContract generateClient generateServerApi checkContracts`。先让客户端及服务端生成物都能编译，此时仅候选快照，不冻结公共开发基线。

### 阶段B：建立可运行骨架

生成页面壳、导航、core组件、真实认证/家庭最小链路、数据库迁移、Mock/501边界。运行本机后端和模拟器，完成两账号家庭共享读取。把真实执行结果写BOOTSTRAP.md。

### 阶段C：建立并验证集成检查

生成并运行门禁自测、契约兼容测试、模块依赖检查、CI和PR模板。在当前真实仓库上验证必需状态是否生效；没有权限只报告缺口。A到C均在EF初始化分支，--bootstrap只允许本地显式初始化，生成候选public-surface和摘要；普通PR CI硬性禁用bootstrap，基线缺失直接失败。阶段C本地全部必需检查通过的commit为候选起点，EF推送后真实测试PR和保护验证通过才成为共同初始基线。以后普通PR对比第14.6规定的最新目标与merge-base，不永久对比这个初始commit。

### 阶段D：分工开发

AC/BD在既有模块中实现各自operation和页面，EF维护共享设施，开发任务注明允许文件范围、依赖API和验收。不重新生成整个项目，不覆盖公共文件，不另建数据模型。每个PR先集成最新主线再验证。

统一预检必须真实执行：

```text
根：validateContract generateClient generateServerApi checkContracts
根：assembleDebug testDebugUnitTest lint
server：test（含架构、权限、事务和真实PostgreSQL集成测试）
server：bootJar
scripts：contract_guard.py 的门禁自测及实际base/head比对
```

脚本从GitHub事件取得明确base/head SHA并校验；本地缺base时要求指定，不能默认跳过。目标分支更新后重新检查；旧commit的绿色结果不证明新提交通过。

必须覆盖的业务证据：

| 测试 | 可观察结果 |
| --- | --- |
| 契约生成 | 所有operation有双方接口；重复生成相同；未知字段和错误结构符合约定 |
| 权限 | 非成员查询被拒绝；移除后新请求失败；个人食记互不可读 |
| 做饭 | 两批次正确扣减；任一步失败不残留部分记录 |
| 防重 | 两个并发相同key只有一个事件；同key不同body拒绝；超时重试返回原事件 |
| 并发库存 | 两个不同请求争用不足库存时至多一个成功，库存不负 |
| 计划 | 预览不写库、锁定不变、任一版本冲突整批拒绝 |
| 历史 | 修改/归档菜谱后食记和烹饪快照不变 |
| 热量 | SERVING/GRAM/MANUAL/UNKNOWN计算和未知计数正确，跨月汇总正确 |
| 客户端 | 旋转不重复保存、退出后的旧回调不污染新账号、离线无假成功 |
| 未实现项 | 501和待接入状态可识别，release不装配Fake |

阶段验收分层：骨架A–C由EF负责契约生成、真实家庭链路、家庭查询权限、旧回调隔离、MockWebServer离线测试、501清单及门禁负例；库存事务/防重/过期由AC+EF在相关功能PR通过，排餐由BD/B，食记历史热量由BD/D，完整权限撤销由EF/E在removeMember实现时通过；所有业务行在最终集成阶段都必须通过。未实现项可以501，不得把相应尚未运行的业务测试列为通过。

ApiCalls旧回调/断网测试用MockWebServer和可控主线程Executor；ViewModel用core-testing的InstantTaskExecutorRule与SavedStateHandle验证旋转恢复相同key且不自动再提交；进程死亡恢复另测PendingWrite重新登录后相同key重试。模拟器导航/旋转为手工验收，实机后续再验证，均记录实际状态。

必需测试集跳过数必须为0，未配置测试库非零退出；缺环境记录UNVERIFIED，不能吞掉异常或用假数据库代替。骨架阶段不要将尚未实现的最终业务测试作为必需套件，使用明确阶段标签与未实现清单。Fake只在src/test，生产源扫描确认main/debug未引用；无需为了检查Fake强制release联网打包。

骨架完成≠App完成。只有全部实际业务验收完成才可称App可交付。README保留未实现operation列表，不能删除契约来使覆盖率“100%”。

## 16. 可直接交给 AI Coding Agent 的启动指令

```text
你是Civa的EF架构初始化Agent。读取本规格与现有Git状态，保留已有工作。
按第15节A→B→C生成并验证，第7–9节全部构成OpenAPI生成输入。
A–C在同一初始化分支，用显式bootstrap建立候选基线，C完成前不开放各组并行依赖。
每阶段写BOOTSTRAP.md并创建只含本任务变更的本地提交；任务可连续完成，换会话时读上阶段报告及commit。
实现register/login/logout/getMe/createHousehold/createInvitation/joinHousehold/getHousehold/listMembers真实链路，其他未实现接口统一501。
Fake只能在src/test；统一ApiCalls/AuthRepository/MediaClient，遵守契约、模块及缓存隔离规则。
实现IC机器元数据、draft/digest、可信基线授权、门禁及负例测试。
版本调整核对官方来源，记录URL、访问日期与实际构建结果；不能确认时记录未验证，不猜版本。
遇到影响数据含义、权限、兼容或公开签名的规格冲突/缺失，在OPEN_QUESTIONS.md记录节号、证据、选项和依赖；不自行裁决，只暂停依赖部分，继续无关工作。一般内部实现细节按规格合理选择并记录即可。
本任务仅授权本地工作和任务范围内的本地提交。C本地通过后输出远程操作清单，由EF推送、开测试PR和检查保护；只有当前会话另行明确授权才推送指定分支。无法读取远程规则时报告缺口。
远程CI未运行时不能声称已触发或保护已生效；文档中本地完成与远程验证分开记录。
交付生成文件、各阶段commit、真实命令/结果/跳过数、手工模拟器步骤、未实现API、OPEN_QUESTIONS及各组入口。
不能关闭门禁、静默跳过测试或伪造成功。
```

## 17. 取舍与参考来源

本方案借鉴成熟团队的契约优先、单一规范生成、模块依赖约束、变更记录及自动兼容检查，按六人课程项目缩减为单仓库、单后端和一个关系数据库。以下来源支持相应方法（本轮核对来源见末尾；既有版本仍须在阶段A重新验证），不代表某公司直接采用了本文的全部目录和规则；本文结构和业务取舍是针对Civa的架构建议。

- [Microsoft Web API设计：契约优先及OpenAPI](https://learn.microsoft.com/en-us/azure/architecture/best-practices/api-design)：用描述文件先统一接口，再实现服务与客户端。（访问日期：2026-10-29）
- [Microsoft API实施：权限、版本及客户端契约](https://learn.microsoft.com/en-us/azure/architecture/best-practices/api-implementation)：接口文档包含错误、重试和权限，生成客户端仍需验证行为。（访问日期：2026-10-29）
- [OpenAPI Generator Java生成器](https://openapi-generator.tech/docs/generators/java/)及[Spring生成器](https://openapi-generator.tech/docs/generators/spring/)：支持对应客户端/服务端生成；具体选项须对锁定版本验证。（访问日期：2026-10-29）
- [ArchUnit架构测试](https://www.archunit.org/userguide/html/000_Index.html)：以字节码检查依赖、分层和循环。（访问日期：2026-10-29）
- [Android模块化](https://developer.android.com/topic/modularization)：模块粒度需要平衡隔离收益与构建负担。（访问日期：2026-10-29）
- [AGP 8.13兼容要求](https://developer.android.com/build/releases/agp-8-13-0-release-notes)及[Spring Boot 3.5系统要求](https://docs.spring.io/spring-boot/3.5/system-requirements.html)：用于初始JDK/Gradle基线核对。（访问日期：2026-10-29）
- [Room发布说明](https://developer.android.com/jetpack/androidx/releases/room)：核对缓存依赖与兼容变更。（访问日期：2026-10-29）
- [Android模拟器网络](https://developer.android.com/studio/run/emulator-networking-address)：10.0.2.2指向模拟器所在开发电脑。（访问日期：2026-10-29）
- [GitHub受保护分支](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches)及[CODEOWNERS](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-code-owners)：区分文件约定、审核请求与实际强制合并规则。（访问日期：2026-10-29）

本轮复核（访问日期：2026-10-29）：

- [OpenAPI 3.0.3 required与nullable](https://spec.openapis.org/oas/v3.0.3)：必填与可空是不同约束。（访问日期：2026-10-29）
- [Arch Core 2.2.0](https://developer.android.com/jetpack/androidx/releases/arch-core)：core-testing版本。（访问日期：2026-10-29）
- [Spring Boot外部配置](https://docs.spring.io/spring-boot/how-to/properties-and-configuration.html)：显式环境变量占位符。（访问日期：2026-10-29）
- [GitHub保护分支](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches)：owner审核及过期批准。（访问日期：2026-10-29）

上列既有AGP/Spring Boot/OpenAPI Generator等来源于2026-10-29架构研究中查阅；访问日期只表示资料查阅，不表示版本组合已构建通过。阶段A必须补各依赖版本的实际解析及生成验证记录。
