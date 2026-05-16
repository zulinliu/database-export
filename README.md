# 数据库导出工具 V2.0

一款面向企业内网的数据库数据导出系统，专注于解决生产库到开发/测试环境的数据同步需求。系统提供灵活的数据导出能力和可复用的任务模板管理，支持高效、安全地导出大规模数据。

---

## 核心亮点

### 灵活的导出模式
- **自定义表名导出** - 手动输入表名列表，支持顿号、逗号、换行多种分隔方式
- **表勾选导出** - 从数据库表列表中可视化选择，支持搜索、全选、反选
- **自定义SQL导出** - 输入SELECT语句，内置SQL注入防护和语法校验

### 强大的数据过滤
- **时间范围过滤** - 按日期字段范围过滤，自动识别表中是否存在目标字段
- **自定义字段过滤** - 支持等于/包含/范围三种过滤方式
- **策略组合** - 两种过滤策略可同时启用，智能组合SQL条件

### 高性能导出引擎
- 多线程并行导出，Semaphore控制并发连接数
- 流式读取（fetchSize控制）+ 流式写入（SXSSFWorkbook），内存占用可控
- Excel(.xlsx) + INSERT SQL(.sql) 双格式输出

### 安全可靠
- 所有数据库连接强制只读模式
- SQL注入防护（白名单+黑名单+注释去除+分号拦截）
- 会话超时自动登出（30分钟）
- 单表导出失败自动跳过，不影响其他表

### 任务模板管理
- 保存/加载/删除导出配置模板
- 批量导入导出模板（ZIP格式）
- 一键加载历史配置，免去重复设置

### 精美UI
- 深色/浅色主题一键切换
- 实时导出进度展示（进度条+分级日志）
- 连接池状态可视化监控
- 响应式布局，适配各种屏幕尺寸

---

## 所需环境

### 运行环境（部署使用）

| 组件 | 要求 | 说明 |
|------|------|------|
| JDK | 8 或更高版本 | 推荐 JDK 8 / 11 / 17 |
| 数据库 | 达梦DM7/8 或 MySQL 5.7+ | 需要数据库的JDBC驱动 |

### 开发环境（二次开发）

| 组件 | 要求 | 说明 |
|------|------|------|
| JDK | 8 或更高版本 | 编译和运行 |
| Maven | 3.6+ | 项目构建 |
| 数据库 | 达梦DM 或 MySQL | 测试用 |

> **注意**：默认已包含 MySQL 驱动。如需连接达梦数据库，需将达梦JDBC驱动（DmJdbcDriver.jar）放入项目并添加Maven依赖。

---

## 快速开始

### 方式一：直接运行（推荐）

1. **确认Java环境**

   ```bash
   # 检查Java版本
   java -version
   # 应显示 1.8.x 或更高版本
   ```

2. **运行安装脚本**

   ```bash
   # Linux / macOS
   chmod +x scripts/install.sh
   ./scripts/install.sh
   ```

   ```bat
   :: Windows
   scripts\install.bat
   ```

3. **启动服务**

   ```bash
   # Linux / macOS
   ./scripts/start.sh
   ```

   ```bat
   :: Windows
   scripts\start.bat
   ```

4. **访问系统**

   打开浏览器访问：**http://localhost:8080**

   默认账号：`admin`
   默认密码：`123456`

### 方式二：手动启动

```bash
# 1. 打包（需要Maven）
mvn clean package -DskipTests

# 2. 创建必要目录
mkdir -p exports templates logs temp

# 3. 直接运行jar
java -jar target/db-export-tool.jar
```

### 停止服务

```bash
# Linux / macOS
./scripts/stop.sh

# Windows
scripts\stop.bat

# 或直接 Ctrl+C 停止前台进程
```

---

## 使用说明

### 第一步：登录

1. 打开浏览器，访问 `http://localhost:8080`
2. 输入账号密码（默认 admin / 123456）
3. 点击"登录"进入系统

### 第二步：连接数据库

1. 选择**数据库类型**（达梦 / MySQL / 自定义）
2. 填写**主机地址**（如 192.168.1.100）
3. 填写**端口号**（达梦默认5236，MySQL默认3306）
4. 填写**数据库名**、**用户名**、**密码**
5. 点击**"测试连接"**验证配置是否正确
6. 点击**"连接"**建立数据库连接

> **提示**：连接成功后，状态栏会显示绿色连接状态，连接池监控面板会自动出现。

### 第三步：选择导出模式

#### 自定义表名导出
适合已知具体表名的场景。在文本框中输入表名，支持以下分隔方式：
- 顿号分隔：`TABLE1、TABLE2、TABLE3`
- 逗号分隔：`TABLE1,TABLE2,TABLE3`
- 换行分隔：每行一个表名
- 混合使用也可以

#### 表勾选导出
适合不确定具体表名，需要浏览选择的场景：
1. 连接数据库后，切换到"表勾选"标签
2. 可以在搜索框中输入关键词筛选表名
3. 使用"全选"、"反选"、"清空"快捷操作
4. 勾选需要导出的表

#### 自定义SQL导出
适合需要精确控制查询条件的场景：
1. 切换到"自定义SQL"标签
2. 每行输入一条SELECT语句
3. 点击"验证SQL"检查语法
4. 系统会自动从SQL中提取表名作为Sheet名

### 第四步：配置数据过滤（可选）

#### 时间范围过滤
1. 开启"时间范围过滤"开关
2. 输入**时间字段名**（支持多个字段，用顿号分隔，如 `DATE、DATA_TIME`）
3. 选择**开始日期**和**结束日期**
4. 系统会自动检查表中是否存在该字段，不存在则导出全量数据

#### 自定义字段过滤
1. 开启"自定义字段过滤"开关
2. 输入**字段名**（如 `CASE_ID`）
3. 选择**过滤方式**：
   - **等于**：精确匹配，如输入 `111`，生成 `WHERE CASE_ID = '111'`
   - **包含**：多值匹配，如输入 `111,222,333`，生成 `WHERE CASE_ID IN ('111','222','333')`
   - **范围**：数值范围，如输入 `100-200`，生成 `WHERE CASE_ID >= 100 AND CASE_ID <= 200`
4. 输入**参数值**

> **提示**：两种过滤策略可以同时启用，系统会自动组合条件。

### 第五步：选择导出格式

- **Excel (.xlsx)** - 单文件多Sheet，每个表一个Sheet
- **INSERT SQL (.sql)** - 批量插入格式，默认每1000条一批
- **两者同时** - 同时生成两种格式，打包到一个ZIP文件

### 第六步：开始导出

1. 调整**最大并发连接数**（默认5，范围1-10）
2. 点击**"开始导出"**
3. 系统会显示实时导出进度：
   - 进度条和百分比
   - 当前正在处理的表名和数据条数
   - 已完成表数/总表数
   - 已用时和预计剩余时间
   - 分级实时日志（INFO/SUCCESS/WARNING/ERROR）
4. 导出过程中可以点击**"取消导出"**
5. 导出完成后会弹出统计弹窗，可以立即下载

### 使用任务模板

#### 保存模板
1. 配置好导出参数后，点击**"保存为模板"**
2. 输入模板名称和描述
3. 点击"保存"

#### 使用模板
1. 切换到"模板管理"页面
2. 找到目标模板，点击**"加载"**
3. 配置会自动填充到导出页面
4. 根据需要调整参数后开始导出

#### 批量导入/导出模板
1. 勾选需要导出的模板
2. 点击**"批量导出"**，下载ZIP文件
3. 在另一台机器上点击**"批量导入"**，上传ZIP文件即可

### 查看导出记录
1. 切换到"导出记录"页面
2. 可以查看所有历史导出文件
3. 支持重新下载、删除文件

---

## 配置文件详解

配置文件位于 `src/main/resources/application.yml`（打包后内嵌于jar中），主要配置项说明如下：

### 服务端口
```yaml
server:
  port: 8080                    # 服务端口号，默认8080
  servlet:
    session:
      timeout: 1800s            # 会话超时时间，默认30分钟(1800秒)
```

### 导出引擎
```yaml
export:
  thread:
    core: 4                     # 导出线程池核心线程数
    max: 8                      # 导出线程池最大线程数
  batch:
    size: 1000                  # SQL批量插入每批条数
  fetch-size: 1000              # JDBC流式读取每次获取条数
  storage:
    path: ./exports             # 导出文件存储目录
    temp: ./temp                # 临时文件目录
  timeout: 3600                 # 导出任务超时时间(秒)
```

### 模板存储
```yaml
template:
  storage:
    path: ./templates           # 模板文件存储目录
```

### 安全配置
```yaml
security:
  default:
    username: admin              # 登录用户名
    password: "123456"           # 登录密码（生产环境请务必修改！）
  session:
    timeout: 1800                # 会话超时时间(秒)
```

### 日志配置
```yaml
logging:
  level:
    com.dbexport: DEBUG          # 日志级别：DEBUG/INFO/WARN/ERROR
  file:
    path: ./logs                 # 日志文件目录
    name: db-export-tool.log     # 日志文件名
```

### 修改配置的方式

1. **修改源码配置后重新打包**（推荐用于永久修改）
   ```bash
   # 修改 src/main/resources/application.yml 后
   mvn clean package -DskipTests
   ```

2. **使用外部配置覆盖**（推荐用于部署时修改）
   ```bash
   # 在jar同级目录创建 application.yml
   java -jar target/db-export-tool.jar --spring.config.location=file:./application.yml
   ```

3. **使用命令行参数覆盖**
   ```bash
   java -jar target/db-export-tool.jar --server.port=9090 --security.default.password=your_new_password
   ```

---

## 目录结构

```
db-export-tool/
├── pom.xml                          # Maven项目配置
├── README.md                        # 项目文档
├── scripts/
│   ├── install.sh / install.bat     # 安装脚本
│   ├── start.sh / start.bat         # 启动脚本
│   └── stop.sh / stop.bat           # 停止脚本
├── src/
│   └── main/
│       ├── java/com/dbexport/
│       │   ├── DbExportApplication.java    # 启动类
│       │   ├── config/                     # 配置类
│       │   │   ├── AsyncConfig.java        # 异步线程池
│       │   │   ├── AuthInterceptor.java    # 认证拦截器
│       │   │   ├── ExportConfigProperties.java  # 导出配置
│       │   │   ├── SecurityConfigProperties.java # 安全配置
│       │   │   └── WebConfig.java          # Web配置
│       │   ├── controller/
│       │   │   └── ExportController.java   # 主控制器
│       │   ├── model/                      # 数据模型
│       │   ├── service/                    # 服务接口
│       │   ├── service/impl/               # 服务实现
│       │   └── util/                       # 工具类
│       └── resources/
│           ├── application.yml             # 应用配置
│           ├── static/
│           │   ├── css/style.css           # 样式文件
│           │   └── js/main.js              # 前端逻辑
│           └── templates/
│               ├── login.html              # 登录页面
│               └── index.html              # 主页面
├── exports/                         # 导出文件存储（运行时生成）
├── templates/                       # 模板数据存储（运行时生成）
├── logs/                           # 日志目录（运行时生成）
└── temp/                           # 临时文件（运行时生成）
```

---

## 注意事项

1. **达梦数据库驱动**：默认项目未内置达梦JDBC驱动。如需连接达梦数据库，请将 `DmJdbcDriver.jar` 添加到项目依赖中。修改 `pom.xml` 添加本地jar依赖：
   ```xml
   <dependency>
       <groupId>com.dameng</groupId>
       <artifactId>DmJdbcDriver18</artifactId>
       <version>8.1.2.192</version>
       <scope>system</scope>
       <systemPath>${project.basedir}/lib/DmJdbcDriver18.jar</systemPath>
   </dependency>
   ```

2. **修改默认密码**：生产环境部署时，务必通过配置文件修改默认的登录密码。

3. **数据库权限**：建议使用只读数据库账号连接，系统虽已强制设置只读模式，但最小权限原则是最佳实践。

4. **内存配置**：导出大数据量时，建议JVM堆内存设置为1GB以上：
   ```bash
   java -Xms512m -Xmx2048m -jar target/db-export-tool.jar
   ```

5. **磁盘空间**：导出文件存储在 `exports/` 目录，请确保磁盘空间充足。

6. **浏览器兼容**：推荐使用 Chrome、Firefox、Edge 等现代浏览器，不支持 IE 浏览器。

7. **并发连接数**：最大并发连接数不宜设置过高，建议根据数据库服务器性能设置为3-10之间。过高可能导致数据库连接数耗尽。

8. **文件编码**：SQL导出文件使用UTF-8编码，导入到数据库时请注意编码设置。

---

## 性能参考

| 场景 | 数据量 | 耗时 | 内存峰值 |
|------|--------|------|----------|
| 单表导出 | 10万行 | < 2分钟 | < 512MB |
| 单表导出 | 100万行 | < 5分钟 | < 1GB |
| 10表并行导出 | 各10万行 | 约2分钟 | < 1GB |

> 实际性能取决于数据库服务器性能、网络带宽和数据复杂度。

---

## 后续规划

### P1 - 高优先级
- **定时调度系统** - Cron表达式配置定时自动导出
- **定时任务管理** - 创建、编辑、删除、启停定时任务

### P2 - 中优先级
- **增量导出** - 基于上次导出时间戳的增量数据导出
- **数据脱敏** - 敏感字段自动脱敏（手机号、身份证等）
- **多数据库同时连接** - 支持同时管理多个数据库连接
- **执行历史记录** - 查看定时任务的历史执行记录

### P3 - 低优先级
- **导出预览** - 导出前预览前100条数据
- **导出统计分析** - 导出数据量统计、耗时分析
- **云存储对接** - 支持OSS/S3等云存储
- **邮件通知** - 导出完成后邮件通知
- **操作审计日志** - 记录所有操作行为
- **移动端适配** - 响应式布局优化
- **快捷键支持** - 常用操作快捷键
- **主题自定义** - 支持自定义配色方案

---

## 技术栈

| 组件 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 2.7.18 |
| 连接池 | HikariCP | 4.0.3 |
| Excel生成 | Apache POI | 5.2.5 |
| JSON处理 | Jackson | 内置 |
| 前端 | 原生HTML/CSS/JS | - |
| MySQL驱动 | mysql-connector-java | 8.0.33 |

---

## 许可证

本项目仅供企业内部使用。
