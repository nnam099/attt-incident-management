# Bước 1: Build ứng dụng bằng Maven
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /app

# Copy file cấu hình và cài đặt dependency trước (giúp cache layer tốt hơn)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy toàn bộ source code và build file JAR
COPY src ./src
RUN mvn clean package -DskipTests

# Bước 2: Chạy ứng dụng bằng JRE siêu nhẹ
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Khai báo múi giờ (nếu cần)
ENV TZ=Asia/Ho_Chi_Minh
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone

# Thư mục lưu file đính kèm
RUN mkdir -p /app/uploads

# Copy file JAR từ bước 1 sang
COPY --from=builder /app/target/incident-management-0.1.0.jar app.jar

# Expose port
EXPOSE 8080

# Chạy ứng dụng
ENTRYPOINT ["java", "-jar", "app.jar"]
