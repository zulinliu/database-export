# 数据库导出工具 V2.0

数据库导出工具 V2.0 是一款基于 Spring Boot 开发的通用数据库导出系统，支持达梦数据库和 MySQL 的数据导出，提供三种导出模式（SQL 查询、全表导出、模板导出），可生成 Excel 和 SQL 格式的导出文件，适用于企业级数据备份、数据分析和数据迁移场景。

## 核心功能

### 数据导出

- **SQL 查询导出**：用户自定义 SQL 语句，灵活筛选数据
- **全表导出**：选择数据库表，一键导出全部数据
- **模板导出**：使用预定义的导出模板，快速执行标准化导出

### 数据过滤策略

- 条件过滤：支持等于、不等于、包含、范围等条件组合
- 分页预览：导出前预览数据，防止误导出
- 字段选择：自由选择需要导出的列

### 模板管理

- 创建、编辑、删除导出模板
- 模板参数化：支持变量替换
- 模板分类管理

### 导出格式

- Excel 格式（.xlsx）：支持大数据量分 Sheet 导出
- SQL 格式（.sql）：生成可执行的 INSERT 语句

### 导出进度监控

- 实时进度条显示
- 后台异步导出，不阻塞操作
- 导出完成后自动通知

### 连接池管理

- HikariCP 高性能连接池
- 多数据源配置支持
- 连接健康检测

### 主题切换

- 亮色主题 / 暗色主题
- 一键切换，实时生效
- 记忆用户偏好

## 技术架构

| 组件 | 技术选型 | 说明 |
|------|----------|------|
| 基础框架 | Spring Boot 2.7.x | 核心框架，提供 Web 服务 |
| 数据库连接池 | HikariCP | 高性能 JDBC 连接池 |
| Excel 处理 | Apache POI | 处理 Excel 文件生成 |
| 前端技术 | 原生 HTML/CSS/JS | 无框架依赖，轻量高效 |
| 数据库支持 | 达梦7/8、MySQL | 主流国产数据库兼容 |
| 构建工具 | Maven | 项目构建和依赖管理 |

## 项目结构

```
db-export-tool/
├── pom.xml                      # Maven 配置文件
├── README.md                    # 项目文档
├── scripts/                     # 脚本目录
│   ├── install.sh               # Linux 安装脚本
│   ├── start.sh                 # Linux 启动脚本
│   ├── install.bat              # Windows 安装脚本
│   └── start.bat                # Windows 启动脚本
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── dbexport/
│       │           ├── DbExportApplication.java    # 应用入口
│       │           ├── config/                     # 配置类
│       │           │   └── DataSourceConfig.java
│       │           ├── controller/                 # 控制器
│       │           │   ├── ExportController.java
│       │           │   └── TemplateController.java
│       │           ├── service/                   # 业务逻辑
│       │           │   ├── ExportService.java
│       │           │   └── TemplateService.java
│       │           ├── dao/                       # 数据访问
│       │           │   └── DataSourceDao.java
│       │           ├── model/                     # 数据模型
│       │           │   ├── ExportTask.java
│       │           │   └── ExportTemplate.java
│       │           └── util/                      # 工具类
│       │               └── ExcelUtil.java
│       └── resources/
│           ├── application.yml        # 应用配置
│           ├── static/                # 静态资源
│           │   ├── css/
│           │   ├── js/
│           │   └── index.html
│           └── templates/             # 页面模板
│               └── export.html
├── exports/                     # 导出文件目录
├── templates/                   # 导出模板目录
├── logs/                        # 日志目录
└── temp/                        # 临时文件目录
```

## 环境要求

### 运行环境

- JDK 8 或更高版本
- Maven 3.6 或更高版本

### 支持的数据库

- 达梦数据库 7.x / 8.x
- MySQL 5.7 / 8.x

### 磁盘空间

- 至少 500MB 可用空间
- 根据导出数据量预估存储需求

## 安装部署

### Linux 环境

#### 步骤 1：准备环境

确保已安装 JDK 和 Maven：

```bash
java -version
mvn -version
```

#### 步骤 2：安装

```bash
cd /path/to/db-export-tool
chmod +x scripts/install.sh
./scripts/install.sh
```

安装脚本将执行以下操作：
- 检查 Java 和 Maven 环境
- 创建必要的目录结构
- 编译项目生成 JAR 文件

#### 步骤 3：启动服务

```bash
./scripts/start.sh
```

#### 步骤 4：访问服务

打开浏览器访问：`http://localhost:8080`

默认登录账号：`admin` / `123456`

### Windows 环境

#### 步骤 1：准备环境

确保已安装 JDK 和 Maven，并在命令行中可用。

#### 步骤 2：安装

双击运行 `scripts\install.bat`，或以管理员身份打开命令提示符执行：

```cmd
cd /d C:\path\to\db-export-tool
scripts\install.bat
```

#### 步骤 3：启动服务

双击运行 `scripts\start.bat`，或以管理员身份打开命令提示符执行：

```cmd
scripts\start.bat
```

#### 步骤 4：访问服务

打开浏览器访问：`http://localhost:8080`

默认登录账号：`admin` / `123456`

## 使用说明

### 登录系统

1. 访问 `http://localhost:8080`
2. 输入默认账号 `admin` 和密码 `123456`
3. 点击登录按钮进入主界面

### 三种导出模式

#### 模式一：SQL 查询导出

1. 在左侧菜单选择「SQL 导出」
2. 在编辑器中输入 SQL 查询语句
3. 点击「预览」查看查询结果
4. 选择导出格式（Excel/SQL）
5. 点击「导出」开始导出任务
6. 导出完成后自动下载文件

#### 模式二：全表导出

1. 在左侧菜单选择「全表导出」
2. 从下拉列表中选择目标数据库表
3. 系统自动加载表结构和数据预览
4. 选择需要导出的列
5. 设置导出格式和分页参数
6. 点击「导出」开始导出任务

#### 模式三：模板导出

1. 在左侧菜单选择「模板导出」
2. 从模板列表中选择目标模板
3. 根据模板要求填写参数值
4. 点击「执行」使用模板导出数据
5. 导出完成后自动下载文件

### 过滤策略

在导出设置页面可以配置以下过滤条件：

| 条件类型 | 说明 | 示例 |
|----------|------|------|
| 等于 | 字段值完全匹配 | status = 'active' |
| 不等于 | 字段值不匹配 | status != 'deleted' |
| 包含 | 字段值包含字符串 | name LIKE '%张%' |
| 范围 | 字段值在区间内 | age BETWEEN 18 AND 60 |
| 开头 | 字段值以字符串开头 | code LIKE 'EMP%' |
| 为空 | 字段值为空 | email IS NULL |

### 模板管理

1. 进入「模板管理」页面
2. 点击「新建模板」创建模板
3. 填写模板名称、描述和 SQL 语句
4. 使用 `${param}` 语法定义参数占位符
5. 保存模板后可在导出时调用

### 导出记录

所有导出记录保存在「导出记录」页面：
- 查看历史导出任务
- 下载之前导出的文件
- 删除不需要的导出记录

## 配置文件详解

配置文件位于 `src/main/resources/application.yml`：

```yaml
spring:
  application:
    name: db-export-tool  # 应用名称
  
  datasource:
    driver-class-name: dm.jdbc.driver.DmDriver  # 达梦驱动
    url: jdbc:dm://localhost:5236  # 数据库连接地址
    username: SYSDBA  # 数据库用户名
    password: SYSDBA  # 数据库密码
    hikari:
      maximum-pool-size: 10  # 最大连接数
      minimum-idle: 5  # 最小空闲连接
      connection-timeout: 30000  # 连接超时时间（毫秒）
      idle-timeout: 600000  # 空闲超时时间（毫秒）
      max-lifetime: 1800000  # 最大生命周期（毫秒）
      pool-name: DbExportHikariCP  # 连接池名称
  
  servlet:
    multipart:
      enabled: true  # 启用文件上传
      max-file-size: 100MB  # 单个文件最大大小
      max-request-size: 200MB  # 请求最大大小

server:
  port: 8080  # 服务端口
  address: 0.0.0.0  # 监听地址

export:
  path: ./exports  # 导出文件存储路径
  temp: ./temp  # 临时文件路径
  max-rows: 1000000  # 单次导出最大行数
  timeout: 3600  # 导出超时时间（秒）
  chunk-size: 50000  # Excel 分片大小

logging:
  level:
    root: INFO  # 日志级别
    com.dbexport: DEBUG  # 应用包日志级别
  file:
    name: ./logs/application.log  # 日志文件路径
    max-size: 10MB  # 单个日志文件大小
    max-history: 30  # 日志保留天数
```

### 配置项说明

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| spring.datasource.driver-class-name | dm.jdbc.driver.DmDriver | 数据库驱动类名 |
| spring.datasource.url | jdbc:dm://localhost:5236 | JDBC 连接 URL |
| spring.datasource.username | SYSDBA | 数据库用户名 |
| spring.datasource.password | - | 数据库密码 |
| spring.datasource.hikari.maximum-pool-size | 10 | 连接池最大连接数 |
| spring.datasource.hikari.minimum-idle | 5 | 连接池最小空闲连接 |
| spring.datasource.hikari.connection-timeout | 30000 | 获取连接超时时间（毫秒） |
| server.port | 8080 | HTTP 服务端口 |
| export.path | ./exports | 导出文件保存目录 |
| export.max-rows | 1000000 | 单次导出最大记录数 |
| export.chunk-size | 50000 | Excel 每个 Sheet 的行数 |

### 切换数据库

如需使用 MySQL，修改配置如下：

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/your_database?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: your_password
```

## 注意事项

### 达梦数据库驱动

- 首次使用需确保达梦数据库驱动 JAR 文件已放置在正确位置
- 驱动文件路径：`src/main/resources/lib/dm-jdbc-driver.jar`
- 如使用 Maven 构建，驱动依赖会自动下载

### 连接池配置

- 根据服务器硬件配置调整连接池参数
- 高并发场景建议 `maximum-pool-size` 设置为 CPU 核心数的 2-3 倍
- 生产环境建议设置合理的 `connection-timeout` 防止连接泄漏

### 数据安全

- 生产环境务必修改默认登录密码
- 敏感配置项（如数据库密码）建议使用环境变量或配置中心管理
- 定期清理 `exports` 和 `logs` 目录释放磁盘空间
- 导出文件包含敏感数据，操作完毕后及时下载并删除服务器端文件

### 性能优化

- 大数据量导出建议使用分页导出，避免内存溢出
- 频繁导出的表建议建立合适的索引
- 导出过程中避免执行其他 DDL 操作
- 监控服务器 CPU 和内存使用情况

### 常见问题

**Q: 启动失败，提示端口被占用？**
A: 修改 `application.yml` 中的 `server.port` 为其他端口，如 8081。

**Q: 导出文件时提示内存不足？**
A: 启动脚本中添加 JVM 参数 `-Xmx1024m` 增加堆内存大小。

**Q: 连接达梦数据库失败？**
A: 检查达梦数据库服务是否启动，确认 IP、端口、用户名密码配置正确。

## 后续规划

- **定时调度**：支持配置定时导出任务，自动执行数据导出
- **增量导出**：支持基于时间戳或自增 ID 的增量数据导出
- **数据脱敏**：内置敏感字段（如手机号、身份证）自动脱敏功能
- **多数据库支持**：扩展支持 PostgreSQL、Oracle、SQL Server 等数据库
- **云存储集成**：支持将导出文件直接上传至 S3、OSS、七牛云等对象存储
- **邮件通知**：导出完成后自动发送邮件通知
- **操作审计**：记录所有导出操作，支持审计查询和报表导出

## 许可证

本项目仅供学习和企业内部使用，如需商业授权请联系开发者。
