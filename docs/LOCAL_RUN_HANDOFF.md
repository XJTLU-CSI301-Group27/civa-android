# Civa 本地调试与运行交接

更新日期：2026-11-09。本文用于开发者接手环境、构建和排障，不是课程提交报告。功能设计、课程报告和演示须由团队按课程要求自行完成。

**新成员先读：[另一台电脑快速测试](QUICK_START.md)。** 启动器现已自动检测 JDK/SDK/Studio，不再写死 F 盘。支持 `-CheckOnly`、`-Stop`、`-BuildServer`、`-BuildApp`、`-NoStudio`；下文的固定路径只是原开发机的已验证环境记录。

## 1. 当前交付范围

项目是原生 Android Java 应用，客户端通过 HTTP 调用 Java/Spring Boot 后端，后端使用 PostgreSQL。Docker 目前只承载开发数据库；Android App 不在 Docker 中运行。后端 JAR 是服务端程序，APK 是安装到 Android 的程序，二者不能互相替代。

本次已修复 Windows PowerShell 对 Docker 退出码的误判，统一 Android Studio/启动器的 JDK 与 SDK，并处理当前网络访问 Maven Central 的 403。APK 构建、Android 的 22 项本地单元测试及 lint 已实际通过。

设备联调进一步发现并修复了家庭创建后进入首页的导航崩溃：`popBackStackImmediate` 恢复旧 Fragment 时触发 `onResume` 再次导航，产生 FragmentManager 事务重入。公共导航已改为排队执行 `popBackStack`，与原有异步 `commit` 一起处理。修复后已在 API 35 模拟器通过注册、登录、创建家庭和五个导航入口切换。

**已有页面和接口不等于完整产品。** 登录、注册及家庭创建/加入已有实现；当前 `AppNavigator` 除登录、家庭外仍使用占位页。菜谱、库存、排餐、食记等多个服务端操作仍返回 `501 CONTRACT_NOT_IMPLEMENTED`。本次环境调试没有实现这些业务。

## 2. 技术基线与目录

| 项目 | 当前配置 |
| --- | --- |
| Java | JDK 17；不要选择 Android Studio 自带的 Java 25 运行 Gradle 8.13 |
| Gradle / Android 插件 | Wrapper 8.13 / AGP 8.13.2；不要为消除提示随意升级 |
| Android | minSdk 26，compileSdk/targetSdk 36；包名 `edu.xjtlu.civa` |
| Android UI | XML Views、View Binding、Fragment、ViewModel/LiveData |
| 网络 / 缓存 | Retrofit/OkHttp/Gson；Room 只读缓存与待确认写入记录 |
| 后端 | Java 17、Spring Boot 3.5.16、JDBC、Flyway |
| 数据库 | PostgreSQL 16；开发 Compose 文件 `infra/compose.yaml` |
| 业务契约 | `contracts/openapi.json`，仍为待团队批准的候选版本 |

| 路径 | 用途 |
| --- | --- |
| `app/` | 应用入口、装配和导航 |
| `feature-ac/` | AC 负责的菜谱、库存模块；业务页面待开发 |
| `feature-bd/` | BD 负责的排餐、食记模块；业务页面待开发 |
| `feature-ef/` | EF 负责的登录、家庭等公共入口 |
| `core-data/`、`core-ui/` | 会话、网络、缓存及公共 UI |
| `api-client/` | 从 OpenAPI 生成的 Java 客户端 |
| `server/` | 独立后端 Gradle 工程 |
| `scripts/start-local.ps1` | 本机开发启动器 |
| `scripts/local-maven.init.gradle` | 可选的本机 Maven Central 镜像配置 |

源码与业务说明以 `PROJECT_DOC.md`、`AGENTS.md`、`OPEN_QUESTIONS.md` 和现有 `docs/HANDOFF.md` 为准。本文件补充本次运行环境和验证结果。

## 3. 本机已配置的环境

实际项目：`E:\XJTLU\大四\CSI301\CW\code\civa`。

启动器通过目录联接 `%LOCALAPPDATA%\CivaLocal` 访问同一项目，避开部分 Windows Java/Gradle 工具对中文路径的兼容问题。它不是另一份源码。

| 本机位置 | 用途 |
| --- | --- |
| `%LOCALAPPDATA%\CivaLocal\build\jdk17\jdk-17.0.21.0.2+1` | JDK 17 |
| `F:\Android_SDK` | 当前优先使用的物理 SDK 根目录，已有 Android 36、Build Tools 35.0.0、Platform Tools、Emulator 和 API 35 镜像 |
| `%LOCALAPPDATA%\CivaLocal\build\android-sdk` | 原本机 SDK；保留为回退，不再优先用于 IDE 启动模拟器 |
| `%LOCALAPPDATA%\CivaLocal\build\gradle-user-home` | 本次 Android 构建使用的 Gradle 缓存 |
| `F:\Android Studio\bin\studio64.exe` | Android Studio |
| `F:\Android_SDK\emulator\emulator.exe` | 已安装的模拟器程序 |
| `build/android-sdk/emulator` | 指向上述模拟器目录的本机联接，供当前 SDK 和 IDE 复用 |
| `F:\Android_SDK\system-images\android-35\default\x86_64` | 当前 AVD 使用的 Android 35 AOSP 镜像 |
| `build/avd/Civa_API35.avd` | 本机模拟器数据 |

这些 JDK、SDK、缓存和模拟器属于本机依赖，不随 Git 自动交付。其他成员不能照搬本机用户名或 F 盘路径。当前工具链放在根 `build/` 内，接手者清理此目录前必须先迁移/备份工具链；常规开发无需先执行根工程 `clean`。

启动器会维护以下忽略文件，保留其中其他属性，并写入无 BOM、正确转义的路径：

```properties
# .gradle/config.properties；示例，请使用自己的实际路径
java.home=C\:/Users/xyl/AppData/Local/CivaLocal/build/jdk17/jdk-17.0.21.0.2+1

# local.properties
sdk.dir=F\:/Android_SDK
```

Android Studio：`File → Settings → Build, Execution, Deployment → Build Tools → Gradle`，将 Gradle JDK 设为 `GRADLE_LOCAL_JAVA_HOME`，Gradle user home 使用上述本机缓存目录。修改配置后执行 `File → Sync Project with Gradle Files`。

如弹出 “The project and Android Studio point to different Android SDKs”，先核对项目的 SDK 是已补齐组件的 **F:\Android_SDK**，再选择 **Use Project's SDK**。F 盘 SDK 最初缺少 Android 36 和 Build Tools 35；本次已从经过验证的本机 SDK 补齐，因此不能继续沿用“F 盘 SDK 缺组件”的旧判断。

## 4. 本机日常启动

1. 打开 Docker Desktop，等待 Linux 引擎可用；`docker version` 应同时显示 Client 和 Server。
2. 双击工作区根目录的 `start-civa.bat`。脚本检查环境、启动 PostgreSQL、启动后端，然后打开 Android Studio。
3. 在 Android Studio 选择运行配置 `app`、设备 `Civa_API35`，点击绿色 Run。没有运行配置时先完成 Gradle Sync；无需先选择某个 Java 文件。

本机模拟器也可单独启动：

```powershell
$env:ANDROID_HOME = 'F:\Android_SDK'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
& "$env:ANDROID_HOME\emulator\emulator.exe" -avd Civa_API35 -no-snapshot -gpu auto
```

2026-11-09 再次运行时，IDE 构建约 5 秒即完成，但模拟器持续 `offline`。无快照方式手动启动曾恢复，但**仅设置冷启动并未解决 Studio 直接 Run 的重复崩溃**。后续日志记录模拟器退出码 `0xC0000005`：Studio 从还原后的中文项目路径加载原生组件，与先前手动从物理 F 盘启动的路径不同。补齐并选用物理 `F:\Android_SDK` 后，Studio 日志确认加载 `F:\Android_SDK\emulator\lib64\vulkan\vulkan-1.dll`；保留同一镜像和 AVD 数据，连续两次从模拟器完全关闭状态直接 Run 成功，分别于 18:34:57、18:36:11 自动打开 MainActivity。该结果支持此机的 SDK 路径兼容性判断，不代表已定位到原生崩溃的具体函数。

以后 Run 长时间无响应时，先在底部 Build 查看是否已经 `BUILD SUCCESSFUL`，再检查设备状态和模拟器退出码。首先核对 SDK 是实际的英文安装目录；Cold Boot Now 是设备排障步骤，不能替代 SDK 路径修正。不要因此重复下载 Gradle 或清空数据库。

首次启动会从 `.env.example` 生成 `.env`，使用随机开发数据库密码。不要把 `.env`、访问令牌或真实密码发进群聊或提交 Git。修改 `.env` 中的密码不会自动重置已有 PostgreSQL 数据卷内的密码。

数据库仅绑定 `127.0.0.1:5432`；后端监听 `8080`。模拟器中的 `localhost` 指向模拟器本身，debug APK 默认通过 `http://10.0.2.2:8080/` 访问电脑后端。

新启动器会复用由它自己启动、身份记录仍有效的后端；其他程序或旧启动器留下的进程占用 8080 时才会拒绝启动并给出 PID。可用项目根目录的 `start-civa.bat -Stop` 停止新启动器的后端，再运行。

### 命令行构建与安装

在本机项目目录打开 PowerShell：

```powershell
$projectPath = Join-Path $env:LOCALAPPDATA 'CivaLocal'
$env:JAVA_HOME = Join-Path $projectPath 'build\jdk17\jdk-17.0.21.0.2+1'
$env:ANDROID_HOME = 'F:\Android_SDK'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = Join-Path $projectPath 'build\gradle-user-home'
Set-Location -LiteralPath $projectPath

.\gradlew.bat :app:assembleDebug --console=plain
.\gradlew.bat testDebugUnitTest lint --console=plain
```

生成 APK：`app/build/outputs/apk/debug/app-debug.apk`。

模拟器启动后，也可用 ADB 安装和启动：

```powershell
$adbPath = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
& $adbPath devices -l
& $adbPath -s emulator-5554 shell getprop sys.boot_completed
# 只有上一条返回 1，才继续安装；序列号以 devices 的实际输出为准。
& $adbPath -s emulator-5554 install -r .\app\build\outputs\apk\debug\app-debug.apk
& $adbPath -s emulator-5554 shell am start -W -n edu.xjtlu.civa/.MainActivity
```

### 后端源码修改后的处理

启动器优先执行已有 `server/build/libs/civa-server.jar`，没有 JAR 时才使用 Gradle `bootRun`。修改后端源码后需要停止旧后端，再构建并重新启动；仅修改源码不会更新正在运行的 JAR。

```powershell
# 在项目根目录，使用上面配置的 JDK 17 环境
.\server\gradlew.bat -p server bootJar --console=plain
```

新机器必须能解析后端插件和依赖。本次 Android 镜像脚本只修改 settings 中的依赖仓库，不保证解决后端插件仓库的所有网络问题；不要因 Android 构建成功就宣称后端可从全新缓存构建。

### 停止服务

后端进程停止命令由启动器在成功后打印。先确认 PID 对应本次后端，再停止进程树。停止开发数据库保留数据卷：

```powershell
docker compose --env-file .env -f infra/compose.yaml stop
```

不要使用 `down -v` 作为普通重启方法，它会删除数据库卷。

## 5. 其他成员首次接手

1. 获取完整源码并阅读 `AGENTS.md`。当前工作树存在大量未提交文件；不要只交付 Git 的旧提交后假定它包含这次工作。
2. 安装 JDK 17、Android Studio、Android SDK Platform 36、Build Tools 35.0.0、Platform Tools、Android Emulator，以及一个 API ≥26 的兼容 x86_64/ARM64 镜像。
3. 使用 Android Studio 打开包含 `settings.gradle` 的 `civa` 根目录，不要只打开 `app` 或 `server`。
4. 在 Gradle 设置中选择自己机器上的 JDK 17，通过 SDK Manager 确认 SDK，并使项目 `local.properties` 指向该 SDK。自己的路径不要提交。
5. 启动 PostgreSQL 16；可复用本项目 Docker Compose。按 `.env.example` 创建自己的 `.env`。
6. 按 `README.md` 设置后端的 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`CIVA_MEDIA_DIR`，构建并启动后端。
7. 先让 Gradle Sync、`assembleDebug`、单元测试和 lint 通过，再安装到模拟器并验证现有功能。

仓库根目录的 `start-civa.bat` 现为可用于其他 Windows 电脑的入口。它从 `JAVA_HOME`、PATH、本机配置及常见安装位置查找 JDK 17，从 `ANDROID_HOME`/`ANDROID_SDK_ROOT`、`local.properties` 和常见位置查找 SDK，并从环境变量、PATH 或 Windows 安装记录查找 Android Studio。原本的 `build/jdk17` 只作为已有环境回退，不是新成员必须复制的目录。它不是工具自动安装器；macOS/Linux 仍使用 README 的手动步骤。

真机不能直接使用 `10.0.2.2`。USB 调试时可先 `adb reverse tcp:8080 tcp:8080`，并构建带 `-PCIVA_API_BASE_URL=http://127.0.0.1:8080/` 的 debug APK。构建参数修改后需要重新安装。release 要求显式 HTTPS 地址。

## 6. 当前网络的 Maven 403 处理

本机实测 Maven Central 返回：当前出口 IP 被识别为开放 HTTP/SOCKS 代理。更换到允许访问的网络/代理出口可以解决；本次采用可选的阿里云 Central 镜像，仅用于这台机器的 Gradle 初始化。

```powershell
# 在 civa 根目录；GRADLE_USER_HOME 必须与 Android Studio 中配置一致。
$initPath = Join-Path $env:GRADLE_USER_HOME 'init.d'
New-Item -ItemType Directory -Force -Path $initPath | Out-Null
Copy-Item .\scripts\local-maven.init.gradle (Join-Path $initPath 'local-maven.gradle')
```

网络恢复后可以删除这个 `init.d/local-maven.gradle`，恢复项目声明的原始仓库。此配置不修改业务版本，也不关闭 HTTPS 或 ZIP 校验。Google Android 仓库仍使用项目原配置。

模拟器镜像本次从腾讯 AndroidSDK 镜像下载，按 Google 官方仓库元数据核对 SHA-1：

```text
文件：x86_64-35_r02.zip
大小：782404023 字节
SHA-1：2d857d170c0d1b827149565da34b3383e5306f7f
官方元数据：https://dl.google.com/android/repository/sys-img/android/sys-img2-3.xml
```

## 7. 已遇到的问题与处理

| 现象 | 根因 / 正确处理 |
| --- | --- |
| Docker Desktop 开着仍等待 60 秒后失败 | Windows PowerShell 的进程对象没有保留退出码；启动器已在等待前缓存进程 Handle |
| Gradle 8.13 不兼容 Java 25 | 用 JDK 17；IDE 自带 JBR 与项目 Gradle JVM 是两个不同设置 |
| `Archive is not a ZIP archive` | 最初是 F 盘 SDK 下载缺失平台/Build Tools 时取得异常包；已用完整、校验过的组件补齐，不是业务代码异常 |
| Gradle 发行包 SHA-256 校验失败 | 下载损坏；保留官方校验，重新下载或复用完整缓存，不要关闭校验或填写损坏文件的哈希 |
| Maven `403 Forbidden` | 当前网络出口被 Central 拒绝；使用合适网络或上述本机镜像 |
| SDK 路径 `PropertyEscape` lint 错误 | Windows 盘符冒号须转义，例如 `sdk.dir=C\:/Android/Sdk`；启动器已修正 |
| 修改路径后 lint 仍报旧内容 | 本次重新运行任务以刷新旧分析结果；先检查实际配置，再使用 `lint --rerun-tasks` |
| 创建家庭后退回桌面；`FragmentManager is already executing transactions` | 公共导航同步弹栈触发旧页面的生命周期回调并再次导航；已改为异步弹栈，并用设备流程复测 |
| Run 灰色 / 没有 app | 完成项目 Gradle Sync，确认打开的是整个 Android 根工程 |
| 设备 offline / 安装时系统服务空指针 | 首次启动尚未完成；等待 `sys.boot_completed=1` 后再安装 |
| Run 编译已完成，数分钟仍不打开 App | 若模拟器 offline 或以 0xC0000005 退出，先检查物理 SDK 路径；此机已通过 SDK 路径修正后两次直接 Run。冷启动设置单独使用未解决重复崩溃 |
| `501 CONTRACT_NOT_IMPLEMENTED` | 当前业务尚未实现，不能靠换 JDK、重装 SDK 或重启后端解决 |

## 8. 本次验证证据

| 检查 | 2026-11-09 实际结果 / 证据 |
| --- | --- |
| `:app:assembleDebug testDebugUnitTest lint` | 导航修复后通过；`build/android-local-final.log` |
| `testDebugUnitTest lint --rerun-tasks` | 修复本地路径后通过；22 测试，0 失败、0 错误、0 跳过；`build/android-local-tests.log`。导航修复后上述完整命令再次通过 |
| 后端 `unitTest --offline` | 8 测试，0 失败、0 错误、0 跳过；使用已有 `%TEMP%\civa-gradle-8.13` 缓存，`build/server-local-unit-check.log`；不代表集成测试或全新缓存验证 |
| `scripts/test_start_local.ps1` | 通过；覆盖路径转义、重复配置、保留其他属性和无 BOM |
| 可移植启动器检查 | 在不含项目内 JDK/SDK 的英文带空格临时目录中，通过显式环境变量完成检查；仅后端检查在 SDK 路径无效时仍通过。日志：`build/portable-env-check.log`、`build/portable-headless-check.log` |
| 可移植启动器生命周期 | 启动、已有后端复用、归属验证后停止均已执行；从源码 `-BuildServer -NoStudio` 构建并启动成功，`-BuildApp -NoStudio` 构建成功；日志：`build/portable-backend-build.log`、`build/portable-app-build.log` |
| 可移植入口后的设备回归 | 安装新构建 APK，并连接重新构建后的后端，注册、登录、创建家庭、五入口导航再次通过；`build/portable-device-smoke.log` |
| 本机 Android 平台 JAR ZIP 校验 | 通过 |
| Android 35 系统镜像官方哈希校验 | 通过；`build/system-image-download.log` |
| PostgreSQL / 后端启动 | 数据库 healthy，后端已监听 8080，日志记录数据库连接及 Flyway 迁移成功 |
| 模拟器安装与现有功能验证 | API 35，`adb install -r` 成功；注册、登录、创建家庭、进入今天及五入口切换通过；`build/device-smoke.log` |
| Android Studio 实际操作 | 17:54:47 Gradle Sync 完成；17:56:38 IDE Run 构建完成，17:56:40 安装并启动 `edu.xjtlu.civa.MainActivity`；不是仅命令行构建 |

IDE 原始日志位于本机 `%LOCALAPPDATA%\Google\AndroidStudio2026.2.1\log\idea.log`。IDE 旧的普通 Java 模块 `CivaLocal.iml` 注册已从本机 `.idea/modules.xml` 移除；Android 模块仍由 Gradle 导入管理，未删除项目源码。当前运行配置为 `app`，目标设备为 `Civa API 35`。

### 重跑设备回归

保持开发后端和数据库运行，安装最新 debug APK，并等待模拟器完全启动：

```powershell
# 在 civa 根目录；ANDROID_HOME 已按前文设置
python .\scripts\test_android_smoke.py --serial emulator-5554
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\test_start_local.ps1
```

设备脚本使用 Python 标准库和 ADB，无需额外测试框架。它会停止并重新打开当前 App，每次在开发数据库创建一个随机 `handoff_` 测试账号和名为 `HandoffSmoke` 的家庭；不会清空数据库或 App 数据。请只针对开发环境运行。五入口检查验证页面导航，不将占位页面计为业务功能通过。

日志和测试 XML 多数在忽略的 `build/` 目录。向他人提供证据前先检查敏感信息；不要直接分享完整桌面截图或含会话令牌的 HTTP 响应。

## 9. 后续工作与边界

- AC：实现菜谱、收藏和库存的真实读写页面与服务端操作。
- BD：实现排餐、做饭确认、食记和汇总；做饭扣库存需要服务端事务与重复请求保护。
- EF：持续维护账号/家庭、装配、共享组件与集成；邀请创建和成员移除仍有未完成操作。
- 团队：处理 `OPEN_QUESTIONS.md` 中的契约、权限和邀请码决策，提供真实成员编码贡献记录。
- 设备联调成功只证明本次实际验证的路径，不代表全部业务、双账号权限、离线恢复、真机、release、远端 CI 或全新机器构建均已通过。

服务端集成测试只允许使用名称以 `_test` 结尾的隔离 PostgreSQL 数据库。已有 guard 会拒绝其他数据库；不要为了使测试通过而关闭它。本次后端启动使用的是开发数据库，不能直接拿来运行集成测试。
