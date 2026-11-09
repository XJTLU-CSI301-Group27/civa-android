# 初始化记录

状态：UNVERIFIED；阶段 A 尚未完成，不是可供各组依赖的基线。

2026-10-29 本机检查：`java -version` 返回 Java 21.0.4；`python --version` 返回 Python 3.10.9；`gradle --version` 未找到命令。未发现 ANDROID_HOME 或 ANDROID_SDK_ROOT 环境变量（不代表 SDK 一定未安装）。当前目录不是 Git 仓库。上述检查未验证构建环境。

已开始：项目目录、协作约束和生成器候选配置。生成器选项尚未用 7.17.0 验证。

补充：已添加 Android 七个模块的候选 Gradle 配置和独立 server Gradle 配置，尚未解析依赖或构建。`git ls-remote https://github.com/XJTLU-CSI301-Group27/civa-android.git` 因无法获得 GitHub 凭据失败，远端内容与分支未知；本地目录尚未关联远端。重新核对环境仍找到 Java 21.0.4、Python 3.10.9，未找到全局 Gradle。

用户在旁边目录克隆仓库后，读取到 `main` 的 69e91a03a422c87208f786c94826755456290e4e。远端已有团队规则和预检文件，没有 Android 工程；相关规则已复制到本项目 docs 中供核对。契约审批衔接问题记录在 OPEN_QUESTIONS.md。

随后将克隆的 Git 元数据复制到 civa，创建 `bootstrap/architecture` 分支并恢复远端已有团队文件。当前基于本地 origin/main 的 69e91a0；本环境仍无法访问凭据，因此未重新获取远端最新状态。

继续检查发现 `F:\Android_SDK` 含 API 37 平台和 build-tools 36.0.0，`F:\Android Studio\jbr` 是 Java 25.0.3；尚未找到规格所需 API 36 平台和 JDK 17。Docker Compose 为 2.40.3。Python 当前为 3.10.9，已安装的 PyYAML 为 6.0.3，与规格固定版本不符。固定 Gradle 8.13 发行包 SHA-256 核对来源：<https://gradle.org/release-checksums/>；尚未取得或验证 Wrapper。

已增加数据库 Compose、服务端 application.yml、环境变量样例及固定 Python 依赖；`docker compose -f infra/compose.yaml config --quiet` 使用临时占位变量返回 0，YAML 语法检查通过（使用本机 PyYAML 6.0.3，非完整预检）。尚未启动数据库或验证迁移。`QuantityInputs` 使用 `javac --release 17` 编译，并用 Java 运行 `QuantityInputsCheck`，退出码 0；这是独立小检查，不代表 Android Gradle 测试通过。

新增 `contracts/openapi.draft.json`：41 个操作及 64 个候选 schema。Python 临时检查操作 ID 唯一性、schema 引用、属性正则、部分 nullable 必填语义、501 和 10 个幂等标记，退出码 0；这不是 OpenAPI 验证器或契约合规验收。候选错误集合、字段约束、分页和实例仍需逐项核对，尤其 nullable 引用及生成器兼容性尚未验证；请求/响应 fixtures 和契约生成均未完成。草稿不是正式 `openapi.json`，不能用于组间开发或生成正式基线。

两套 Gradle 8.13 Wrapper 已加入，JAR SHA-256 与 Gradle 官方 8.13 校验值一致，发行包下载校验后，根及 server 的 `gradlew.bat --version` 均返回 0（运行 JVM 21，尚非基线 JDK 17）。临时 Gradle 工程运行 OpenAPI Generator 7.17.0 的 `validateCandidate` 返回 0，无结构验证问题；此结果不证明业务语义、示例或生成代码正确。根 `gradlew.bat tasks` 首次因 Windows 非 ASCII 路径被 AGP 拒绝；已按 AGP 提示启用 `android.overridePathCheck`，重试结果待记录。两份 Unix `gradlew` 已在 Git 暂存区设置可执行位；尚无提交。

根 `gradlew.bat tasks` 在启用路径开关后返回 0。由于 `F:\Android_SDK` 对本进程不可写，第一次 `assembleDebug` 自动安装 SDK 失败；在忽略的 `build/android-sdk` 内安装 API 36、build-tools 35 后，以 JDK 21 运行 `assembleDebug` 返回 0，192 个任务执行；当前 APK 无启动页面，该结果仅验证工程空壳。之后从 [Microsoft 官方 JDK 17 下载页面](https://learn.microsoft.com/en-us/java/openjdk/download) 获取 JDK 17.0.21.0.2，校验 SHA-256 后放入忽略的 build 目录；其 Android 检查结果见下。

OpenAPI Generator 7.17.0 对候选草稿的客户端和服务端生成任务返回 0，生成客户端 API 方法使用 Retrofit `Call<T>`。最初日期带 `format: date` 导致双方生成 `LocalDate`，且服务端对其生成 `@Pattern`；已从草稿日期 schema 移除该 format，重新验证和生成返回 0，抽查食记请求日期已是 String。生成日志中 RxJava 选项存在警告且生成器默认产生额外构建文件、Gson 配置及内联错误模型，正式生成任务仍须限定文件并核验依赖、单一 Gson 和完整方法集，尚未编译生成代码。

实际检查发现默认服务端生成的响应模型 `Recipe.imageId`、`Recipe.totalKcal`、`User.householdId` 尽管在草稿中是 required 且 nullable，仍带 `@NotNull`，不符合协议。现已增加模板修正和候选回归测试，但正式生成尚未接入模板，不能把草稿冻结。JDK 17 下首次 `assembleDebug testDebugUnitTest lint` 在 `:core-ui:testDebugUnitTest` 因 Gradle Test Executor 找不到 `GradleWorkerMain` 失败，lint 未运行；后续排查结果见下。

把 Gradle 缓存放在临时英文路径后 worker 可以启动，但测试类仍因项目中文路径无法加载；通过临时英文目录 junction 指向同一工作区，并使用英文路径的 Gradle 缓存后，JDK 17 的 `:core-ui:testDebugUnitTest` 返回 0，JUnit XML 显示 1 项测试、0 失败、0 跳过。这只验证数量换算，未证明其他功能。完整 Android 检查及独立服务端任务发现结果另行记录；临时 SDK 和 JDK 均在忽略的 build 目录，不属于项目交付依赖。

经同一 junction 和缓存，以 JDK 17.0.21.0.2、Android SDK Platform 36 执行 `gradlew.bat assembleDebug testDebugUnitTest lint --no-daemon`，退出码 0；JUnit XML 合计 1 项、失败 0、跳过 0。当前是没有启动页面的空壳及一个数量换算测试，不代表阶段 A 完成、手工导航通过或业务可用。构建日志为 `build/android-alias-verification.log`；junction 及缓存属于本机临时环境，其他电脑仍需自行配置 JDK/SDK。已补充固定 Route 和 NavHost 接口，尚未接入页面。

独立 server 目录使用同一 JDK 17 和临时英文路径执行 `gradlew.bat tasks --no-daemon`，退出码 0，表明当前 Gradle 配置可以解析；服务端仍没有可运行的应用入口、生成接口或数据库测试，`test`、`bootJar` 和真实链路未验证。

根任务 `validateCandidateContract` 使用 toolchain.properties 指定的 OpenAPI Generator 7.17.0 验证草稿并返回 0；`validateContract` 因正式 `contracts/openapi.json` 尚不存在按预期返回非零。服务端候选生成使用 `contracts/templates/server/beanValidation.mustache`，仅对 nullable 字段去掉 `@NotNull`，保留非空字段约束；临时生成工程中 User 的 null 响应序列化和 Bean Validation 测试通过。该修正需要随正式生成配置使用，不能仅依赖手工改生成代码。

服务端 `gradlew.bat testCandidateContract --no-daemon` 以 JDK 17、临时英文路径运行，退出码 0；候选生成的服务端 API 和模型编译成功，JUnit XML 显示 2 项测试、失败 0、跳过 0。测试覆盖 User 可空字段输出、非空字段拒绝 null、菜谱请求可空字段缺失/null/合法值与未知字段拒绝。该检查仅针对草稿，不替代生产端点或完整契约、授权和数据库测试；日志 `build/candidate-regression.log`。

加入生成前清理候选输出后，再以 `testCandidateContract --rerun-tasks --no-daemon` 重跑，退出码 0，2 项测试、0 失败、0 跳过。

客户端已添加 MainActivity、Fragment 占位页、五个主入口及 w600dp 侧栏布局；所有业务页面目前均显示“功能待接入”，没有伪造数据。第一次导航构建中 `Theme.Civa` 与 core-ui 资源前缀 lint 冲突，见 OPEN_QUESTIONS；使用临时候选名 `CivaTheme.Civa` 后，以 JDK 17、临时英文路径重跑 `assembleDebug testDebugUnitTest lint --no-daemon`，退出码 0，JUnit 1 项、失败 0、跳过 0。`aapt dump badging` 识别出可启动的 `edu.xjtlu.civa.MainActivity`，但这并非运行验证。`adb devices` 无设备，`emulator -list-avds` 无 AVD；窄/宽屏、旋转、返回和截图均未手工验证。日志：`build/android-navigation-rerun.log`。

由于当前尚无会话实现，启动时固定进入登录占位页并隐藏受保护导航，不能登录或进入业务功能；真实认证接入后仍须实现会话判断和切换。修改后执行 `validateCandidateContract assembleDebug testDebugUnitTest lint --no-daemon`，退出码 0；仅有的 Android 单测 1 项、0 失败、0 跳过。无设备或模拟器，启动行为仍未实测。日志：`build/android-login-check.log`。

候选契约补充注册密码最少 10 字符、用户名先规范化再校验的说明，以及 503 `Retry-After`；新增注册、用户响应和错误样例。最终 `validateCandidateContract` 和服务端 `testCandidateContract` 均返回 0，后者 3 项测试、0 失败、0 跳过。用户名规范化及密码 UTF-8 72 字节上限需要真实服务端校验，不能由当前 schema/DTO 测试替代；候选契约其他规则、样例及错误集合仍需完整核对。日志：`build/candidate-validation.log`、`build/candidate-fixtures.log`。

新增服务端包内 Credentials 校验：用户名用 Locale.ROOT 小写后匹配规定格式；注册密码至少 10 个 Unicode 码点、UTF-8 最多 72 字节，拒绝无效代理字符且不 trim。JDK 17 下当时执行 `server/gradlew.bat test --tests edu.xjtlu.civa.server.auth.CredentialsTest --no-daemon` 返回 0，1 项测试、0 失败、0 跳过；日志 `build/credentials-test.log`。该代码尚未连接注册/登录接口，不能视为认证链路完成。`docker info` 返回 1（Docker Desktop Linux 引擎未运行）。该次执行发生在测试库门禁加入之前，不能视为完整服务端测试。

随后为 server `test` 加入前置 `verifyTestDatabase`：要求显式 PostgreSQL JDBC URL 和凭据，先检查 URL 中的库名以 `_test` 结尾，再连接查询 `current_database()` 并要求与 URL 一致，成功后才启动测试。独立 `unitTest` 仅运行明确列出的无数据库单测，不能替代 `test`。JDK 17 下 `unitTest` 返回 0，2 项测试、0 失败、0 跳过；缺少 DB_URL 时 `test` 按预期在验证阶段返回 1；指向不可连接的测试库时 `verifyTestDatabase` 也返回 1。日志分别为 `build/server-unit-test.log`、`build/test-db-missing.log`、`build/test-db-unreachable.log`。Docker 服务启动尝试被操作系统拒绝，尚未验证连接真实 PostgreSQL 的成功路径、实际库名不匹配负例或迁移/清理安全；完整数据库门禁及集成测试仍未验收。

当前前置检查只验证 DB_URL 指向的连接；Spring 的外部配置或测试代码仍可能覆盖实际数据源。接入 Spring/Flyway 前必须在实际 DataSource 初始化和迁移前复核同一连接，防止验证与使用的数据库不一致。现有检查不能作为允许迁移或清理的独立安全证明。

补充测试专用 Spring Boot FlywayMigrationStrategy：在调用自动迁移前，从 Flyway 实际 DataSource 获取连接并检查当前库名。`server/gradlew.bat unitTest --no-daemon` 返回 0，5 项测试、0 失败、0 跳过；其中使用 Mockito 检查错误库名不调用 migrate、匹配时检查完成后才调用 migrate，并验证自动配置可被发现。日志 `build/flyway-guard-unit.log`。这是隔离单测，不是数据库集成验收；尚未用真实 PostgreSQL 验证自动配置、Flyway 实际连接及迁移，也未覆盖绕过 Spring Boot 策略的手动迁移/清理。Flyway 检查连接和真正迁移连接可能是两个连接，因此动态切换数据源场景仍需进一步约束，不能用本检查批准迁移或清理。

为验证真实连接，从 EDB 获取 PostgreSQL 16.15-5 Windows 二进制归档。首次错误地使用 17.11 的摘要比较导致检查失败，未执行文件；之后从 EDB 对应 `.sha256` 和 [EDB 校验记录](https://github.com/EnterpriseDB/edb-installers/issues/731)核对 16.15 摘要 `43bb45f173a6f08cf1d29a97a6d8deb119e8e8093a24c00d2d1001a0ccaa8281`，与下载文件匹配。仅在忽略的 `build/` 内初始化独立集群和 `civa_test`，监听 127.0.0.1:55432；`pg_ctl start` 因沙箱令牌限制失败，直接启动 postgres 成功。以 JDK 17 运行 server `test --no-daemon` 返回 0，6 项测试、0 失败、0 跳过，其中 1 项通过真实 PostgreSQL 验证当前库匹配及预期库名不匹配；其余含隔离单测。没有运行项目迁移或真实账号家庭链路。测试后服务已正常停止；日志 `build/server-postgres-test.log`。测试数据与随机密码文件留在被 Git 忽略的 build 目录，未加入仓库。

会话代次变更现在同步通知 ApiCalls 取消对应代次的在途请求；请求登记与代次变更互斥，防止取消发生后才登记旧请求。MockWebServer 测试验证登出取消旧请求且新账号请求可完成。JDK 17 下 `assembleDebug testDebugUnitTest lint --no-daemon` 返回 0，Android 单测 7 项、0 失败、0 跳过；日志 `build/android-session-cancel.log`。真实 AuthRepository、缓存清理、认证拦截器以及请求与令牌快照的绑定仍未实现。

新增 Flyway 初始迁移和追加修正迁移。在本机隔离 `civa_test` 的 PostgreSQL 16.15 上，server `test --no-daemon` 返回 0，共 7 项测试、0 失败、0 跳过。实际查询确认 19 张业务表及两条成功迁移记录；测试重复执行 Flyway 验证，事务回滚检查活动家庭唯一、库存非负和幂等占位约束。V1 应用后发现菜谱食材数量被错误限制为大于 0，已追加 `V20261029143735__EF_allow_zero_recipe_quantity.sql` 并验证零值；没有改写已应用 V1。测试库已停止。迁移还未经过全部业务、并发和权限测试；标准食材种子、真实服务实现及家庭 OWNER 数量确认仍缺失，后者见 OPEN_QUESTIONS。不得以该局部结果宣称阶段验收通过；日志 `build/baseline-migration-test.log`。

新增包内 AuthService：注册时在事务中创建用户与偏好、BCrypt 保存密码哈希；登录生成 256 位随机 token，数据库保存 SHA-256 摘要及 24 小时过期时间；支持验证、重复登出、统一登录失败和可配置失败窗口。当前限速是进程内全局锁和共享窗口，进程重启会清空，需在实际装配时明确单实例、配置和容量边界。使用隔离 PostgreSQL 16.15 执行 server `test --no-daemon` 返回 0，8 项测试、0 失败、0 跳过；账号测试覆盖重复用户名、Unicode 名称边界、哈希、过期、撤销、限速和窗口恢复，事务回滚后 users/sessions/preferences 数量均为 0。测试中的 BCrypt 工作因子为 4，仅用于测试。生产 BCrypt 配置、Spring Security 上下文、HTTP 路由、错误映射及真实客户端均未接入，不能称认证链路完成。日志 `build/auth-service-test.log`，测试后数据库已停止。

新增包内 InvitationCodes：生成 128 位 Base32 大写无填充邀请码，对输入去空白、转大写并验证 26 字符及规范重编码，哈希规范字符串。新增 HouseholdReadService：家庭和成员列表查询中验证活动成员，区分非成员 NOT_FOUND 与历史成员 HOUSEHOLD_ACCESS_REVOKED，并提供稳定分页。隔离 PostgreSQL 16.15 下 `server/gradlew.bat test --no-daemon` 返回 0，10 项测试、0 失败、0 跳过；覆盖邀请码固定向量、分页、非成员和移除后的访问拒绝。测试事务回滚后 users/households/memberships 均为 0，数据库已停止。日志 `build/household-read-test.log`。这只是包内能力；家庭创建、邀请创建/消费、幂等和 HTTP/Security 装配尚未完成。

新增候选共享 IdempotencyExecutor：占位、业务写入和成功响应在同一事务；并发同键冲突后在新事务读取已提交结果，不同摘要拒绝，重放通过调用方提供的授权检查；业务异常或占位被移除时回滚。真实 PostgreSQL 并发测试验证同键仅执行一次、重放授权、不同摘要冲突及失败后重试。测试在新建随机 test schema 中进行，完成后只 TRUNCATE 该 schema 的测试表，保留空 schema 与迁移历史，不删除数据库。完整 server `test --no-daemon` 返回 0，11 项、0 失败、0 跳过；public schema 的 users/households/memberships 仍为 0，数据库已停止。日志 `build/household-idempotency-test.log`。当时调用方的 schema 感知规范化摘要、初次写入与重放授权、HTTP 错误映射和实际做饭事务尚未实现，不能把该局部测试等同于完整防重验收；摘要进度见下。

新增候选 JSON 请求规范化与摘要，格式记录在 ADR-002。按候选 OpenAPI schema 补全可空字段、规范化数量、排序对象键并保持数组顺序；校验重复键、未知字段、非法数量及 Unicode。固定摘要向量由独立 Python 计算并与 Java 测试比对；真实 PostgreSQL 并发测试将等价 JSON 重试接入幂等执行器，验证一次执行，以及数量不同的冲突。JDK 17 下 server `test --no-daemon` 返回 0，12 项、0 失败、0 跳过；日志 `build/canonical-idempotency-test.log`。测试后数据库已停止；测试 schema 的业务表已清空，schema 和迁移历史保留。该实现只覆盖 JSON，multipart、完整业务校验、实际路由和调用方授权尚未接入；正式契约和摘要格式仍未冻结。

新增候选家庭写入服务：通过幂等事务创建家庭和 OWNER 成员；使用已存在、未过期且未使用的邀请码加入时锁定家庭和邀请，原子创建 MEMBER 并消费邀请；重放前重新核验活动成员。真实 PostgreSQL 测试覆盖创建与重放、重复家庭、加入与重放、邀请码已用、移除后的重放拒绝及同一用户并发加入两个家庭时只有一次成功且仅消费一个邀请码。server `test --no-daemon` 返回 0，12 项测试、0 失败、0 跳过；日志 `build/household-write-test.log`。邀请码由测试直接写入哈希，生产邀请码创建尚未实现；原因及需确认的协议见 OPEN_QUESTIONS。HTTP、真实认证上下文和客户端仍未接入；测试数据库已停止。

新增 `contracts/public-surface.draft.json`，记录目前已实现的候选跨模块签名、负责人、消费者、线程语义和 Android 模块依赖图。已检查 JSON 语法、类型名唯一及登记字段完整；它不是编译字节码自动比对，也未覆盖尚未实现的公共入口，不能作为授权或正式门禁基线。补充 NavHost、ApiCallback 和服务端相关可空注解；断网和畸形错误响应已加入 MockWebServer 测试，最终构建结果见本条后续记录。

JDK 17 下重跑 Android `assembleDebug testDebugUnitTest lint --no-daemon`，返回 0，共 9 项测试、0 失败、0 跳过；日志 `build/android-public-surface.log`。服务端独立 `unitTest --no-daemon` 返回 0，7 项、0 失败、0 跳过，日志 `build/server-public-surface.log`；此处没有运行依赖真实 PostgreSQL 的 server `test`。断网测试只验证错误回调且不伪报成功，缓存保留与重试交互仍未实现。

新增 Spring Boot 启动入口及测试中的 Bearer 安全基础：默认 formLogin/httpBasic/logout 和 cookie 会话关闭；有效 token 通过数据库哈希与有效期查询后建立仅含用户 ID 的认证上下文，未认证响应使用统一 JSON 和请求 traceId。生产使用默认强度的 BCrypt，失败窗口通过配置注入；排除 Spring Boot 默认用户自动配置，避免生成并记录额外密码。真实 PostgreSQL 下 `server/gradlew.bat test bootJar --no-daemon` 返回 0，13 项测试、0 失败、0 跳过；测试专用路由验证无令牌、无效令牌、双账号、撤销与过期行为，测试 Flyway 安全配置被发现，users/sessions/preferences 回滚后均为 0。检查打包 JAR 确认测试路由和测试 Flyway 配置未包含，且本次测试日志未生成默认安全密码。数据库已停止；日志 `build/security-foundation-test.log`。生产业务 HTTP 控制器、统一业务错误映射、权限路由与完整链路尚未实现，`bootJar` 成功不代表可用 API 或阶段验收通过。

新增 `scripts/contract_diff.py`，比较 OpenAPI 文件中的操作、组件、传递引用及认证定义；重复键、无效引用和输出覆盖输入会失败。`python -m unittest discover -s scripts -p test_contract_diff.py -v` 在本机 Python 3.10.9 下运行 5 项、失败 0；同一候选文件自比返回 NO_CHANGES、授权 UNVERIFIED，报告 `build/contract-diff-candidate.json`。该工具不获取 Git SHA、不判断目标分支 IC 授权，也不覆盖公共面、路由、依赖、CI 和语义行为测试；不是完整 contract_guard/preflight。规格要求 Python 3.11+ 和固定 PyYAML，当前解释器不满足正式预检环境。

新增共享 `JsonbCodec`，家庭响应与重放读取使用同一个注入的 Jackson 配置；空输入、重复键和尾随 JSON 被拒绝，错误不回显输入。以 PostgreSQL 16.15 和 JDK 17 执行 server `test bootJar --no-daemon`，返回 0，14 项测试、0 失败、0 跳过；包括 codec 单测及已有真实数据库测试。日志 `build/jsonb-codec-test.log`，测试数据库已停止。公开接口草稿已同步候选构造签名；正式契约、生产生成任务和未批准的邀请码重放方案仍未完成。

core-data 新增仅驻留内存的 SessionStore：公开用户/家庭 ID、登录状态和清理；包内通过原子快照、按代次安装和家庭更新、原子清理及捕获旧 token 支持后续认证网络层。尚未接入 AuthRepository、请求取消、缓存清理或主线程回调，不能视为旧回调隔离验收完成。首次 Android 编译因系统 `android.annotation.Nullable` 不可公开使用失败；改用固定 AndroidX annotation 1.9.1 后，以 JDK 17 运行 `assembleDebug testDebugUnitTest lint --no-daemon` 返回 0，2 项测试、0 失败、0 跳过，日志 `build/android-session-rerun.log`。

客户端生成器默认对 Gson 候选模型引入 Jackson JsonNullable；在客户端候选配置中设置 `openApiNullable=false`，并仅生成 API、模型及 CollectionFormats/StringUtil 支持文件。JDK 17 下 `:api-client:testCandidateClient --no-daemon` 返回 0，候选客户端 95 个 Java 文件编译成功，检查未发现 JsonNullable 或生成的 JSON 配置类；1 项测试、0 失败、0 跳过，覆盖 Retrofit Call 返回类型、日期 String、Gson null 序列化和忽略未知响应字段。测试使用临时 Gson 实例，生产环境的唯一 Gson、Retrofit/认证装配和客户端生成代码接入均尚未实现。日志 `build/client-candidate-test.log`。

新增普通请求的 ApiCalls/ApiCallback 和共享 ApiError：投递到主线程执行器时重新检查会话代次，处理 204、结构化错误及非认证路径 401，并在丢弃过期流式响应时关闭响应体。MockWebServer 与可控执行器测试 204、账号切换后的旧回调、认证与非认证 401 和流式响应关闭。JDK 17 下 `assembleDebug testDebugUnitTest lint --no-daemon` 返回 0；Android 单测合计 6 项、0 失败、0 跳过（包含 4 项 ApiCalls 测试）。日志 `build/android-api-calls-rerun.log`。当前尚无 Retrofit/OkHttp 生产装配、AuthRepository、登录/退出时集中取消与缓存清理、缓存请求、401 导航或 MockWebServer 断网测试；所加 API 尚未接入页面或完成公共面登记。异常响应的本地错误码待确认，见 OPEN_QUESTIONS。

请求规范化现在对候选契约中的日期和 UTC 毫秒时间执行严格解析，拒绝不存在的日期和非法时分秒；候选服务端契约测试递归编译 JSON 解析后的 schema 正则。JDK 17 下 `server/gradlew.bat unitTest testCandidateContract --no-daemon` 返回 0，覆盖闰日、非法日期和时刻。此校验目前仅在调用 `CanonicalRequestDigest` 时生效，尚未接入生产 HTTP 路由，不等同于全部请求已受保护。

新增候选接口索引及生成/检查脚本：`python scripts/contract_index.py contracts/openapi.draft.json docs/generated/CONTRACT_INDEX.draft.md` 可重新生成，末尾加 `--check` 可检测过期；根任务 `checkCandidateIndex` 运行同一检查并已通过。索引覆盖候选草稿的 41 个 operation、负责人、请求体和成功响应，含数组及媒体类型。脚本测试在 Python 3.10.9 下共 6 项通过，包括索引被改动时检查失败；这不满足正式预检要求的 Python 3.11+，也不代表候选契约已经批准或 CI 已接入。

阶段 A 按规格使用唯一 `contracts/openapi.json` 作为候选输入（仍未批准），替代原 `openapi.draft.json`；客户端和服务端均将该文件生成的接口加入 main sourceSet 并在编译前生成。根 `validateContract generateClient generateServerApi :api-client:testCandidateClient assembleDebug testDebugUnitTest lint checkCandidateIndex` 的 Gradle 输出为 BUILD SUCCESSFUL；服务端使用隔离 PostgreSQL 运行 `test testCandidateContract bootJar --no-daemon` 返回 0，真实服务端测试 14 项、候选契约测试 4 项，失败和跳过均为 0。检查 JAR 中存在生成的 AuthApi 与 User，未包含测试控制器或测试 Flyway 配置；数据库已停止。日志 `build/candidate-main-generation.log` 和 `build/candidate-server-main.log`。这证明生成和编译，不代表 HTTP 业务接口已实现、契约已完成审阅或阶段 A 已验收；`checkContracts` 和正式门禁仍未实现。现在索引重生成命令使用 `contracts/openapi.json`。

新增 `ApiProvider` 与真实 Retrofit/OkHttp 实现：对 feature 暴露六组生成接口，认证和媒体接口限于 core-data；单一 Gson 使用 `serializeNulls`，连接/读写超时为 10/20/20 秒并禁用自动连接失败重试。API 方法创建调用时绑定当前会话，`ApiCalls` 拒绝已切换账号的旧请求；旧调用在账号变更后也不能被克隆重试。MockWebServer 测试覆盖延后提交的旧调用、当前账号请求、正常异步回调和登录请求不携带旧 token。JDK 17 下根任务 `:core-data:testDebugUnitTest assembleDebug lint --no-daemon` 返回 0，core-data 单测 9 项、0 失败、0 跳过；日志 `build/provider-final-test.log`。此组件尚未由 AppContainer 装配，AuthRepository、登出旧 token 撤销、缓存清理以及禁止 feature 直接执行网络调用的架构门禁仍未实现；不能视为完整认证链路或完整账号隔离验收。

服务端通过生成的 AuthApi/ProfileApi 接口实现 register、login、logout、getMe；偏好接口暂以 JSON `CONTRACT_NOT_IMPLEMENTED` 返回 501。异常处理统一映射验证错误、重复用户名、登录失败与限速，并在 429 附加 Retry-After。字符串长度生成模板改为按 Unicode 码点校验。隔离 PostgreSQL 下 `test testCandidateContract bootJar --no-daemon` 成功，覆盖真实控制器注册、两账号查询、重复登出、未知字段、120 个表情字符及生成模型边界；之后 `test --tests edu.xjtlu.civa.server.auth.SecurityFoundationIntegrationTest --no-daemon` 复测成功，验证用户不存在与错误密码返回相同代码，以及限速 429/Retry-After。日志 `build/auth-http-test.log`、`build/auth-http-rate.log`；测试数据库已停止。家庭 HTTP 接口、客户端认证、其余未实现路由的统一 501、全链路和安全验收仍未完成；候选契约未获批准。

新增 Android `AuthRepository`：注册后返回用户但不安装会话；登录在主线程验证响应和原代次后安装内存令牌，再以新代次返回用户；登出立即清本地会话并独立发送已绑定旧令牌的撤销请求，若期间切换账号则丢弃旧回调。MockWebServer 验证注册、异常会话响应、过期登录回调、登出所用令牌及账号切换；`:core-data:testDebugUnitTest assembleDebug lint --no-daemon` 返回 0，core-data 单测 10 项、失败和跳过均为 0，日志 `build/auth-repository-final.log`。实际登录页面、AppContainer、账号缓存及登出后的页面生命周期处理尚未接入；尚未完成真实端到端认证验收。

家庭服务现接入生成的 HouseholdApi，提供创建、使用已存在邀请码加入、家庭查询及成员分页；创建与加入使用事务、规范化摘要和重放权限复核。邀请码创建与移除成员暂返回 501。真实 PostgreSQL 下 `test bootJar --no-daemon` 与针对 `HouseholdHttpIntegrationTest` 的复测均成功；验证三账号登录、同 key 等价请求重放及冲突、重复 JSON 键和缺失 header、成员共享读取、外人 404、移除后 403 及重放拒绝。邀请码由测试直接插入哈希，测试只清理其随机账号及经归属核实的家庭；数据库已停止。日志 `build/household-http-test.log`、`build/household-http-focused.log`。客户端家庭页面、生产邀请码创建、完整统一 501、锁超时映射和端到端验收仍未完成。

Android 新增应用容器和真实登录/注册页面；未登录隐藏主导航，普通配置变化由 ViewModel 保留输入，进程重建时不恢复受保护页面，离开登录页会取消尚未完成的登录。debug 默认访问 `http://10.0.2.2:8080/`，仅 debug 允许明文，release 要求显式 HTTPS 地址。`:core-data:testDebugUnitTest assembleDebug lint --no-daemon` 成功，core-data 测试 10 项、失败和跳过均为 0；APK 为 `app/build/outputs/apk/debug/app-debug.apk`。缺地址及 HTTP 地址的 release 构建配置任务均按预期失败，显式 HTTPS 地址下 `:app:processReleaseMainManifest` 成功；检查合并清单确认 release 禁用明文且 debug 使用独立网络安全配置。日志 `build/login-ui-build.log`、`build/release-url-missing.log`、`build/release-url-http.log`、`build/release-url-valid.log`。`adb devices` 无设备，项目 SDK 未安装模拟器，也未发现本机 AVD；未进行页面显示、旋转、无障碍或真实端到端运行。家庭页面、用户缓存、前台 401 立即跳转及完整页面生命周期取消仍未完成。

未实现的 Recipe、Inventory、Planning、Records 和 Media 方法现由生成接口的控制器明确返回 501；README 列出 33 个未实现 operation。新增测试将候选契约路径和实际 Spring 路由逐项比较，除 `/error` 外不允许额外或缺失路由；认证后抽查食材和图片接口返回结构化 501，图片请求 Accept 为 PNG 时仍返回 JSON 错误。隔离 PostgreSQL 下 `test testCandidateContract bootJar --no-daemon` 成功：服务端测试 16 项、候选模型测试 4 项，均 0 失败、0 跳过；日志 `build/server-501-test.log`，数据库已停止。此测试验证路由覆盖并抽查 501，不等于逐一验证所有占位方法的完整行为，也不代表这些业务已实现；正式契约门禁和实际客户端页面仍未完成。

非认证请求返回 401 时，ApiCalls 现在在主线程清除当前会话并通知应用容器；活动页面可返回登录页，状态已保存时在恢复后处理。MockWebServer 验证认证接口 401 不触发全局退出、当前账号 401 通知一次，以及旧账号 401 不清除或跳转新账号。`:core-data:testDebugUnitTest assembleDebug lint --no-daemon` 成功，core-data 测试 11 项、失败和跳过均为 0；日志 `build/unauthorized-navigation.log`。尚无设备或模拟器验证实际页面切换；用户缓存和家庭权限撤销清理仍未实现。

新增 Room 2.8.4 的私有 `cache_entries` 与 `pending_writes` 表、按用户及家庭限定的 DAO 和版本 1 导出 schema；待确认写入不受缓存过期清理影响，同 key 插入冲突不覆盖原意图。基于 Room 导出的建表 SQL 和 DAO 实际查询，Python SQLite 测试验证账号/家庭隔离、缓存过期和待确认记录保留。`:core-data:assembleDebugAndroidTest :core-data:testDebugUnitTest assembleDebug lint --no-daemon` 重试成功，设备测试 APK 已编译；首次因 Guava 下载 TLS 握手失败，构建期依赖处理记录于 ADR-001，日志 `build/room-foundation-build.log`、`build/room-foundation-retry.log`。没有连接设备，Room 设备测试未执行；数据库目前尚未装配到 AppContainer 或 ApiCalls，也未实现 `enqueueCached`、待确认写入恢复、敏感内容校验或登出清理，不能称离线缓存/恢复功能已完成。

后续已将只读 Room 缓存装配到 AppContainer，并实现 `enqueueCached`：仅允许 GET 和非敏感生成模型，key 包含用户、家庭、完整 URL 与调用方 key 的摘要；先返回 stale 快照，再返回网络结果或错误。账户切换时排队清除旧用户缓存，家庭访问撤销时仅清该家庭缓存、清除家庭状态并标记相关待确认记录人工核查；后台写入重新检查会话。MockWebServer 和测试用 DAO 覆盖断网回退、错误响应不覆盖快照、切换账户后旧写入失效以及家庭撤销后的隔离；`:core-data:testDebugUnitTest :core-data:assembleDebugAndroidTest assembleDebug lint --no-daemon` 返回 0，日志 `build/cached-calls-test.log`。当前只校验生成模型的非空字段和嵌套结构，尚未完整校验 OpenAPI 的长度、范围、日期及 nullable 字段存在性；Room 设备测试、页面接入、失败时缓存清理的可靠恢复、待确认写入创建与恢复仍未完成，不能称完整离线或安全验收通过。

缓存请求的 401 现在在清除会话后仍向当前调用方报告错误；家庭撤销后的错误回调检查账号和更新后的会话代次，避免旧账号回调污染新账号。MockWebServer 复测缓存路径的 401、家庭 403、异常 DTO、断网及切换账号；`:core-data:testDebugUnitTest :core-data:assembleDebugAndroidTest assembleDebug lint --no-daemon` 返回 0，core-data 单测 14 项、失败和跳过均为 0，日志 `build/cached-calls-resumed.log`。数据库设备测试依然仅编译、未在设备上运行；上条列出的 DTO 完整校验和清理失败恢复限制仍存在。

缓存 DTO 校验改为读取打包的候选 OpenAPI schema，检查非空、长度、日期、数值、数组约束和嵌套引用。首次测试发现 Gson 将声明为 Set 的数组去重，不能从 DTO 恢复原始重复项；测试现分别检查原始 JSON 和转换后的行为。修正后 `:core-data:testDebugUnitTest :core-data:assembleDebugAndroidTest assembleDebug lint --no-daemon` 成功，core-data 单测 15 项、失败和跳过均为 0；APK 内契约资源摘要与仓库文件一致，日志 `build/cache-schema-validation-rerun.log`。此校验发生在 DTO 反序列化之后，无法证明原始响应中 required nullable 字段存在或未包含重复 Set 项等，仍需原始响应校验；Room 设备测试仍未运行。

2026-10-30 修复缓存 GET 原始响应校验的断网误判：MockWebServer `DISCONNECT_AT_START` 实际返回 200 且 body 长度 0（而非在 `chain.proceed` 抛 IOException），原校验把空 body 的 EOFException 归为 `INVALID_RESPONSE`。改为空 body 视为传输失败、抛 `IOException` 映射 `NETWORK_ERROR`，非空但非法 JSON/schema 仍为 `INVALID_RESPONSE`；同时删除 `ApiCalls.java`、`RawResponseValidation.java`、`RealApiProvider.java` 的 4 处临时 debug 打印。JDK 17 下 `:core-data:testDebugUnitTest :core-data:assembleDebugAndroidTest assembleDebug lint --no-daemon` 返回 0，core-data 单测 16 项、失败和跳过均为 0。Room 设备测试仍仅编译未在设备运行，页面接入仍未完成。

2026-10-30 新增 core-data 公开 `PendingWriteStore`：`record` 在首次网络提交前持久化待确认意图（空字段校验、同 key 冲突 ABORT、不含 token/密码），`listPending` 按账号隔离返回恢复列表，`confirm` 按账号删除；`PendingWrite` 转为公开值对象，经 `ApiCalls.pendingWrites()` 与 `AppContainer` 装配。`PendingWriteStoreTest` 用 Proxy-DAO 覆盖持久化、空字段拒绝、同 key 冲突、账号隔离与跨账号删除无效。`:core-data:testDebugUnitTest :core-data:assembleDebugAndroidTest assembleDebug lint --no-daemon` 返回 0，core-data 单测 17 项、失败和跳过均为 0。家庭创建/加入 UI 消费方仍未建，`record` 的调用时机（确认页生成 key 时）待 feature 接入。

2026-10-30 新增 `scripts/contract_guard.py`：在 `contract_diff.py` 之上增加 IC 授权门禁——每个变更的 operation/schema 必须被状态为 ACCEPTED/IMPLEMENTING/VERIFIED/CLOSED 的 IC 记录的 allowedOperations/allowedSchemas/allowedSymbols 覆盖；YAML front matter 拒绝重复键与未知元数据字段。`python -m unittest discover -s scripts -p test_contract_guard.py` 6 项、失败 0，覆盖新增端点无 IC 失败、授权操作通过、PROPOSED 不授权、schema 变更需授权、重复键与未知字段拒绝；全量脚本测试 13 项、失败 0。尚未实现 digest 校验、Git SHA/`draft` 命令、路由与依赖检查，不能作为完整 §14.6 门禁。新增 `contracts/changes/README.md` 登记入口与模板。

2026-10-30 `contract_guard.py` 增加 `draft` 子命令（按 §14.5 生成 DETECTED 草稿，不覆盖已有 IC、不自动批准）；新增 `scripts/check_contracts.py` 与根任务 `checkContracts`（`validateContract`+`checkCandidateIndex`+public-surface/结构一致性）。脚本测试增至 14 项、失败 0；`gradlew.bat checkContracts --no-daemon` 返回 0（OpenAPI 验证无问题、索引最新、`contracts consistent`）。

2026-10-30 补全 `scripts/preflight.ps1/.sh`（§3 的 Python 3.11+/PyYAML 门禁 + §15 的 `validateContract generateClient generateServerApi checkContracts assembleDebug testDebugUnitTest lint` + 脚本自测 + 服务端 bootJar）；新增 `.github/workflows/ci.yml`（contracts/android/server 三 job，`permissions:contents:read`、`fetch-depth:0`、server 用 postgres:16 service），删除被取代的 `android-ci.yml`。`ci.yml` YAML 已校验；因本机 Python 3.10.9 与无远端授权，preflight 与 ci.yml 未在真实环境完整执行，属 UNVERIFIED。CODEOWNERS 因缺真实 GitHub 账号/team 未生成（不把「EF」当有效 owner）。

2026-10-30 `contract_guard.py` 增加 digest 校验：`contract_digest` 按 allowedOperations/allowedSchemas 的变更前后片段（用已有 operation/component 指纹、缺省为 null 哨兵）计算 `sha256:<64hex>`，`check` 对带非空 `contractDigest` 的 ACCEPTED IC 逐条核对。脚本测试增至 15 项、失败 0。digest 未做 §14.5 的「去掉纯 description/summary/example 变更」归一化（当前纯说明性变更会保守地改变 digest、产生假拒绝，不会假放行）。

2026-10-30 `contract_diff.py` 增加 `strip` 参数与 `_strip_descriptive`（去掉 description/summary/example/title），`contract_guard.contract_digest` 用 `strip=True` 计算，纯说明性变更不再改变 digest；脚本测试增至 17 项、失败 0。新增 6 个 fixtures（login-request、auth-session-response、create-household-request、household-response、join-household-request、membership-page-response），共 9 个、JSON 有效。新增 ArchUnit 1.5.0（版本经 Maven Central 核对，访问日期 2026-10-30）：`app` 的 `FeatureDependencyArchTest`（feature 不得依赖 okhttp3/java.net）与 `PublicSurfaceArchTest`（登记 Android 符号存在 + 成员名存在，2 项）共 3 项、失败 0；`assembleDebug testDebugUnitTest lint --no-daemon` 全量通过。`contract_guard.py` 增加 `load_ref`（文件路径或 Git SHA，经 `git show`），`check`/`draft` 支持 `--base SHA --head SHA`（Git SHA 路径未实测——无冻结 baseline）。

2026-10-30 接入家庭创建/加入界面（item 2 消费方）：`AuthRepository.refreshMe`（getMe 后更新 `SessionStore.householdId`）、feature-ef 的 `HouseholdHost`/`HouseholdViewModel`（幂等 key 生成→`PendingWrite.record`→提交→confirm→refreshMe；NETWORK_ERROR/TIMEOUT 保留 key 复用，被拒请求换新 key）/`HouseholdFragment`（中性布局）、`MainActivity`/`AppNavigator` 接线（登录后无家庭先到 HOUSEHOLD）。`assembleDebug testDebugUnitTest lint --no-daemon` 全绿，Android 单测 22 项、失败 0；public-surface 登记 `HouseholdHost`/`HouseholdFragment`/`AuthRepository.refreshMe`（27 symbols）。进程死亡恢复 UI（listPending 提示）仍未建，属独立后续。

2026-10-30 `PublicSurfaceArchTest` 增至参数个数匹配（方法/构造器按参数个数核对，泛型逗号识别）；家庭页接入进程死亡恢复：`HouseholdViewModel.recover()`（`listPending` 恢复未确认 create/join，同 key、锁定 payload）+ `abandon()`（confirm 删除）+ 布局「放弃」按钮与 `ef_recovered` 提示；PendingWrite payload 改为原始值便于恢复。`assembleDebug testDebugUnitTest lint --no-daemon` 全绿，Android 单测 22 项、失败 0。

2026-10-30 完整真实预检（§15）逐项执行：根 `validateContract generateClient generateServerApi checkContracts` BUILD SUCCESSFUL（修复根 `generateServerApi` 任务 Windows 下 `cmd /c gradlew.bat` 因 NoDefaultCurrentDirectoryInExePath 找不到脚本的问题，改为 `.\\gradlew.bat`）；根 `assembleDebug testDebugUnitTest lint` Android 单测 22 项、失败 0；server `test bootJar --rerun-tasks` 连真实 PostgreSQL（127.0.0.1:55432/civa_test，**用户名 civa**）16 项、失败 0；scripts 17 项、失败 0；`contract_guard.py` 对真实文件自比 PASS、对新增 operation 未授权 FAIL（exit 1）、`draft` 正确生成 DETECTED 草稿。测试后 PostgreSQL 已停止。

2026-10-30 收尾两项：`CacheStore.clearUser/clearHousehold` 失败改为重试一次（7 天 TTL 仍为兜底）；`PublicSurfaceArchTest` 从参数个数升级为参数类型简单名匹配（泛型类型变量擦除为 Object 处理）。`testDebugUnitTest lint` 全绿，Android 单测 22 项、失败 0，脚本测试 17 项、失败 0。

2026-10-30 `PublicSurfaceArchTest` 补全签名比对：方法返回类型 + 参数类型 + 字段类型简单名匹配（枚举常量、泛型类型变量擦除、`static` 修饰符均处理）。`testDebugUnitTest lint` 全绿。

2026-10-30 补 server 依赖锁（§3「提交依赖锁」）：`server/build.gradle` 加 `dependencyLocking { lockAllConfigurations() }`，生成 `server/gradle.lockfile`（87 条），`bootJar` 通过验证锁一致。

未完成：完整 OpenAPI、fixtures、公共接口登记和生产代码生成；真实 Android 业务页面、服务端程序、数据库迁移、真实账号家庭链路、门禁、CI 和全部业务接口。完整阶段预检、模拟器、远程 CI 和仓库保护检查尚未运行。阶段 A、B、C 均未通过，也没有阶段提交。
