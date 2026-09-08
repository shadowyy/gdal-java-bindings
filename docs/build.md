完成。方法 b 走通了。

结果
- gdal-ffm-natives:natives-windows-x86_64 从 1.2 KB → 829 MB，含
bin/gdal.dll、bin/proj_9.dll、share/gdal、share/proj（695 个条目）
- Swiss 变体：40 MB（同样 DLL + 精简 PROJ 数据）
- 冒烟测试通过：smokeTestPackagedNative → OK（真实加载 GDAL 做了一次 translate，不只是打包成功）

最终命令
JAVA_HOME=/c/Users/yy/.jdks/azul-25.0.4 ./gradlew publishToMavenLocal

过程中踩到并绕开的坑（都在 tmp/，被 gitignore，未改仓库代码）
1. cph extract 在含硬链接的 .conda 包上报 seeking backwards is not allowed（Py 3.12/3.13 都有）→ 用 tmp/cph-shim/cph
包装：先 transmute 成 .tar.bz2，再用 Python tarfile 解包，符号链接/硬链接落地为副本（Windows
无管理员权限建不了符号链接）。
2. Python 是 uv 管理的（PEP 668），pip install --user 被拒 → 改用 uv tool install conda-package-handling。
3. anaconda.org 只有 ~10 KB/s，换 mirrors.ustc.edu.cn 约 6 倍速（部分包 404 则回退原站）；proj-data 755 MB 用 curl -C
- 断点续传才稳。
4. fetch-and-stage.sh 的清理步骤 find ... -exec rm -rf {} + 在已 stage 的目录上会失败（GNU find 删了还继续下钻）→
重跑前手动 -maxdepth 1 清理一次。这是脚本本身的 bug，Linux CI 上 BSD/GNU find
行为差异导致未暴露，需要的话我可以给上游提个 fix。

两点提醒
- 只有 windows-x86_64 有真实二进制，linux/osx 的 classifier jar 仍是空壳（本机用不到，要的话得各自 stage）。
- gdal-java-bindings/tmp/ 现在占 2.7 GB（下载的 conda 包 +
解压产物）。删掉不影响已发布的产物，但会失去重新打包的缓存；tmp/cph-shim/ 也在这个目录里，将来重新 stage

构建

- 在 Git-Bash 里跑；JAVA_HOME 指向 JDK 25（默认 java 版本不够会失败）。
- 会发布 gdal-ffm-core + gdal-ffm-natives/-swiss/-cn 的各 classifier JAR，版本见 gradle.properties（当前 0.1.3-gdal3.13.3-SNAPSHOT）。
- 前提：windows-x86_64 和 linux-x86_64 已 staged（已完成），assemble 会把 src/main/resources/... 打进 JAR；publishToMavenLocal 会先执行打包。


想快：只打当前平台的包（见下表第 3 行），或关掉不需要的变体（第 4 行）。
详细命令（一条条，Windows 用 .\gradlew.bat，都要带 -PgdalFfmJavaToolchainVersion=25）
# 0. 前置：抓取并 stage 某平台的二进制（每个 classifier 在对应系统上跑一次）
tools/natives/fetch-and-stage.sh windows-x86_64

# 1. 检查 staged 依赖闭环
tools/natives/audit-runtime-deps.sh windows-x86_64

# 2. 打全部 natives 包（5 平台 × standard/swiss/cn = 15 个 jar）
.\gradlew.bat :gdal-ffm-natives:assemble -PgdalFfmJavaToolchainVersion=25

# 3. 只打当前平台的包（最快，日常迭代用这个）
.\gradlew.bat :gdal-ffm-natives:nativesJarWindowsX8664 :gdal-ffm-natives:nativesSwissJarWindowsX8664 :gdal-ffm-natives:nativesCnJarWindowsX8664 -PgdalFfmJavaToolchainVersion=25

# 4. 只打 standard，关掉 swiss（更快）
.\gradlew.bat :gdal-ffm-natives:assemble -PgdalFfmJavaToolchainVersion=25 -PgdalSwissNativesEnabled=false

# 5. 打 core 包
.\gradlew.bat :gdal-ffm-core:jar -PgdalFfmJavaToolchainVersion=25

# 6. 打standard和cn，并且发布到本地仓库
.\gradlew.bat :gdal-ffm-natives:assemble :gdal-ffm-core:jar publishToMavenLocal -PgdalFfmJavaToolchainVersion=25 -PgdalSwissNativesEnabled=false

# 7. 构建 + 发布到本地 ~/.m2（上面都做完后，一步到位）
.\gradlew.bat publishToMavenLocal -PgdalFfmJavaToolchainVersion=25

# 单独发布到本地仓库（不构建）Gradle 是增量的：如果 jar 已打好且没改动，publishToMavenLocal 会把 jar 任务判为 UP-TO-DATE 直接跳过，只执行发布动作。所以"只发布"就是原命令再跑一遍，天然不重复构建：
## 1. 全部四个 publication（core + natives + swiss + cn）增量发布到 ~/.m2
.\gradlew.bat publishToMavenLocal -PgdalFfmJavaToolchainVersion=25 --offline
--offline 是保险：禁止它再去网上检查依赖，快且保证零构建外动作。想更细，只发某一个：
## 2. 只发 cn 变体
.\gradlew.bat :gdal-ffm-natives:publishNativesCnPublicationToMavenLocal -PgdalFfmJavaToolchainVersion=25 --offline

## 3. 只发 core
.\gradlew.bat :gdal-ffm-core:publishMavenJavaPublicationToMavenLocal -PgdalFfmJavaToolchainVersion=25 --offline
产物位置：~/.m2/repository/ch/so/agi/gdal-ffm-core/<version>/、gdal-ffm-natives/、gdal-ffm-natives-swiss/、gdal-ffm-natives-cn/。