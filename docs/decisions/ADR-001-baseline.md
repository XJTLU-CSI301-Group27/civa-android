# ADR-001 候选技术基线

状态：候选，未验证。采用 PROJECT_DOC.md 第 3 节指定版本和技术方案；暂未调整版本。实际依赖解析、构建及兼容性验证完成后记录命令、结果和必要的官方来源。

Windows 工作目录包含非 ASCII 字符。AGP 8.13.2 初次配置失败并建议设置 `android.overridePathCheck=true`；由于指定项目目录不能随意迁移，暂按提示开启该开关继续验证。此开关只允许继续尝试，不证明后续构建工具均支持该路径；必须实际构建和在其他机器验证。Gradle 8.13 Wrapper JAR 与发行包 SHA-256 以 [Gradle 官方校验表](https://gradle.org/release-checksums/) 为准。

网络层按规格使用与 Retrofit 同版本的 `converter-gson:2.11.0`，共享 `serializeNulls` Gson。OkHttp 禁用 `retryOnConnectionFailure`，由调用方显式处理重试，以免不知情地重发写入；GET 可在确认当前会话后重新创建请求。会话只保存在内存，请求创建时绑定当时的会话代次；切换账号后的旧请求通过统一 ApiCalls 丢弃。该实现仍需和认证、缓存、应用容器及架构约束集成验证。

服务端生成器的字符串长度模板使用 Hibernate Validator 的 `CodePointLength`，保持 OpenAPI 字符长度与 Unicode 码点一致；数组长度仍使用 `Size`。默认 `Size` 对 Java 字符串按 UTF-16 单元计数，会拒绝合法的 120 个补充平面字符。模板基于固定的 OpenAPI Generator 7.17.0 版本，并由生成模型测试验证边界。

Room 2.8.4 通过 Java annotationProcessor 生成本地数据库。首次构建下载其构建期 Guava 33.2.1-jre 时 Maven Central TLS 握手失败；显式在 annotationProcessor 配置使用本机已解析的 33.3.1-jre 后生成和构建成功。该固定版本只用于编译器配置，尚需在其他机器的干净缓存中验证，不能把本机缓存结果视为可复现性验收。
