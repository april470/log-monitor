# Dockerfile，查了下最佳实践用多阶段构建，最终镜像小很多
# 第一阶段：构建，用maven镜像
FROM maven:3.9-eclipse-temurin-17 AS build

# 先copy pom.xml和mvnw，跑dependency:go-offline
# 这样后面改了Java代码，Docker缓存层不会失效，依赖不用重新下载
WORKDIR /app
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw mvnw.cmd ./
RUN mvn dependency:go-offline -B

# 再copy源码，开始构建
COPY src ./src
RUN mvn clean package -DskipTests -B

# 第二阶段：运行，用alpine版的JRE，体积小（大概80多MB）
# 为啥用JRE不用JDK？运行时不需要javac这些编译工具
FROM eclipse-temurin:21-jre-alpine

# 设置时区，不然日志时间可能不对
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

WORKDIR /app

# 从构建阶段拿jar包，Spring Boot打包后是重命名为app.jar
COPY --from=build /app/target/*.jar app.jar

# 暴露端口
EXPOSE 8080

# 启动命令，用java -jar，加了-XX:+UseContainerSupport让JVM感知Docker内存限制
ENTRYPOINT ["java", "-jar", "-XX:+UseContainerSupport", "app.jar"]