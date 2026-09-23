# 🚀 IT Ticketing System — Hướng dẫn chạy dự án

> Hệ thống quản lý ticket IT nội bộ — Spring Boot 4.0.1 + PostgreSQL + Maven

---

## Mục lục

- [1. Yêu cầu hệ thống](#1-yêu-cầu-hệ-thống)
- [2. Cấu hình Database (PostgreSQL)](#2-cấu-hình-database-postgresql)
- [3. Chạy bằng Maven](#3-chạy-bằng-maven)
- [4. Build JAR và chạy độc lập](#4-build-jar-và-chạy-độc-lập)
- [5. Docker (tuỳ chọn)](#5-docker-tuỳ-chọn)
- [6. Biến môi trường](#6-biến-môi-trường)
- [7. Tài khoản demo](#7-tài-khoản-demo)
- [8. Lệnh Docker hữu ích](#8-lệnh-docker-hữu-ích)

---

## 1. Yêu cầu hệ thống


| Công cụ    | Phiên bản yêu cầu |
| ---------- | ----------------- |
| **Java**   | 21                |
| **Maven**  | 3.8+              |
| **Docker** | Mới nhất          |


> ⚠️ **Lưu ý:** `pom.xml` ghi Java 17, nhưng `system.properties` quy định **Java 21**. Hãy dùng **Java 21** để đảm bảo tương thích.

---



## 2. Cấu hình Database (PostgreSQL)



### 2.1. Khởi động PostgreSQL bằng Docker

```bash
# Tạo volume để lưu dữ liệu bền vững
docker volume create ticketing-postgres-data

# Chạy container PostgreSQL
docker run -d \
  --name ticketing-postgres \
  -e POSTGRES_DB=ticketing \
  -e POSTGRES_USER=ticketing_user \
  -e POSTGRES_PASSWORD=ticketing_pass \
  -p 5432:5432 \
  -v ticketing-postgres-data:/var/lib/postgresql/data \
  postgres:17
```



### 2.2. Kiểm tra PostgreSQL đã chạy

```bash
docker ps
# OUTPUT: ticketing-postgres  postgres:17  Up 2 minutes  0.0.0.0:5432->5432/tcp
```

---



## 3. Chạy bằng Maven



### 3.1. Cách nhanh nhất — Script tự động

Script `start_local.sh` tự động khởi động PostgreSQL và chạy ứng dụng.

```bash
cd IT-Ticketing-System
chmod +x start_local.sh
./start_local.sh
```

> ✅ Script sẽ tự động: kiểm tra Docker → tạo container PostgreSQL → đợi DB sẵn sàng → chạy Spring Boot trên **port 8080**.

---



### 3.2. Cách thủ công



#### Bước 1 — Đảm bảo PostgreSQL đang chạy (xem mục 2.1)



#### Bước 2 — Chạy Spring Boot

**Dùng Maven Wrapper** (khuyến nghị — không cần cài Maven):

```bash
cd IT-Ticketing-System
./mvnw spring-boot:run
```

**Hoặc dùng Maven global:**

```bash
cd IT-Ticketing-System
mvn spring-boot:run
```



#### Bước 3 — Truy cập ứng dụng

```
🌐 http://localhost:8080
📄 API Docs: http://localhost:8080/swagger-ui/index.html
```

---



## 4. Build JAR và chạy độc lập

Build ra file JAR rồi chạy như một ứng dụng bình thường:

```bash
cd IT-Ticketing-System

# Build JAR (bỏ qua test để nhanh)
mvn clean package -DskipTests

# Chạy JAR
java -jar target/ticketing-0.0.1-SNAPSHOT.jar
```

> 💡 JAR đã build có thể chạy trên bất kỳ máy nào có Java 21, không cần Maven.

---



## 5. Docker (tuỳ chọn)

> Dự án **chưa có Dockerfile** hoặc `docker-compose.yml` sẵn có. Các template bên dưới có thể dùng ngay.



### 5.1. Dockerfile cho ứng dụng

Tạo file `Dockerfile` trong thư mục `IT-Ticketing-System/`:

```dockerfile
# Build stage
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN ./mvnw package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/ticketing-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Build và chạy:

```bash
docker build -t ticketing-app .
docker run -p 8080:8080 --link ticketing-postgres ticketing-app
```

---



### 5.2. docker-compose.yml (DB + App cùng lúc)

Tạo file `docker-compose.yml` trong thư mục `IT-Ticketing-System/`:

```yaml
version: '3.8'

services:
  # ── Database ──────────────────────────────────────────
  postgres:
    image: postgres:17
    container_name: ticketing-postgres
    environment:
      POSTGRES_DB: ticketing
      POSTGRES_USER: ticketing_user
      POSTGRES_PASSWORD: ticketing_pass
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ticketing_user -d ticketing"]
      interval: 10s
      timeout: 5s
      retries: 5

  # ── Ứng dụng ─────────────────────────────────────────
  app:
    build: .
    container_name: ticketing-app
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/ticketing
      SPRING_DATASOURCE_USERNAME: ticketing_user
      SPRING_DATASOURCE_PASSWORD: ticketing_pass
      SECURITY_JWT_SECRET: change-this-secret-to-a-long-random-value
    depends_on:
      postgres:
        condition: service_healthy

volumes:
  pgdata:
```

Chạy:

```bash
docker-compose up -d
# Kiểm tra logs
docker-compose logs -f app
```

Dừng:

```bash
docker-compose down
# Xóa cả dữ liệu
docker-compose down -v
```

---



## 6. Biến môi trường


| Biến                              | Mặc định                                        | Mô tả                     |
| --------------------------------- | ----------------------------------------------- | ------------------------- |
| `PORT`                            | `8080`                                          | Port chạy ứng dụng        |
| `SPRING_DATASOURCE_URL`           | `jdbc:postgresql://localhost:5432/IT-Ticketing` | JDBC URL                  |
| `SPRING_DATASOURCE_USERNAME`      | `postgres`                                      | Tài khoản DB              |
| `SPRING_DATASOURCE_PASSWORD`      | `1234`                                          | Mật khẩu DB               |
| `SECURITY_JWT_SECRET`             | *(secret mặc định)*                             | Khóa ký JWT (nên đổi)     |
| `SECURITY_JWT_EXPIRATION_SECONDS` | `3600`                                          | Thời hạn token JWT (giây) |








### Ví dụ — Chạy với biến môi trường (PowerShell):

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/ticketing"
$env:SPRING_DATASOURCE_USERNAME="ticketing_user"
$env:SPRING_DATASOURCE_PASSWORD="ticketing_pass"
mvn spring-boot:run
```

---



## 7. Tài khoản demo

Đăng nhập tại `http://localhost:8080` bằng các tài khoản sau:


| **Tên đăng nhập** | **Mật khẩu** | **Vai trò**  | **Phòng ban** |
| ----------------- | ------------ | ------------ | ------------- |
| `admin`           | `12345678`   | ADMIN        | -             |
| `mayagiamdoc`     | `12345678`   | GIAM_DOC     | EXEC          |
| `tp.it`           | `12345678`   | TRUONG_PHONG | IT            |
| `tp.hr`           | `12345678`   | TRUONG_PHONG | HR            |
| `tp.sale`         | `12345678`   | TRUONG_PHONG | SALE          |
| `tp.mkt`          | `12345678`   | TRUONG_PHONG | MKT           |
| `tp.fin`          | `12345678`   | TRUONG_PHONG | FIN           |
| `tech.smith`      | `12345678`   | NHAN_VIEN    | IT            |
| `tech.jane`       | `12345678`   | NHAN_VIEN    | IT            |
| `hr.tina`         | `12345678`   | NHAN_VIEN    | HR            |
| `hr.bob`          | `12345678`   | NHAN_VIEN    | HR            |
| `sale.alice`      | `12345678`   | NHAN_VIEN    | SALE          |
| `sale.david`      | `12345678`   | NHAN_VIEN    | SALE          |
| `mkt.chris`       | `12345678`   | NHAN_VIEN    | MKT           |
| `mkt.eva`         | `12345678`   | NHAN_VIEN    | MKT           |
| `fin.anna`        | `12345678`   | NHAN_VIEN    | FIN           |
| `fin.tom`         | `12345678`   | NHAN_VIEN    | FIN           |


---



## 8. Lệnh Docker hữu ích

```bash
# ── Kiểm tra ──────────────────────────────────────────
docker ps                        # Xem container đang chạy
docker ps -a                     # Xem tất cả container (kể cả đã dừng)

# ── Logs ──────────────────────────────────────────────
docker logs ticketing-postgres   # Xem logs PostgreSQL
docker logs -f ticketing-postgres # Xem logs real-time

# ── Quản lý container ────────────────────────────────
docker start ticketing-postgres  # Khởi động lại container
docker stop ticketing-postgres    # Dừng container
docker restart ticketing-postgres # Restart container

# ── Dọn dẹp ──────────────────────────────────────────
docker rm ticketing-postgres     # Xóa container
docker volume rm ticketing-postgres-data   # Xóa dữ liệu (⚠️ mất hết data)
docker system prune -a           # Xóa tất cả image/container không dùng
```

---



## 📋 Tóm tắt nhanh

```
# Cách nhanh nhất — chạy script có sẵn:
./start_local.sh

# Hoặc tự setup thủ công:
1. docker run -d --name ticketing-postgres -e POSTGRES_DB=ticketing \
     -e POSTGRES_USER=ticketing_user -e POSTGRES_PASSWORD=ticketing_pass \
     -p 5432:5432 postgres:17

2. mvn spring-boot:run

→ Mở http://localhost:8080
→ Đăng nhập: admin / admin123
```

---



## 🔗 Liên kết hữu ích


| Tài nguyên      | URL                                                                                        |
| --------------- | ------------------------------------------------------------------------------------------ |
| Ứng dụng        | [http://localhost:8080](http://localhost:8080)                                             |
| Swagger UI      | [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html) |
| API Docs (JSON) | [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)                     |


---

*Generated: September 2026*