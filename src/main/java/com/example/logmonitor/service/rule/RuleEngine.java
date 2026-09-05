package com.example.logmonitor.service.rule;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Component
public class RuleEngine {

    private final List<ErrorRule> rules;

    public RuleEngine() {
        this.rules = initRules();
    }

    public String matchRule(String interfaceName, String errorMsg) {
        if (errorMsg == null || errorMsg.isEmpty()) {
            return null;
        }

        for (ErrorRule rule : rules) {
            if (match(rule, errorMsg)) {
                return formatResult(rule, interfaceName);
            }
        }
        return null;
    }

    private boolean match(ErrorRule rule, String errorMsg) {
        switch (rule.matchType) {
            case EXACT:
                return errorMsg.equalsIgnoreCase(rule.pattern);
            case CONTAINS:
                return errorMsg.toLowerCase().contains(rule.pattern.toLowerCase());
            case REGEX:
                return Pattern.compile(rule.pattern, Pattern.CASE_INSENSITIVE).matcher(errorMsg).find();
            default:
                return false;
        }
    }

    private String formatResult(ErrorRule rule, String interfaceName) {
        StringBuilder sb = new StringBuilder();
        sb.append("【匹配规则】").append(rule.ruleName).append("\n\n");
        sb.append("【错误类型】").append(rule.errorType).append("\n\n");
        sb.append("【原因分析】\n").append(rule.analysis).append("\n\n");
        sb.append("【修复建议】");
        for (int i = 0; i < rule.suggestions.size(); i++) {
            sb.append("\n").append(i + 1).append(". ").append(rule.suggestions.get(i));
        }
        return sb.toString();
    }

    private List<ErrorRule> initRules() {
        List<ErrorRule> list = new ArrayList<>();

        list.add(new ErrorRule(
                "NPE检测",
                MatchType.CONTAINS,
                "NullPointerException",
                "空指针异常",
                "调用了null对象的方法或访问了null对象的属性。",
                Arrays.asList(
                        "检查报错行及其上游对象是否为null，加null判断或使用Optional",
                        "使用Objects.requireNonNull()在方法入口做参数校验",
                        "可能是链式调用中某个中间对象为null，建议逐步排查"
                )
        ));

        list.add(new ErrorRule(
                "参数异常检测",
                MatchType.CONTAINS,
                "IllegalArgumentException",
                "参数非法异常",
                "方法接收到了不合法或不符合预期的参数。",
                Arrays.asList(
                        "检查调用该接口时传入的参数是否符合要求",
                        "查看Controller层的参数校验注解是否生效",
                        "在Service层入口加参数校验，提前拦截非法请求"
                )
        ));

        list.add(new ErrorRule(
                "SQL异常检测",
                MatchType.CONTAINS,
                "SQLException",
                "数据库操作异常",
                "数据库层面的错误，可能是SQL语法错误、约束冲突、连接问题等。",
                Arrays.asList(
                        "检查SQL语句语法是否正确，表名、字段名是否拼写正确",
                        "检查数据库连接配置和连接池状态",
                        "如果是约束冲突，检查业务逻辑是否重复提交"
                )
        ));

        list.add(new ErrorRule(
                "数据库约束冲突",
                MatchType.CONTAINS,
                "Duplicate entry",
                "主键/唯一键冲突",
                "插入数据时违反了唯一性约束。",
                Arrays.asList(
                        "检查是否存在重复提交",
                        "在Service层加分布式锁或唯一校验",
                        "考虑用INSERT ON DUPLICATE KEY UPDATE代替直接INSERT"
                )
        ));

        list.add(new ErrorRule(
                "IO异常检测",
                MatchType.CONTAINS,
                "IOException",
                "IO操作异常",
                "文件读写、网络通信等IO操作失败。",
                Arrays.asList(
                        "检查文件路径是否正确，文件是否存在",
                        "检查文件/目录的读写权限",
                        "如果是网络IO，检查远端服务是否正常"
                )
        ));

        list.add(new ErrorRule(
                "连接异常检测",
                MatchType.CONTAINS,
                "ConnectException",
                "网络连接异常",
                "无法连接到目标服务器。",
                Arrays.asList(
                        "确认目标服务是否正常运行",
                        "检查IP地址和端口配置是否正确",
                        "检查防火墙/安全组是否放行了对应端口"
                )
        ));

        list.add(new ErrorRule(
                "超时异常检测",
                MatchType.REGEX,
                "(?i)(timeout|SocketTimeoutException|ReadTimeout)",
                "操作超时",
                "操作在规定时间内未完成。",
                Arrays.asList(
                        "分析慢查询/慢接口，定位性能瓶颈",
                        "适当增大超时时间，但要找到根本原因",
                        "加熔断降级机制，超时后快速失败"
                )
        ));

        list.add(new ErrorRule(
                "内存溢出检测",
                MatchType.CONTAINS,
                "OutOfMemoryError",
                "内存溢出",
                "JVM堆内存不足。",
                Arrays.asList(
                        "使用jmap -dump查看堆内存快照，分析大对象",
                        "检查是否存在内存泄漏",
                        "适当增大JVM堆内存（-Xmx）"
                )
        ));

        list.add(new ErrorRule(
                "栈溢出检测",
                MatchType.CONTAINS,
                "StackOverflowError",
                "栈溢出",
                "方法调用层级过深，超出了JVM栈空间。",
                Arrays.asList(
                        "检查是否存在无限递归或循环调用",
                        "将递归改为迭代，或加递归深度限制",
                        "如果是数据量过大导致，考虑分批处理"
                )
        ));

        list.add(new ErrorRule(
                "类找不到检测",
                MatchType.CONTAINS,
                "ClassNotFoundException",
                "类加载异常",
                "运行时找不到某个类。",
                Arrays.asList(
                        "检查pom.xml中是否缺少相关依赖",
                        "检查类的全限定名是否正确",
                        "执行mvn clean install重新编译"
                )
        ));

        list.add(new ErrorRule(
                "方法找不到",
                MatchType.CONTAINS,
                "NoSuchMethodError",
                "方法签名不匹配",
                "调用的方法在运行时不存在或签名不同。",
                Arrays.asList(
                        "检查依赖版本是否一致，可能存在版本冲突",
                        "执行mvn clean重新编译",
                        "确认调用方和被调用方使用的是同一个版本的接口定义"
                )
        ));

        list.add(new ErrorRule(
                "并发修改异常",
                MatchType.CONTAINS,
                "ConcurrentModificationException",
                "并发修改集合异常",
                "在迭代集合的同时修改了集合。",
                Arrays.asList(
                        "使用Iterator.remove()代替集合.remove()",
                        "使用并发集合（ConcurrentHashMap、CopyOnWriteArrayList等）",
                        "使用Java 8+的removeIf或Stream API的filter操作"
                )
        ));

        list.add(new ErrorRule(
                "数据访问异常",
                MatchType.CONTAINS,
                "DataAccessException",
                "数据访问层异常",
                "Spring Data Access的通用异常。",
                Arrays.asList(
                        "查看cause中的具体异常类型，定位真正原因",
                        "检查数据库连接池和SQL配置",
                        "如果是查询无结果，考虑用Optional或默认值处理"
                )
        ));

        list.add(new ErrorRule(
                "空结果异常",
                MatchType.REGEX,
                "(?i)(EmptyResultDataAccessException|IncorrectResultSizeDataAccessException)",
                "查询结果异常",
                "期望返回单条记录但实际返回0条或多条。",
                Arrays.asList(
                        "添加空判断，使用selectList代替selectOne",
                        "加唯一条件确保查询结果唯一",
                        "用Optional包装返回值，优雅处理null情况"
                )
        ));

        list.add(new ErrorRule(
                "JSON解析异常",
                MatchType.CONTAINS,
                "JsonProcessingException",
                "JSON处理异常",
                "JSON序列化/反序列化失败。",
                Arrays.asList(
                        "检查JSON格式是否正确，字段名是否匹配",
                        "检查Java对象的字段类型与JSON值是否兼容",
                        "配置@JsonFormat处理日期格式"
                )
        ));

        list.add(new ErrorRule(
                "HTTP 404",
                MatchType.REGEX,
                "(?i)(404|NotFound|NoResourceFoundException)",
                "资源不存在",
                "请求的URL对应的资源不存在。",
                Arrays.asList(
                        "检查URL路径是否正确，是否有拼写错误",
                        "确认Controller的@RequestMapping配置",
                        "检查是否有WebMvcConfigurer拦截了请求"
                )
        ));

        list.add(new ErrorRule(
                "HTTP 500",
                MatchType.CONTAINS,
                "500",
                "服务器内部错误",
                "HTTP 500状态码，表示服务端处理请求时发生了未预期的错误。",
                Arrays.asList(
                        "查看服务端完整的异常堆栈信息",
                        "检查最近的代码变更，是否有新引入的Bug",
                        "如果是第三方接口返回500，联系对方排查"
                )
        ));

        return list;
    }

    private enum MatchType {
        EXACT,
        CONTAINS,
        REGEX
    }

    private static class ErrorRule {
        final String ruleName;
        final MatchType matchType;
        final String pattern;
        final String errorType;
        final String analysis;
        final List<String> suggestions;

        ErrorRule(String ruleName, MatchType matchType, String pattern,
                  String errorType, String analysis, List<String> suggestions) {
            this.ruleName = ruleName;
            this.matchType = matchType;
            this.pattern = pattern;
            this.errorType = errorType;
            this.analysis = analysis;
            this.suggestions = suggestions;
        }
    }
}