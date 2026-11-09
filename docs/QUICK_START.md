# 在另一台 Windows 电脑上快速测试 Civa

本页用于团队内部开发交接，不是课程报告。当前一键启动器支持 Windows PowerShell；macOS/Linux 可按 README 的 Gradle/Compose 命令启动，本次没有实机验证这些系统。

## 第一次准备

安装 JDK 17、Docker Desktop、Android Studio。在 SDK Manager 安装 Android SDK Platform 36、Build Tools 35.0.0、Platform Tools 和 Android Emulator，再创建一个 API ≥26 的模拟器。镜像架构应与电脑相符。

打开 Docker Desktop，等待 Linux 引擎就绪。将完整项目源码放到本机目录，建议例如 `C:\projects\civa`；Android SDK 使用物理英文目录，例如 `C:\Android\Sdk`。不需要复制原开发者的 F 盘目录、用户名、Gradle 缓存或 JDK 文件。

当前工作区有大量未提交代码，旧的远端 main 不一定包含 Android 工程。交接时应提供经过审查的完整源码或包含这些代码的分支，不能只发旧 main 的链接。不要把 `.env`、`.idea`、`local.properties`、访问令牌和整份 `build/` 当作通用配置交给其他人。

## 三个日常命令

在包含 `settings.gradle` 的 **civa 根目录**打开 PowerShell：

```powershell
.\start-civa.bat -CheckOnly   # 检查环境、Docker 引擎，并写入本机 IDE 路径
.\start-civa.bat              # 启动数据库、后端，打开 Android Studio
.\start-civa.bat -Stop        # 停止本启动器的后端；保留数据库及数据
```

也可以直接双击 `start-civa.bat`。看到 `Backend ready` 后，在 Android Studio 选择 `app` 和自己的模拟器，等模拟器启动完毕后点击 Run。

首次启动自动生成带随机数据库密码的 `.env`；缺少后端 JAR 时自动执行 `bootJar`。以后重复启动会复用本启动器仍在运行的后端。其他程序占用 8080 时会报出 PID，不会自动杀掉它。

`-Stop` 根据启动时记录的 PID、进程启动时间和项目路径确认进程归属，不使用固定 PID。它不依赖 Docker 正在运行；因此先退出 Docker 后也能关闭后端。更早版本启动器留下的后端没有此记录，需按旧窗口打印的命令关闭一次。

## 查找不到工具时

脚本依次检查环境变量、项目本机配置、常见安装位置；Android Studio 还会从 Windows 安装记录中查找。自定义位置可在当前 PowerShell 会话设置：

```powershell
$env:JAVA_HOME = 'C:\Tools\jdk-17'                 # 根目录，不是 bin
$env:ANDROID_HOME = 'C:\Android\Sdk'
$env:CIVA_STUDIO_PATH = 'D:\Android Studio\bin\studio64.exe'
.\start-civa.bat -CheckOnly
```

这里是路径格式示例，必须替换成实际安装路径。脚本会拒绝 Java 25，并明确指出缺失的 SDK 组件。SDK 不要放在中文项目目录的联接内，避免本机已复现的原生模拟器路径兼容问题。检查不会自动安装或升级工具。

Gradle 缓存默认复用机器上的标准用户缓存；当前机器已有的项目缓存仍可复用。可以用 `GRADLE_USER_HOME` 显式指定，但不要复制原开发者的绝对路径。

Android Studio 弹出 SDK 路径不一致时，确认项目中的路径确实是完整的物理 SDK，再选择 **Use Project's SDK**。不要直接接受自动升级 Gradle/AGP 的提示。

## 重新构建与无需 IDE 的测试

```powershell
.\start-civa.bat -BuildServer              # 停止本启动器的旧后端，重新打包并启动
.\start-civa.bat -BuildApp                 # 构建 debug APK，再打开 Studio
.\start-civa.bat -NoStudio                 # 只启动数据库和后端，不要求 Android SDK/Studio
.\start-civa.bat -BuildApp -NoStudio       # 构建 APK，不打开 IDE；仍需要 Android SDK
```

修改后端源码后使用 `-BuildServer`，否则已有 JAR 会继续复用。修改 Android 源码后用 Run 或 `-BuildApp` 重新构建。首次构建需要网络下载 Gradle 和依赖，启动器无法消除这部分时间。Maven 403 的可选镜像方法见 [详细交接文档](LOCAL_RUN_HANDOFF.md#6-当前网络的-maven-403-处理)。

APK 位置：`app/build/outputs/apk/debug/app-debug.apk`。已有 APK 的测试者可启动后端后，直接安装 APK 到 Android Studio 模拟器；无需再次编译客户端，但仍需要运行后端。默认 debug APK 使用 `10.0.2.2:8080`，适用于电脑上的 Android 模拟器；真机连接方法见详细交接文档。

## 可复跑的检查

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\test_start_local.ps1
.\gradlew.bat testDebugUnitTest lint
python .\scripts\test_android_smoke.py --adb "$env:ANDROID_HOME\platform-tools\adb.exe" --serial emulator-5554
```

环境变量由子进程设置时不会回写父 PowerShell。执行最后一条前，父会话应先设置 `ANDROID_HOME`，或直接把 `--adb` 指向实际的 adb.exe。设备序列号以 `adb devices` 为准。

设备检查要求已安装最新 APK、系统启动完成和后端正常运行。每次会创建一个随机 `handoff_` 测试账号和 `HandoffSmoke` 家庭，只在开发数据库运行。它覆盖注册、登录、创建家庭、首页和五入口导航，不代表菜谱、库存、排餐、食记业务已完成。

日志在 `build/server-local.log`、`build/server-local-error.log`；本机后端状态在 `build/server-local.json`。这些是本机运行文件，不提交。更完整的排障、已验证范围及功能缺口见 [本地调试与运行交接](LOCAL_RUN_HANDOFF.md)。
