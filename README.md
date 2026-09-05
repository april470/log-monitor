# 接口日志监控告警系统

基于 Spring Boot 4 + AOP + Caffeine + 规则引擎 + AI 的接口级日志监控与智能告警平台，实现**日志自动采集 → P99 动态慢接口识别 → 规则/AI 双路异常分析 → 告警收敛 + 钉钉推送**全链路闭环。

---

## 技术栈

| 类别 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 语言 | Java | 17 | LTS 版本 |
| 框架 | Spring Boot | 4.1.0 | 最新一代 Spring Boot |
| 数据库 | MySQL | 8.0+ | 数据存储 |
| ORM | MyBatis-Plus | 3.5.16 | MyBatis 增强工具（Spring Boot 4 专用 starter） |
| AOP | Spring AOP + AspectJ | - | 无侵入式日志采集 |
| 缓存 | Caffeine | 3.1.8 | 本地缓存（比 Guava 快，首页 0 次 DB 访问） |
| 异步 | Spring @Async + 自定义线程池 | - | 双线程池分离写入/告警 |
| HTTP | Spring WebFlux (WebClient) | - | 异步调用 AI 接口 |
| 模板 | Thymeleaf | - | 服务端页面渲染 |
| 连接池 | HikariCP | - | Spring Boot 默认连接池（最大 20 / 最小 5） |
| 容器 | Docker + docker-compose | - | 多阶段构建，alpine JRE 镜像 ~80MB |

---

## 核心功能

### 1. 声明式日志采集（AOP 零侵入）
- 自定义 `@Log` 注解（支持指定慢阈值、是否记录参数/返回值）
- Spring AOP 环绕通知自动拦截所有 Controller 接口
- 采集字段：接口名、请求参数（自动脱敏）、返回数据（截断 1000 字符）、耗时、状态、错误信息、调用方 IP、TraceId

### 2. P99 动态慢接口识别
- 摒弃固定 2s 阈值，为**每个接口独立维护最近 100 次耗时的滑动窗口**
- `ConcurrentHashMap` + `Collections.synchronizedList` 保证多线程安全
- 实时计算 P99 耗时作为慢判定标准，适配不同接口的性能基线

### 3. 双线程池异步写入
- **日志写入池**（`logExecutor`）：核心 CPU×2 / 最大 CPU×4 / 队列 200 → `CallerRunsPolicy`（保证日志不丢，极端情况由调用线程兜底）
- **告警发送池**（`alarmExecutor`）：核心 2 / 最大 4 / 队列 50 → `DiscardPolicy`（告警非核心，避免阻塞主业务）

### 4. Caffeine 本地缓存
- 缓存 Dashboard 统计数据（成功数、错误数、慢请求数、超时数）
- 1 分钟过期，最大 10 个 Key
- 将首页查询从 **3 次 SQL 优化为 0 次 DB 访问**，响应时间从 ~200ms 降至 ~30ms

### 5. 规则引擎 + AI 双路分析
- **规则引擎**：内置 17 种常见 Java 异常规则（NPE、SQL 异常、连接超时、OOM、类加载异常等），支持 `EXACT / CONTAINS / REGEX` 三种匹配模式，每条规则附带根因分析 + 修复建议
- **AI 分析**：WebClient 异步调用大模型接口，返回根因 / 影响范围 / 修复建议 / 预防措施四段式分析
- **优雅降级**：AI 不可用时自动回退到规则引擎，保证任何场景下都有有效分析结果

### 6. 告警系统
- 慢接口检测（P99 动态阈值）
- 错误率监控（默认 10%，5 分钟滑动窗口）
- 三级告警：INFO / WARN / CRITICAL
- **5 分钟告警收敛**：同一级别 + 同一标题的告警在收敛期内只发送 1 次，避免告警风暴
- 钉钉 ActionCard 推送，附接口名、耗时、AI 分析结论、日志跳转链接

### 7. 敏感字段脱敏
- 黑名单：password、token、secret、key、phone、idcard、creditcard、email 等自动替换为 `******`
- 大小写不敏感匹配，覆盖各种命名风格
- 请求参数 + 返回值均处理

### 8. 分布式 TraceId
- 支持从 `X-Trace-Id` 请求头获取链路追踪 ID
- 未传入时自动生成 UUID（截取前 16 位，平衡唯一性与存储开销）

### 9. 可视化仪表盘
- Thymeleaf 模板渲染日志列表与详情页
- 统计数据：成功数、错误数、慢请求数、超时数、耗时趋势

---

## 项目结构

```
src/main/java/com/example/logmonitor/
├── aspect/
│   ├── Log.java            # 自定义 @Log 注解
│   └── LogAspect.java      # AOP 日志采集切面（核心：滑动窗口 P99 + 脱敏 + TraceId）
├── config/
│   ├── AsyncConfig.java            # 双线程池配置（CallerRunsPolicy / DiscardPolicy）
│   ├── GlobalExceptionHandler.java # @ControllerAdvice 全局异常处理
│   ├── MyBatisPlusConfig.java      # MyBatis-Plus 分页插件
│   └── WebClientConfig.java        # WebClient + RestTemplate 超时配置
├── controller/
│   ├── AlarmController.java  # 告警查询接口
│   ├── HomeController.java   # 首页仪表盘
│   ├── LogController.java    # 日志查询 + AI 分析
│   └── TestController.java   # 模拟异常的测试接口
├── dto/
│   ├── LogCreateDTO.java     # 日志创建入参
│   └── LogQueryDTO.java      # 日志查询参数
├── entity/
│   └── InterfaceLog.java     # 接口日志实体
├── enums/
│   ├── AlarmLevelEnum.java   # INFO / WARN / CRITICAL
│   └── LogStatusEnum.java    # SUCCESS / ERROR / TIMEOUT
├── mapper/
│   └── InterfaceLogMapper.java
├── service/
│   ├── AIService.java            # AI 分析服务（含降级 + 重试）
│   ├── AlarmService.java         # 告警服务（5min 收敛窗口 + 钉钉推送）
│   ├── LogService.java           # 日志服务（Caffeine 缓存）
│   └── rule/
│       └── RuleEngine.java       # 17 条规则引擎（EXACT/CONTAINS/REGEX）
├── vo/
│   └── LogDetailVO.java
└── LogMonitorApplication.java
```

---

## 快速开始

### 环境要求
- JDK 17+
- Maven 3.8+
- Docker 20+（可选，推荐方式）

### 方式一：Docker 一键启动（推荐）

```bash
git clone https://github.com/april470/log-monitor.git
cd log-monitor
docker-compose up -d
```

自动完成：MySQL 8.0 启动 → 自动建表 + 注入模拟数据 → 应用容器启动（健康检查依赖 MySQL）

访问：http://localhost:8080/logs

### 方式二：本地手动启动

#### 1. 创建数据库
```sql
CREATE DATABASE IF NOT EXISTS log_monitor
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;
```

#### 2. 配置环境变量（可选，未配置则使用 application.yml 中的默认值）

| 环境变量 | 默认值 | 说明 |
|----------|--------|------|
| `DB_URL` | `jdbc:mysql://localhost:3306/log_monitor?...` | MySQL 连接 URL |
| `DB_USERNAME` | `root` | 数据库用户名 |
| `DB_PASSWORD` | 空 | 数据库密码 |
| `AI_API_KEY` | 空 | AI 大模型 API Key（不配则只用规则引擎） |
| `AI_API_URL` | 阿里云 DashScope | AI 接口地址 |
| `AI_MODEL` | `qwen3.7-plus` | AI 模型名 |
| `DINGTALK_WEBHOOK` | 空 | 钉钉机器人 Webhook（不配则不发告警） |
| `DASHBOARD_URL` | `http://localhost:8080/logs` | 告警消息中的跳转链接 |

#### 3. 运行
```bash
mvn spring-boot:run
# 或
mvn clean package
java -jar target/log-monitor-0.0.1-SNAPSHOT.jar
```

#### 4. 访问
- 日志仪表盘：http://localhost:8080/logs
- 模拟测试接口：http://localhost:8080/test/simulate
- 手动触发告警检查：http://localhost:8080/alarm/check

---

## 核心设计

### AOP 日志采集流程

```
客户端请求 → Controller 方法
                    │
                    ▼
            LogAspect.logAround()
                    │
            ┌───────┼───────┐
            │       │       │
            ▼       ▼       ▼
      记录开始时间  获取TraceId  脱敏参数
            │       │       │
            └───────┼───────┘
                    │
                    ▼
            joinPoint.proceed()
                    │
            ┌───────┴───────┐
            │               │
         成功 ✅          异常 ❌
            │               │
     记录耗时/返回值    记录耗时/错误信息
            │               │
            └───────┬───────┘
                    │
                    ▼
     异步线程池 → 写入 MySQL
                    │
                    ▼
     更新该接口 P99 滑动窗口
```

### P99 滑动窗口实现

```java
// 每接口独立维护 100 次耗时样本
private final ConcurrentHashMap<String, List<Integer>> latencySamples = new ConcurrentHashMap<>();

// 采完一次耗时，追加到滑动窗口
List<Integer> samples = latencySamples.computeIfAbsent(key, k ->
    Collections.synchronizedList(new ArrayList<>()));
samples.add(costTime);
if (samples.size() > 100) samples.remove(0); // FIFO 滑窗

// 实时算 P99
List<Integer> sorted = new ArrayList<>(samples);
sorted.sort(Integer::compareTo);
int p99Index = (int) (sorted.size() * 0.99);
int p99Threshold = sorted.get(Math.min(p99Index, sorted.size() - 1));
```

### 降级链路

```
用户点击「AI 分析」
        │
        ▼
  AI_API_KEY 已配置？
   ├── 是 → WebClient 异步调用 AI（线性退避重试）
   │        ├── 成功 → 返回 AI 四段式分析
   │        └── 失败 → 降级规则引擎
   └── 否 → 直接规则引擎
                │
                ▼
          匹配 17 条规则（EXACT/CONTAINS/REGEX）
           ├── 命中 → 返回规则分析 + 修复建议
           └── 未命中 → 返回通用排查建议
```

### 告警收敛机制

```
AlarmService.sendAlarm(level, title, content)
        │
        ▼
  检查「level + title」组合是否在收敛窗口（5min）内已发送？
   ├── 是 → 跳过（避免重复轰炸）
   └── 否 → 发钉钉 ActionCard + 记录到收敛窗口
```

### Docker 多阶段构建

```
阶段 1: maven:3.9-eclipse-temurin-17
  ├── COPY pom.xml → RUN mvn dependency:go-offline  ← 利用 Docker 缓存层
  ├── COPY src → RUN mvn clean package -DskipTests
  └── 产物：target/log-monitor.jar

阶段 2: eclipse-temurin:21-jre-alpine (~80MB)
  ├── FROM 阶段 1 的 jar
  ├── 设置时区 Asia/Shanghai
  └── java -jar -XX:+UseContainerSupport app.jar
```

---

## 数据库表结构

### interface_log

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INT | 主键，自增 |
| trace_id | VARCHAR(64) | 分布式链路追踪 ID |
| interface_name | VARCHAR(255) | 接口名（类名.方法名） |
| method | VARCHAR(100) | HTTP 方法 |
| request_params | TEXT | 请求参数（已脱敏） |
| response_data | TEXT | 返回数据（截断 1000 字符） |
| cost_time | INT | 耗时（ms） |
| status | VARCHAR(20) | SUCCESS / ERROR / TIMEOUT |
| error_msg | VARCHAR(1000) | 错误信息 |
| create_time | DATETIME | 创建时间 |
| caller_ip | VARCHAR(50) | 调用方 IP |
| env | VARCHAR(20) | 环境标识 |

首次启动时 Spring Boot 自动执行 `schema.sql`（建表）+ `data.sql`（注入模拟数据）。

---

## 亮点总结

| 亮点 | 实现方式 | 解决的问题 |
|------|----------|------------|
| 零侵入采集 | Spring AOP 环绕通知 + 自定义 @Log 注解 | 业务代码无需改动即可接入 |
| P99 动态慢阈值 | 每接口独立维护 100 次耗时滑动窗口 | 不同接口有不同性能基线，一刀切阈值不合理 |
| 双线程池拒绝策略选型 | 写入 CallerRunsPolicy（不丢数据）/ 告警 DiscardPolicy（防阻塞） | 统一线程池无法兼顾可靠性与吞吐量 |
| Caffeine 缓存 | 首页统计 1min 过期 / 10 Key 上限 | 3 次 SQL → 0 次 DB，响应 200ms → 30ms |
| 规则 + AI 双路分析 | 17 条规则引擎 + WebClient 异步调用 | AI 不可用时自动降级，保证始终有分析结果 |
| 告警收敛 | 5 分钟窗口去重 | 避免告警风暴轰炸钉钉 |
| 敏感配置外部化 | Spring 占位符 + 环境变量注入 | 无硬编码密钥，支持多环境隔离 |
| 生产级脱敏 | 黑名单 + 大小写不敏感 + 长度截断 | 防止敏感信息泄漏和日志爆炸 |
| Docker 多阶段构建 | Maven 依赖缓存层 + alpine JRE ~80MB | 构建快、镜像小、部署简单 |
| HikariCP 调优 | 最大 20 / 最小 5 / 连接超时 20s | 避免连接池耗尽 |

---

## License

MIT License