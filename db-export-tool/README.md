# 数据库导出工具 V2.0

数据库导出工具 V2.0 是一款面向企业内网的数据库数据导出系统，专注于解决生产库到开发/测试环境的数据同步需求。系统提供灵活的数据导出能力和可复用的任务模板管理，支持高效、安全地导出大规模数据。

## 核心功能

### 数据导出

- **自定义表名导出**：手动输入表名列表，支持顿号、逗号、换行分隔
- **表勾选导出**：从数据库表列表中选择，支持搜索、全选、反选
- **自定义SQL导出**：输入SELECT语句，每行一条，Sheet名自动提取表名

### 数据过滤策略

- **时间范围过滤**：支持多个字段（顿号分隔），如 `DATE、DATA_TIME`，格式 `yyyy-MM-dd`
- **自定义字段过滤**：支持等于/包含/范围三种方式，自动检查表中字段是否存在
- **策略组合**：两种过滤策略可同时启用，优先级：先应用时间过滤，再应用字段过滤

### 模板管理

- 模板保存/加载/删除
- 模板搜索（按名称模糊匹配）
- 批量删除
- 模板批量导入/导出（ZIP压缩格式）

### 导出格式

- Excel (.xlsx)：单文件多Sheet，每个表一个Sheet，SXSSF流式处理防OOM
- INSERT SQL (.sql)：批量插入格式，默认1000条/批，Oracle风格INSERT ALL
- 同时导出：同时生成两种格式，打包到同一压缩包

### 导出进度监控

- 实时进度条显示
- 详细进度统计（当前表/已完成表/已导出行/总数据量）
- 时间估算（已用时间/预计剩余时间）
- 四级日志实时查看（INFO/SUCCESS/WARNING/ERROR）
- 支持中途取消导出

### 连接池管理

- HikariCP高性能连接池
- 连接池状态监控（active/total/idle/waiting连接数）
- 最大并发连接数可配置
- 达梦7/8 + MySQL 双数据库支持

### 系统功能

- 用户登录认证（Session管理，30分钟超时）
- 亮色/暗色主题切换
- 导出记录管理（下载/删除/过期清理）

## 技术架构

| 组件 | 技术选型 | 说明 |
|------|----------|------|
| 基础框架 | Spring Boot 2.7.x | 核心框架，提供Web服务 |
| 数据库连接池 | HikariCP | 高性能JDBC连接池 |
| Excel处理 | Apache POI 5.2.5 | SXSSF流式Excel，防内存溢出 |
| 前端技术 | 原生HTML/CSS/JS | 无框架依赖，轻量高效 |
| 数据库支持 | 达梦7/8、MySQL | 主流国产数据库兼容 |
| 构建工具 | Maven 3.6+ | 项目构建和依赖管理 |

## 项目结构

```
db-export-tool/
├── pom.xml                      # Maven配置文件
├── README.md                    # 项目文档
├── scripts/                     # 脚本目录
│   ├── install.sh               # Linux安装脚本
│   ├── start.sh                 # Linux启动脚本
│   ├── install.bat              # Windows安装脚本
│   └── start.bat                # Windows启动脚本
├── src/
│   ├── main/
│   │   ├── java/com/dbexport/
│   │   │   ├── DbExportApplication.java          # 应用入口
│   │   │   ├── config/                           # 配置类
│   │   │   │   ├── AsyncConfig.java              # 异步线程池配置
│   │   │   │   ├── HikariPoolManager.java        # HikariCP连接池管理器
│   │   │   │   ├── SecurityConfig.java           # 安全配置（Session拦截）
│   │   │   │   └── WebConfig.java                # Web配置（CORS/Filter）
│   │   │   ├── controller/                       # 控制器
│   │   │   │   └── ExportController.java         # 主控制器（22个端点）
│   │   │   ├── service/                          # 业务逻辑
│   │   │   │   ├── ExportServiceImpl.java        # 导出服务
│   │   │   │   └── TemplateService.java          # 模板服务
│   │   │   ├── model/                            # 数据模型
│   │   │   │   ├── DatabaseInfo.java             # 数据库连接信息
│   │   │   │   ├── ExportConfig.java             # 导出配置
│   │   │   │   ├── ExportProgress.java           # 导出进度
│   │   │   │   ├── FieldFilterConfig.java        # 字段过滤配置
│   │   │   │   ├── Template.java                 # 模板
│   │   │   │   └── ApiResponse.java              # API响应封装
│   │   │   └── util/                             # 工具类
│   │   │       ├── SqlValidator.java             # SQL验证防注入
│   │   │       ├── FileUtil.java                 # 文件工具
│   │   │       └── JsonUtil.java                 # JSON工具
│   │   └── resources/
│   │       ├── application.yml                   # 应用配置
│   │       ├── static/                           # 静态资源
│   │       │   ├── css/style.css                 # 主样式（深色/浅色主题）
│   │       │   └── js/main.js                    # 前端交互逻辑
│   │       └── templates/                        # 页面模板
│   │           ├── login.html                    # 登录页
│   │           └── index.html                    # 主页面
│   └── test/
│       └── java/com/dbexport/                    # 单元测试（77个测试用例）
│           ├── DbExportApplicationTests.java
│           ├── model/
│           ├── util/
│           ├── config/
│           ├── controller/
│           └── service/
├── exports/                     # 导出文件目录
├── templates/                   # 导出模板目录
├── logs/                        # 日志目录
└── temp/                        # 临时文件目录
```

## 环境要求

- **JDK**: 1.8 或更高
- **Maven**: 3.6 或更高
- **浏览器**: Chrome/Edge/Firefox（推荐最新版）
- **目标数据库**: 达梦7/8 或 MySQL 5.7+

## 安装部署

### Linux 系统

```bash
# 1. 安装项目
cd scripts
chmod +x install.sh
./install.sh

# 2. 启动服务
chmod +x start.sh
./start.sh

# 服务启动后，访问 http://localhost:8080
```

### Windows 系统

```batch
# 1. 安装项目
cd scripts
install.bat

# 2. 启动服务
start.bat

# 服务启动后，访问 http://localhost:8080
```

### 手动运行

```bash
# 编译打包
mvn clean package -DskipTests

# 启动服务
java -Xms256m -Xmx512m -jar target/db-export-tool.jar
```

### 默认账号

- **用户名**: admin
- **密码**: 123456

## 使用说明

### 1. 登录系统

访问 http://localhost:8080，输入账号密码登录。登录成功后进入主页面。

### 2. 连接数据库

在"数据导出"Tab中，填写数据库连接信息：

- 数据库类型：达梦 DM 或 MySQL
- 主机地址：数据库服务器IP/域名
- 端口：达梦默认 5236，MySQL默认 3306
- 数据库名：目标数据库名称
- 用户名/密码：数据库账号密码

点击"测试连接"验证连接是否正常，确认无误后点击"连接数据库"。

### 3. 选择导出模式

#### 自定义表名导出

在"自定义表名"输入框中输入表名，支持：
- 顿号分隔：`TABLE1、TABLE2、TABLE3`
- 逗号分隔：`TABLE1,TABLE2,TABLE3`
- 换行分隔：每行一个表名

#### 表勾选导出

切换到"表勾选"Tab，系统自动加载数据库表列表：
- 支持搜索表名
- 支持全选/反选
- 显示每个表的记录数

#### 自定义SQL导出

切换到"自定义SQL"Tab，输入SELECT语句，每行一条：
```sql
SELECT * FROM TABLE1 WHERE STATUS = 'ACTIVE'
SELECT ID, NAME FROM TABLE2
```
点击"验证SQL"可检查SQL语法是否安全。

### 4. 配置数据过滤（可选）

#### 时间范围过滤

勾选"时间范围过滤"，配置：
- 字段名：多个字段用顿号分隔，如 `DATE、DATA_TIME`
- 开始/结束日期：`yyyy-MM-dd` 格式

#### 自定义字段过滤

勾选"自定义字段过滤"，配置：
- 字段名：如 `CASE_ID`
- 过滤方式：等于/包含/范围
- 参数值：
  - 等于：单个值，如 `111`
  - 包含：逗号分隔，如 `'111','222','333'`
  - 范围：连字符分隔，如 `100-200`

### 5. 配置导出选项

- 导出格式：勾选Excel和/或SQL
- 最大并发连接数：1-10之间，默认为5

### 6. 保存为模板（可选）

点击"保存为模板"，输入模板名称和描述，配置将保存为模板供后续复用。

### 7. 开始导出

点击"🚀 开始导出"，系统进入导出进度监控页面，显示：
- 总进度条
- 当前导出表
- 已完成表数/总表数
- 已导出数据行数
- 已用时间/预计剩余时间
- 实时日志

导出完成后，自动打包为ZIP文件，可在"导出记录"Tab中下载。

### 8. 模板管理

在"模板管理"Tab中：
- 查看所有模板
- 加载模板（自动填充导出配置）
- 删除模板
- 批量导出模板为ZIP
- 导入ZIP模板文件

### 9. 导出记录

在"导出记录"Tab中：
- 查看历史导出记录
- 下载ZIP文件
- 删除记录
- 清理过期记录（按天数）

## 配置文件详解

`application.yml` 配置项：

```yaml
spring:
  application:
    name: db-export-tool
  servlet:
    multipart:
      max-file-size: 100MB      # 上传文件最大大小
      max-request-size: 100MB
  thymeleaf:
    prefix: classpath:/templates/
    suffix: .html
    cache: false

server:
  port: 8080                     # 服务端口
  servlet:
    session:
      timeout: 1800s            # Session超时时间（30分钟）

app:
  login:
    username: admin              # 默认登录用户名
    password: "123456"          # 默认登录密码
  export:
    thread:
      core: 4                   # 导出线程池核心线程数
      max: 8                    # 导出线程池最大线程数
    batch:
      size: 1000                # SQL批量插入大小
    fetch-size: 1000            # ResultSet FetchSize
    storage:
      path: ./exports           # 导出文件存储路径
      temp: ./temp              # 临时文件路径
    timeout: 3600               # 导出超时时间（秒）
  template:
    storage:
      path: ./templates         # 模板存储路径

logging:
  level:
    com.dbexport: DEBUG
  file:
    path: ./logs                # 日志存储路径
    name: db-export-tool.log    # 日志文件名
```

## 注意事项

### 达梦数据库驱动

当前项目默认包含MySQL驱动，如需连接达梦数据库，请将达梦驱动JAR（`DmJdbcDriver18.jar` 或 `DmJdbcDriver-7.0.12.jar`）放到项目目录，并在 `pom.xml` 中添加依赖（或直接放到运行时classpath）：

```xml
<dependency>
    <groupId>com.dameng</groupId>
    <artifactId>DmJdbcDriver</artifactId>
    <version>8.1.2.192</version>
    <scope>system</scope>
    <systemPath>${project.basedir}/lib/DmJdbcDriver18.jar</systemPath>
</dependency>
```

### 性能注意事项

- 大数据量导出时，建议使用较低的并发连接数（2-4）
- 大表导出会自动启用流式处理，避免内存溢出
- 导出时间过长时，可以随时取消，不影响已完成部分

### 安全注意事项

- 禁止在生产环境使用默认密码
- 建议在内网部署，不暴露到公网
- 自定义SQL模式仅允许SELECT语句，系统会自动验证并拦截危险语句
- 导出的文件包含数据，请注意保管
- 建议定期清理exports目录下的过期文件

### 常见问题

**Q: 连接达梦数据库失败？**

A: 检查端口是否正确（默认5236），驱动是否在classpath中，网络是否通畅。

**Q: 导出Excel时内存溢出？**

A: 系统已使用SXSSF流式处理，理论上不会OOM。如仍出现，减少并发连接数，或分批导出。

**Q: 时间过滤字段不存在？**

A: 系统会自动检查表中是否存在该字段，不存在时该策略不生效，导出全量数据。

**Q: 如何备份模板？**

A: 使用"导出选中"功能，将选中的模板导出为ZIP文件。

## 后续规划

- [ ] 定时导出调度：支持按时间自动执行导出
- [ ] 增量导出：基于时间戳或增量标识只导出新增/变更数据
- [ ] 数据脱敏：导出时对敏感字段进行脱敏处理
- [ ] 多数据源配置：预配置多个数据源，快速切换
- [ ] 云存储：支持直接上传到阿里云OSS、腾讯云COS等
- [ ] 邮件通知：导出完成后发送邮件通知
- [ ] 操作审计：记录所有用户操作和导出日志
- [ ] 权限管理：细粒度权限控制，不同用户不同权限

---

**版本历史**:
- V2.0: 2025年5月，全新架构，支持双数据库，优化性能
