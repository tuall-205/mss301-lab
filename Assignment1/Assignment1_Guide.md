# Assignment 01 – Hướng dẫn thực hiện step-by-step & kiểm thử bằng Postman
## FUCinemaBookingSystem – Cinema Ticket Booking System using API Gateway

> **Phiên bản:** Java 21 · Spring Boot **4.1.0** · Spring Cloud **2025.1.3** · **SQL Server 2022 · MongoDB 7.0.5 · MySQL 8.3.0** (Docker) · Postman (bản Desktop mới nhất)

---

## Mục lục

- [0. Tổng quan & chuẩn bị](#0-tổng-quan--chuẩn-bị)
- [Bước 1 – Hạ tầng: Docker Compose 3 database (F0)](#bước-1--hạ-tầng-docker-compose-3-database-f0)
- [Bước 2 – customer-service với SQL Server (F1, F2, F3)](#bước-2--customer-service-với-sql-server-f1-f2-f3)
- [Bước 3 – movie-service với MongoDB (F4, F5, F6)](#bước-3--movie-service-với-mongodb-f4-f5-f6)
- [Bước 4 – booking-service với MySQL (F7, F8, F9)](#bước-4--booking-service-với-mysql-f7-f8-f9)
- [Bước 5 – api-gateway (F10)](#bước-5--api-gateway-f10)
- [Bước 6 – Chạy toàn hệ thống](#bước-6--chạy-toàn-hệ-thống)
- [Bước 7 – Kiểm thử API bằng Postman (F11)](#bước-7--kiểm-thử-api-bằng-postman-f11)
- [Bước 8 – Xử lý lỗi thường gặp](#bước-8--xử-lý-lỗi-thường-gặp)
- [Bước 9 – Quy ước commit theo từng TODO (Conventional Commits)](#bước-9--quy-ước-commit-theo-từng-todo-conventional-commits)

---

## 0. Tổng quan & chuẩn bị

### 0.1 Công cụ cần có

| Công cụ | Ghi chú |
|---|---|
| JDK 21 | `java -version` phải ra 21 |
| Maven 3.9+ (hoặc Maven wrapper `mvnw` sinh sẵn) | |
| IntelliJ IDEA | Bật *Annotation Processing* cho Lombok |
| Docker Desktop | Chạy SQL Server, MongoDB, MySQL (cấp ≥ 4 GB RAM cho Docker) |
| MongoDB Compass *(tùy chọn)* | Xem dữ liệu MongoDB bằng giao diện |
| Postman Desktop | Test API |

### 0.2 Cấu trúc thư mục cuối cùng

```
fu-cinema/
├── docker-compose.yml  (sqlserver + sqlserver-init, mongo, mysql)
├── sqlserver/init.sql
├── mysql/init.sql
├── customer-service/   (port 8081, SQL Server – DB cinema_customer, Flyway)
├── movie-service/      (port 8082, MongoDB    – DB cinema_movie, DataSeeder)
├── booking-service/    (port 8083, MySQL      – DB cinema_booking, Flyway)  --Feign-->  movie-service
└── api-gateway/        (port 9000)  <-- Postman chỉ gọi vào đây
```

### 0.3 Luồng xác thực (đọc kỹ trước khi code)

```
(1) POST /api/auth/login  ──► Gateway (permitAll) ──► customer-service
                                                       kiểm tra email/password
    ◄──────────── { accessToken: "eyJ..." } ◄──────── ký JWT HS256 {sub, uid, role}

(2) GET /api/bookings/my
    Authorization: Bearer eyJ...  ──► Gateway
                                       ├─ verify chữ ký (cùng secret) + hạn token → sai: 401
                                       ├─ role có được phép gọi URL này? → không: 403
                                       ├─ xóa X-User-* client gửi lên, chèn lại từ JWT:
                                       │    X-User-Id: 1, X-User-Email: an@gmail.com, X-User-Role: CUSTOMER
                                       └─ route ──► booking-service đọc @RequestHeader("X-User-Id")
```

> **Ý tưởng chính:** chỉ Gateway kiểm tra token. Các service phía sau **không cần Spring Security**, chỉ đọc header do Gateway chèn vào. Vì vậy **luôn test qua cổng 9000**; gọi thẳng 8081/8083 các API cần user sẽ báo `401 Missing user context header`.

### 0.4 Tài khoản test

| Vai trò | Email | Password | Ghi chú |
|---|---|---|---|
| Admin | `admin@fucinema.com` | `@@abc123@@` | Lưu trong `application.properties` |
| Customer | `an@gmail.com` | `123456` | ID 1, ACTIVE |
| Customer | `binh@gmail.com` | `123456` | ID 2, ACTIVE |
| Customer | `chi@gmail.com` | `123456` | ID 3, **INACTIVE** (login → 403) |

---

## Bước 1 – Hạ tầng: Docker Compose 3 database (F0)

| Container | Image | Port | Tài khoản | Dùng cho |
|---|---|---|---|---|
| `cinema-sqlserver` | `mcr.microsoft.com/mssql/server:2022-latest` | 1433 | `sa` / `Fucinema@2026` | customer-service |
| `cinema-sqlserver-init` | (cùng image) | – | – | Chạy 1 lần tạo DB `cinema_customer` rồi tắt |
| `cinema-mongo` | `mongo:7.0.5` | 27017 | `root` / `password` | movie-service |
| `cinema-mysql` | `mysql:8.3.0` | 3306 | `root` / `mysql` | booking-service |

> SQL Server cần Docker Desktop cấp **≥ 2 GB RAM** và chỉ có image cho CPU **x86-64** (máy Apple Silicon phải bật Rosetta/emulation). Mật khẩu `sa` bắt buộc **≥ 8 ký tự, có chữ hoa, chữ thường, số và ký tự đặc biệt** – sai quy tắc container sẽ tự tắt.

### Bước 1.1 (TODO 0.1) – `docker-compose.yml`

Tạo thư mục `fu-cinema/`, thêm file **`fu-cinema/docker-compose.yml`**:

```yaml
services:
  # ---------- SQL Server: customer-service ----------
  sqlserver:
    image: mcr.microsoft.com/mssql/server:2022-latest
    container_name: cinema-sqlserver
    ports:
      - "1433:1433"
    environment:
      ACCEPT_EULA: "Y"
      MSSQL_SA_PASSWORD: "Fucinema@2026"
      MSSQL_PID: "Developer"
    volumes:
      - sqlserver-data:/var/opt/mssql          # named volume (bind mount tren Windows hay loi quyen)
    healthcheck:
      test: ["CMD-SHELL", "/opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P \"$$MSSQL_SA_PASSWORD\" -C -Q \"SELECT 1\" || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 20s

  # Chay 1 lan sau khi SQL Server san sang: tao database cinema_customer
  sqlserver-init:
    image: mcr.microsoft.com/mssql/server:2022-latest
    container_name: cinema-sqlserver-init
    depends_on:
      sqlserver:
        condition: service_healthy
    volumes:
      - ./sqlserver/init.sql:/init.sql:ro
    entrypoint: ["/opt/mssql-tools18/bin/sqlcmd", "-S", "sqlserver", "-U", "sa", "-P", "Fucinema@2026", "-C", "-i", "/init.sql"]
    restart: "no"

  # ---------- MongoDB: movie-service ----------
  mongo:
    image: mongo:7.0.5
    container_name: cinema-mongo
    ports:
      - "27017:27017"
    environment:
      MONGO_INITDB_ROOT_USERNAME: root
      MONGO_INITDB_ROOT_PASSWORD: password
    volumes:
      - ./docker/mongodb/data:/data/db

  # ---------- MySQL: booking-service ----------
  mysql:
    image: mysql:8.3.0
    container_name: cinema-mysql
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: mysql
    volumes:
      - ./mysql/init.sql:/docker-entrypoint-initdb.d/init.sql
      - ./docker/mysql/data:/var/lib/mysql

volumes:
  sqlserver-data:
```

**Giải thích:**

| Phần | Ý nghĩa |
|---|---|
| `healthcheck` | SQL Server khởi động mất 10–30 giây. Lệnh `sqlcmd ... SELECT 1` chạy thành công thì container được đánh dấu **healthy** |
| `$$MSSQL_SA_PASSWORD` | `$$` để Docker Compose **không** thay biến, giữ nguyên `$MSSQL_SA_PASSWORD` cho shell trong container |
| `-C` | `sqlcmd` (bản tools18) mặc định mã hóa kết nối → `-C` = tin chứng chỉ tự ký của server |
| `depends_on.condition: service_healthy` | `sqlserver-init` chỉ chạy khi SQL Server đã sẵn sàng |
| `sqlserver-init` | SQL Server **không** có thư mục `docker-entrypoint-initdb.d` như MySQL/MongoDB → dùng một container phụ chạy `sqlcmd -i init.sql` rồi tự thoát |
| `mongo` | MongoDB tự tạo database `cinema_movie` và các collection khi movie-service ghi document đầu tiên → không cần script |

### Bước 1.2 (TODO 0.2) – Script tạo database

File **`fu-cinema/sqlserver/init.sql`** (T-SQL, `GO` kết thúc một batch):

```sql
IF DB_ID(N'cinema_customer') IS NULL
    CREATE DATABASE cinema_customer;
GO
```

File **`fu-cinema/mysql/init.sql`**:

```sql
CREATE DATABASE IF NOT EXISTS cinema_booking CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### Bước 1.3 – Khởi động & kiểm tra

```bash
cd fu-cinema
docker compose up -d
docker compose ps -a
#  cinema-sqlserver        Up (healthy)
#  cinema-sqlserver-init   Exited (0)        <- da chay xong script, binh thuong
#  cinema-mongo            Up
#  cinema-mysql            Up
```

```bash
# SQL Server: co database cinema_customer
docker exec -it cinema-sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "Fucinema@2026" -C -Q "SELECT name FROM sys.databases"

# MongoDB: dang nhap duoc
docker exec -it cinema-mongo mongosh -u root -p password --authenticationDatabase admin --eval "db.adminCommand('ping')"

# MySQL: co database cinema_booking
docker exec -it cinema-mysql mysql -uroot -pmysql -e "SHOW DATABASES;"
```

> ⚠️ Container `mysql` / `mongo` của Part 1 đang chiếm cổng 3306 / 27017 → `docker stop mysql mongo` rồi chạy lại.
> ⚠️ `cinema-sqlserver` liên tục restart/Exited → xem `docker logs cinema-sqlserver` (thường do mật khẩu yếu hoặc thiếu RAM).
> ⚠️ Image cũ không có `/opt/mssql-tools18` → đổi thành `/opt/mssql-tools/bin/sqlcmd` và bỏ tham số `-C`.
> ⚠️ Script init **chỉ chạy lần đầu**. Muốn làm lại từ đầu: `docker compose down -v` (xóa cả named volume SQL Server) → xóa thư mục `docker/` → `docker compose up -d`.

**Kết nối bằng IntelliJ (Database tool window) – tùy chọn:**

| Loại | URL | User / Password |
|---|---|---|
| Microsoft SQL Server | `jdbc:sqlserver://localhost:1433;databaseName=cinema_customer;encrypt=true;trustServerCertificate=true` | `sa` / `Fucinema@2026` |
| MongoDB | `mongodb://localhost:27017/?authSource=admin` | `root` / `password` |
| MySQL | `jdbc:mysql://localhost:3306/cinema_booking` | `root` / `mysql` |

**✅ Checklist Bước 1:** `cinema-sqlserver` healthy · `cinema-sqlserver-init` Exited (0) · có DB `cinema_customer` (SQL Server) và `cinema_booking` (MySQL) · MongoDB ping OK.

**💾 Commit gợi ý:** khởi tạo Git theo [9.2](#92-khởi-tạo-git-làm-trước-bước-1) **trước**, rồi commit TODO 0.1, 0.2 – xem bảng ở [Bước 9.4](#bước-9--quy-ước-commit-theo-từng-todo-conventional-commits).

---

## Bước 2 – customer-service với SQL Server (F1, F2, F3)

### Bước 2.1 (TODO 0.3) – Generate project

Vào **start.spring.io**: Maven · Java **21** · Spring Boot **4.1.0** · Group `com.fudn` · Artifact `customer-service` · Packaging Jar.
Dependencies: **Spring Web**, **Spring Data JPA**, **Validation**, **MS SQL Server Driver**, **Flyway Migration**, **Lombok**.

Sau khi giải nén vào `fu-cinema/customer-service`, mở **`pom.xml`** và chỉnh cho giống bên dưới (thêm 2 thư viện Spring Security nhỏ để **băm mật khẩu BCrypt** và **ký JWT** – *không* thêm `spring-boot-starter-security` để service không bị khóa toàn bộ endpoint):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.0</version>
        <relativePath/>
    </parent>
    <groupId>com.fudn</groupId>
    <artifactId>customer-service</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>customer-service</name>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-flyway</artifactId>
        </dependency>
        <!-- TODO 0.3: Flyway ho tro SQL Server (Flyway 10+ tach module theo DBMS) -->
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-sqlserver</artifactId>
        </dependency>
        <!-- TODO 0.3: JDBC driver Microsoft SQL Server -->
        <dependency>
            <groupId>com.microsoft.sqlserver</groupId>
            <artifactId>mssql-jdbc</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- TODO 1.2: BCryptPasswordEncoder (chi thu vien crypto, khong bat Spring Security) -->
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-crypto</artifactId>
        </dependency>
        <!-- TODO 1.3: NimbusJwtEncoder de ky JWT HS256 -->
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-oauth2-jose</artifactId>
        </dependency>

        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

> Version của `spring-security-*` do Spring Boot BOM quản lý → **không ghi `<version>`**. Sửa xong: IntelliJ → Maven → **Reload project**.
> Start.spring.io có thể sinh tên starter test khác (`spring-boot-starter-data-jpa-test`, `spring-boot-starter-flyway-test`…). Giữ nguyên các dependency test nó sinh ra cũng được.

Cấu trúc package sẽ tạo:

```
customer-service/src/main/java/com/fudn/customerservice/
├── CustomerServiceApplication.java
├── config/PasswordConfig.java
├── controller/{AuthController, CustomerController}.java
├── dto/{LoginRequest, LoginResponse, RegisterRequest, ProfileUpdateRequest,
│        ChangePasswordRequest, AdminCustomerRequest, CustomerResponse}.java
├── exception/{ApiException, ErrorResponse, GlobalExceptionHandler}.java
├── model/{Customer, CustomerStatus}.java
├── repository/CustomerRepository.java
├── security/JwtService.java
└── service/{AuthService, CustomerService}.java
customer-service/src/main/resources/
├── application.properties
└── db/migration/{V1__init.sql, V2__seed.sql}
```

### Bước 2.2 (TODO 0.5, 1.1) – `application.properties`

```properties
spring.application.name=customer-service
server.port=8081

# SQL Server: encrypt=true la mac dinh cua driver moi; trustServerCertificate=true vi container dung chung chi tu ky
spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=cinema_customer;encrypt=true;trustServerCertificate=true
spring.datasource.username=sa
spring.datasource.password=Fucinema@2026

# Schema do Flyway quan ly
spring.jpa.hibernate.ddl-auto=none
spring.jpa.open-in-view=false
spring.jpa.show-sql=true

# TODO 1.1: Tai khoan Admin (khong luu trong DB)
app.admin.email=admin@fucinema.com
app.admin.password=@@abc123@@

# TODO 1.1: JWT - secret HS256 phai >= 32 ky tu va TRUNG voi api-gateway
app.jwt.secret=fu-cinema-booking-system-secret-key-2026-mss301
app.jwt.expiration-minutes=60
```

### Bước 2.3 (TODO 2.1) – Flyway migration

**`db/migration/V1__init.sql`**

```sql
-- T-SQL (SQL Server): IDENTITY thay cho AUTO_INCREMENT, NVARCHAR de luu tieng Viet co dau
CREATE TABLE customer (
    customer_id       BIGINT IDENTITY(1,1) PRIMARY KEY,
    customer_name     NVARCHAR(100) NOT NULL,
    telephone         VARCHAR(15),
    email             VARCHAR(100)  NOT NULL,
    customer_birthday DATE,
    customer_status   VARCHAR(20)   NOT NULL CONSTRAINT df_customer_status DEFAULT 'ACTIVE',
    password          VARCHAR(100)  NOT NULL,
    CONSTRAINT uk_customer_email UNIQUE (email)
);
```

**`db/migration/V2__seed.sql`** – mật khẩu cả 3 là `123456` (đã băm BCrypt):

```sql
INSERT INTO customer (customer_name, telephone, email, customer_birthday, customer_status, password) VALUES
(N'Nguyễn Văn An', '0905123456', 'an@gmail.com',   '2002-05-10', 'ACTIVE',   '$2a$10$dmoDdVpWYdqLarqBfkYQteoq1YORLC5LLMd55bpomZ3EarS/vtjtW'),
(N'Trần Thị Bình', '0914234567', 'binh@gmail.com', '2003-08-21', 'ACTIVE',   '$2a$10$dmoDdVpWYdqLarqBfkYQteoq1YORLC5LLMd55bpomZ3EarS/vtjtW'),
(N'Lê Minh Chi',   '0935345678', 'chi@gmail.com',  '2001-12-01', 'INACTIVE', '$2a$10$dmoDdVpWYdqLarqBfkYQteoq1YORLC5LLMd55bpomZ3EarS/vtjtW');
```

**Lưu ý khi chuyển từ MySQL sang SQL Server:**

| MySQL | SQL Server | Ghi chú |
|---|---|---|
| `AUTO_INCREMENT` | `IDENTITY(1,1)` | JPA vẫn dùng `GenerationType.IDENTITY` |
| `VARCHAR` (utf8mb4 lưu được tiếng Việt) | **`NVARCHAR`** | `VARCHAR` của SQL Server **không** lưu được ký tự Unicode → `Nguy?n V?n An` |
| `'Nguyễn'` | **`N'Nguyễn'`** | Tiền tố `N` = chuỗi Unicode trong T-SQL |
| `DEFAULT 'ACTIVE'` | `CONSTRAINT df_... DEFAULT 'ACTIVE'` | Đặt tên constraint để dễ xóa/sửa sau này |
| `LIMIT 10` | `TOP 10` / `OFFSET ... FETCH` | Hibernate tự sinh đúng cú pháp (dialect tự nhận diện) |

> Driver `mssql-jdbc` mặc định gửi tham số chuỗi dạng Unicode (`sendStringParametersAsUnicode=true`) nên Entity `Customer` **không cần** đổi gì. Có thể thêm `@Nationalized` (Hibernate) trên `customerName` để thể hiện rõ cột NVARCHAR.

> ⚠️ Đã chạy app rồi thì **không sửa** file `V1`, `V2` nữa (Flyway báo *checksum mismatch*). Muốn thay đổi → tạo `V3__...sql`.

### Bước 2.4 (TODO 0.6) – Xử lý lỗi dùng chung

Ba file này **copy y hệt sang movie-service và booking-service** (chỉ đổi dòng `package`).

**`exception/ApiException.java`**

```java
package com.fudn.customerservice.exception;

import org.springframework.http.HttpStatus;

/** Exception nghiep vu mang theo HTTP status de GlobalExceptionHandler tra ve. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static ApiException badRequest(String message)   { return new ApiException(HttpStatus.BAD_REQUEST, message); }
    public static ApiException unauthorized(String message) { return new ApiException(HttpStatus.UNAUTHORIZED, message); }
    public static ApiException forbidden(String message)    { return new ApiException(HttpStatus.FORBIDDEN, message); }
    public static ApiException notFound(String message)     { return new ApiException(HttpStatus.NOT_FOUND, message); }
    public static ApiException conflict(String message)     { return new ApiException(HttpStatus.CONFLICT, message); }
}
```

**`exception/ErrorResponse.java`**

```java
package com.fudn.customerservice.exception;

import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

public record ErrorResponse(LocalDateTime timestamp, int status, String error, String message, String path) {

    public static ErrorResponse of(HttpStatus status, String message, String path) {
        return new ErrorResponse(LocalDateTime.now(), status.value(), status.getReasonPhrase(), message, path);
    }
}
```

**`exception/GlobalExceptionHandler.java`**

```java
package com.fudn.customerservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest request) {
        return build(ex.getStatus(), ex.getMessage(), request);
    }

    /** Loi Bean Validation (@Valid) -> 400, gom thong bao theo tung field. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    /** JSON sai cu phap, sai kieu enum/ngay, thieu query param... -> 400 */
    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Invalid request: " + ex.getMessage(), request);
    }

    /** Thieu header X-User-* -> request khong di qua API Gateway. */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED,
                "Missing user context header '" + ex.getHeaderName() + "'. Please call the API through the API Gateway.",
                request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Endpoint not found", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleOther(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status, message, request.getRequestURI()));
    }
}
```

### Bước 2.5 (TODO 2.2) – Model & Repository

**`model/CustomerStatus.java`**

```java
package com.fudn.customerservice.model;

public enum CustomerStatus {
    ACTIVE, INACTIVE
}
```

**`model/Customer.java`** – Spring Boot tự đổi `customerName` → cột `customer_name` (camelCase → snake_case):

```java
package com.fudn.customerservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "customer")
@Getter
@Setter
@NoArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long customerId;

    @Column(nullable = false, length = 100)
    private String customerName;

    private String telephone;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    private LocalDate customerBirthday;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerStatus customerStatus;

    @Column(nullable = false)
    private String password;
}
```

**`repository/CustomerRepository.java`**

```java
package com.fudn.customerservice.repository;

import com.fudn.customerservice.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndCustomerIdNot(String email, Long customerId);

    List<Customer> findByCustomerNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrderByCustomerIdAsc(
            String customerName, String email);
}
```

### Bước 2.6 (TODO 2.3, 3.1) – DTO

**`dto/LoginRequest.java`**

```java
package com.fudn.customerservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Email is required") @Email(message = "Email is invalid") String email,
        @NotBlank(message = "Password is required") String password) {
}
```

**`dto/LoginResponse.java`**

```java
package com.fudn.customerservice.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,      // giay
        String role,         // ADMIN | CUSTOMER
        Long userId,         // Admin = 0
        String email,
        String fullName) {
}
```

**`dto/RegisterRequest.java`**

```java
package com.fudn.customerservice.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record RegisterRequest(
        @NotBlank(message = "Customer name is required")
        @Size(max = 100, message = "Customer name must not exceed 100 characters")
        String customerName,

        @NotBlank(message = "Telephone is required")
        @Pattern(regexp = "^0\\d{9}$", message = "Telephone must have 10 digits and start with 0")
        String telephone,

        @NotBlank(message = "Email is required")
        @Email(message = "Email is invalid")
        @Size(max = 100)
        String email,

        @NotNull(message = "Birthday is required")
        @Past(message = "Birthday must be in the past")
        LocalDate customerBirthday,

        @NotBlank(message = "Password is required")
        @Size(min = 6, max = 50, message = "Password must be 6-50 characters")
        String password) {
}
```

**`dto/ProfileUpdateRequest.java`**

```java
package com.fudn.customerservice.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record ProfileUpdateRequest(
        @NotBlank(message = "Customer name is required") @Size(max = 100) String customerName,
        @NotBlank(message = "Telephone is required")
        @Pattern(regexp = "^0\\d{9}$", message = "Telephone must have 10 digits and start with 0") String telephone,
        @NotNull(message = "Birthday is required") @Past(message = "Birthday must be in the past") LocalDate customerBirthday) {
}
```

**`dto/ChangePasswordRequest.java`**

```java
package com.fudn.customerservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Old password is required") String oldPassword,
        @NotBlank(message = "New password is required")
        @Size(min = 6, max = 50, message = "New password must be 6-50 characters") String newPassword) {
}
```

**`dto/AdminCustomerRequest.java`** – dùng cho Admin tạo/sửa; `password` bắt buộc khi tạo, để `null` khi sửa nếu không đổi:

```java
package com.fudn.customerservice.dto;

import com.fudn.customerservice.model.CustomerStatus;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record AdminCustomerRequest(
        @NotBlank(message = "Customer name is required") @Size(max = 100) String customerName,
        @NotBlank(message = "Telephone is required")
        @Pattern(regexp = "^0\\d{9}$", message = "Telephone must have 10 digits and start with 0") String telephone,
        @NotBlank(message = "Email is required") @Email(message = "Email is invalid") @Size(max = 100) String email,
        @NotNull(message = "Birthday is required") @Past(message = "Birthday must be in the past") LocalDate customerBirthday,
        @NotNull(message = "Customer status is required") CustomerStatus customerStatus,
        @Size(min = 6, max = 50, message = "Password must be 6-50 characters") String password) {
}
```

**`dto/CustomerResponse.java`** – **không có password**:

```java
package com.fudn.customerservice.dto;

import com.fudn.customerservice.model.Customer;
import com.fudn.customerservice.model.CustomerStatus;

import java.time.LocalDate;

public record CustomerResponse(
        Long customerId,
        String customerName,
        String telephone,
        String email,
        LocalDate customerBirthday,
        CustomerStatus customerStatus) {

    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(c.getCustomerId(), c.getCustomerName(), c.getTelephone(),
                c.getEmail(), c.getCustomerBirthday(), c.getCustomerStatus());
    }
}
```

### Bước 2.7 (TODO 1.2, 1.3) – PasswordEncoder & JwtService

**`config/PasswordConfig.java`**

```java
package com.fudn.customerservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

**`security/JwtService.java`**

```java
package com.fudn.customerservice.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Ky JWT HS256. Gateway dung CUNG secret de verify. */
@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final long expirationMinutes;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.expirationMinutes = expirationMinutes;
    }

    // TODO 1.3
    public String generateToken(Long userId, String email, String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("fu-cinema")
                .subject(email)                                   // sub
                .issuedAt(now)                                    // iat
                .expiresAt(now.plus(expirationMinutes, ChronoUnit.MINUTES)) // exp
                .claim("uid", userId)
                .claim("role", role)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long getExpirationSeconds() {
        return expirationMinutes * 60;
    }
}
```

> Dán token vào **jwt.io** để xem payload: `{"iss":"fu-cinema","sub":"an@gmail.com","uid":1,"role":"CUSTOMER","iat":...,"exp":...}`.

### Bước 2.8 (TODO 1.4) – `service/AuthService.java`

```java
package com.fudn.customerservice.service;

import com.fudn.customerservice.dto.LoginRequest;
import com.fudn.customerservice.dto.LoginResponse;
import com.fudn.customerservice.exception.ApiException;
import com.fudn.customerservice.model.Customer;
import com.fudn.customerservice.model.CustomerStatus;
import com.fudn.customerservice.repository.CustomerRepository;
import com.fudn.customerservice.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_CUSTOMER = "CUSTOMER";
    private static final long ADMIN_ID = 0L;

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminPassword;

    public LoginResponse login(LoginRequest request) {
        // (1) Admin: so sanh voi application.properties
        if (adminEmail.equalsIgnoreCase(request.email())) {
            if (!adminPassword.equals(request.password())) {
                throw ApiException.unauthorized("Invalid email or password");
            }
            return buildResponse(ADMIN_ID, adminEmail, "Administrator", ROLE_ADMIN);
        }

        // (2) Customer: tim trong DB, so khop BCrypt
        Customer customer = customerRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), customer.getPassword())) {
            throw ApiException.unauthorized("Invalid email or password");
        }
        // (3) Chan tai khoan INACTIVE (BR02)
        if (customer.getCustomerStatus() == CustomerStatus.INACTIVE) {
            throw ApiException.forbidden("Your account is inactive. Please contact the administrator.");
        }
        return buildResponse(customer.getCustomerId(), customer.getEmail(), customer.getCustomerName(), ROLE_CUSTOMER);
    }

    private LoginResponse buildResponse(Long userId, String email, String fullName, String role) {
        String token = jwtService.generateToken(userId, email, role);
        return new LoginResponse(token, "Bearer", jwtService.getExpirationSeconds(), role, userId, email, fullName);
    }
}
```

> Thông báo lỗi giống nhau cho "sai email" và "sai mật khẩu" → không lộ email nào tồn tại trong hệ thống.

### Bước 2.9 (TODO 2.4, 2.5, 3.2) – `service/CustomerService.java`

```java
package com.fudn.customerservice.service;

import com.fudn.customerservice.dto.*;
import com.fudn.customerservice.exception.ApiException;
import com.fudn.customerservice.model.Customer;
import com.fudn.customerservice.model.CustomerStatus;
import com.fudn.customerservice.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email}")
    private String adminEmail;

    // ===================== CUSTOMER (F2) =====================

    // TODO 2.4
    @Transactional
    public CustomerResponse register(RegisterRequest request) {
        ensureEmailAvailable(request.email(), null);
        Customer customer = new Customer();
        customer.setCustomerName(request.customerName());
        customer.setTelephone(request.telephone());
        customer.setEmail(request.email());
        customer.setCustomerBirthday(request.customerBirthday());
        customer.setCustomerStatus(CustomerStatus.ACTIVE);
        customer.setPassword(passwordEncoder.encode(request.password()));
        Customer saved = customerRepository.save(customer);
        log.info("Customer registered: id={}, email={}", saved.getCustomerId(), saved.getEmail());
        return CustomerResponse.from(saved);
    }

    // TODO 2.5
    public CustomerResponse getProfile(Long customerId) {
        return CustomerResponse.from(findCustomer(customerId));
    }

    @Transactional
    public CustomerResponse updateProfile(Long customerId, ProfileUpdateRequest request) {
        Customer customer = findCustomer(customerId);
        customer.setCustomerName(request.customerName());
        customer.setTelephone(request.telephone());
        customer.setCustomerBirthday(request.customerBirthday());
        return CustomerResponse.from(customerRepository.save(customer));
    }

    @Transactional
    public void changePassword(Long customerId, ChangePasswordRequest request) {
        Customer customer = findCustomer(customerId);
        if (!passwordEncoder.matches(request.oldPassword(), customer.getPassword())) {
            throw ApiException.badRequest("Old password is incorrect");
        }
        if (request.oldPassword().equals(request.newPassword())) {
            throw ApiException.badRequest("New password must be different from the old password");
        }
        customer.setPassword(passwordEncoder.encode(request.newPassword()));
        customerRepository.save(customer);
    }

    // ===================== ADMIN (F3) =====================

    // TODO 3.2
    public List<CustomerResponse> search(String keyword) {
        List<Customer> customers = (keyword == null || keyword.isBlank())
                ? customerRepository.findAll(Sort.by("customerId"))
                : customerRepository.findByCustomerNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrderByCustomerIdAsc(
                        keyword.trim(), keyword.trim());
        return customers.stream().map(CustomerResponse::from).toList();
    }

    public CustomerResponse getById(Long id) {
        return CustomerResponse.from(findCustomer(id));
    }

    @Transactional
    public CustomerResponse create(AdminCustomerRequest request) {
        if (request.password() == null || request.password().isBlank()) {
            throw ApiException.badRequest("password: Password is required when creating a customer");
        }
        ensureEmailAvailable(request.email(), null);
        Customer customer = new Customer();
        applyAdminRequest(customer, request);
        customer.setPassword(passwordEncoder.encode(request.password()));
        return CustomerResponse.from(customerRepository.save(customer));
    }

    @Transactional
    public CustomerResponse update(Long id, AdminCustomerRequest request) {
        Customer customer = findCustomer(id);
        ensureEmailAvailable(request.email(), id);
        applyAdminRequest(customer, request);
        if (request.password() != null && !request.password().isBlank()) {
            customer.setPassword(passwordEncoder.encode(request.password()));
        }
        return CustomerResponse.from(customerRepository.save(customer));
    }

    /** Xoa mem: chuyen INACTIVE de giu lich su booking (booking-service van tham chieu customerId). */
    @Transactional
    public void delete(Long id) {
        Customer customer = findCustomer(id);
        customer.setCustomerStatus(CustomerStatus.INACTIVE);
        customerRepository.save(customer);
    }

    // ===================== HELPER =====================

    private Customer findCustomer(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Customer not found with id: " + id));
    }

    /** BR01: email duy nhat va khong trung email Admin. excludeId != null khi update. */
    private void ensureEmailAvailable(String email, Long excludeId) {
        boolean exists = excludeId == null
                ? customerRepository.existsByEmailIgnoreCase(email)
                : customerRepository.existsByEmailIgnoreCaseAndCustomerIdNot(email, excludeId);
        if (exists || adminEmail.equalsIgnoreCase(email)) {
            throw ApiException.conflict("Email is already in use: " + email);
        }
    }

    private void applyAdminRequest(Customer customer, AdminCustomerRequest request) {
        customer.setCustomerName(request.customerName());
        customer.setTelephone(request.telephone());
        customer.setEmail(request.email());
        customer.setCustomerBirthday(request.customerBirthday());
        customer.setCustomerStatus(request.customerStatus());
    }
}
```

### Bước 2.10 (TODO 1.5, 2.6, 3.3) – Controller

**`controller/AuthController.java`**

```java
package com.fudn.customerservice.controller;

import com.fudn.customerservice.dto.LoginRequest;
import com.fudn.customerservice.dto.LoginResponse;
import com.fudn.customerservice.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
```

**`controller/CustomerController.java`** – các endpoint `/me` lấy ID từ header `X-User-Id` do Gateway chèn; phân quyền ADMIN/CUSTOMER đã làm ở Gateway:

```java
package com.fudn.customerservice.controller;

import com.fudn.customerservice.dto.*;
import com.fudn.customerservice.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private static final String USER_ID = "X-User-Id";

    private final CustomerService customerService;

    // ---------- Public ----------
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse register(@Valid @RequestBody RegisterRequest request) {
        return customerService.register(request);
    }

    // ---------- CUSTOMER ----------
    @GetMapping("/me")
    public CustomerResponse getProfile(@RequestHeader(USER_ID) Long userId) {
        return customerService.getProfile(userId);
    }

    @PutMapping("/me")
    public CustomerResponse updateProfile(@RequestHeader(USER_ID) Long userId,
                                          @Valid @RequestBody ProfileUpdateRequest request) {
        return customerService.updateProfile(userId, request);
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@RequestHeader(USER_ID) Long userId,
                               @Valid @RequestBody ChangePasswordRequest request) {
        customerService.changePassword(userId, request);
    }

    // ---------- ADMIN ----------
    @GetMapping
    public List<CustomerResponse> search(@RequestParam(required = false) String keyword) {
        return customerService.search(keyword);
    }

    @GetMapping("/{id}")
    public CustomerResponse getById(@PathVariable Long id) {
        return customerService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@Valid @RequestBody AdminCustomerRequest request) {
        return customerService.create(request);
    }

    @PutMapping("/{id}")
    public CustomerResponse update(@PathVariable Long id, @Valid @RequestBody AdminCustomerRequest request) {
        return customerService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        customerService.delete(id);
    }
}
```

> `GET /me` và `GET /{id}` không xung đột: Spring ưu tiên đường dẫn **cố định** (`/me`) hơn đường dẫn có biến.

### Bước 2.11 – Chạy & kiểm tra nhanh customer-service

```bash
cd customer-service
mvn spring-boot:run
```

Log phải có `Successfully applied 2 migrations to schema "dbo"` (schema mặc định của SQL Server) và `Tomcat started on port 8081`. Thử nhanh (gọi trực tiếp, chưa có gateway):

```bash
docker exec -it cinema-sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "Fucinema@2026" -C \
        -d cinema_customer -Q "SELECT customer_id, customer_name, email, customer_status FROM customer"

curl -X POST http://localhost:8081/api/auth/login -H "Content-Type: application/json" \
     -d "{\"email\":\"an@gmail.com\",\"password\":\"123456\"}"
```

**✅ Checklist Bước 2:** 2 migration chạy trên SQL Server · `SELECT customer_name FROM customer` hiển thị đúng tiếng Việt · login Admin & Customer ra token · login `chi@gmail.com` → 403.

**💾 Commit gợi ý:** TODO 0.3, 0.5, 0.6 (customer) → 2.1, 2.2 → 1.1 – 1.5 → 2.3 – 2.6 → 3.1 – 3.3 – xem bảng ở [Bước 9.4](#bước-9--quy-ước-commit-theo-từng-todo-conventional-commits).

---
## Bước 3 – movie-service với MongoDB (F4, F5, F6)

> **Khác biệt chính so với service dùng JPA:**
>
> | JPA (customer, booking) | MongoDB (movie) |
> |---|---|
> | `@Entity`, `@Table`, `@Column` | `@Document(collection = ...)`, `@Field` |
> | `@Id @GeneratedValue Long` | `@Id String` → MongoDB sinh **ObjectId** |
> | `@ManyToOne` + khóa ngoại | Lưu **ID tham chiếu** (`genreId`, `movieId`, `roomId`), tự kiểm tra tồn tại (BR15) |
> | Flyway tạo bảng + seed | Collection tự tạo khi ghi; seed bằng **`DataSeeder`** |
> | JPQL `@Query` | Derived query / `@Query` JSON / **`MongoTemplate` + `Criteria`** |
> | `@Transactional` | Không dùng (MongoDB standalone không hỗ trợ transaction nhiều document) |

### Bước 3.1 (TODO 0.3) – Generate project

start.spring.io: Artifact `movie-service`. Dependencies: **Spring Web**, **Spring Data MongoDB**, **Validation**, **Lombok** (không chọn JPA, Flyway, driver SQL).

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.0</version>
        <relativePath/>
    </parent>
    <groupId>com.fudn</groupId>
    <artifactId>movie-service</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>movie-service</name>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>
        <!-- TODO 0.3: Spring Data MongoDB (MongoRepository, MongoTemplate) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-mongodb</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

```
movie-service/src/main/java/com/fudn/movieservice/
├── MovieServiceApplication.java
├── config/DataSeeder.java
├── controller/{GenreController, RoomController, MovieController, ShowtimeController}.java
├── dto/{GenreRequest, GenreResponse, RoomRequest, RoomResponse, MovieRequest, MovieResponse,
│        ShowtimeRequest, ShowtimeResponse}.java
├── exception/   (copy 3 file từ customer-service, đổi package)
├── model/{Genre, CinemaRoom, Movie, Showtime, RoomType, RoomStatus, AgeRating, MovieStatus, ShowtimeStatus}.java
├── repository/{GenreRepository, RoomRepository, MovieRepository, ShowtimeRepository}.java
└── service/{GenreService, RoomService, MovieService, ShowtimeService}.java
movie-service/src/main/resources/application.properties      (KHÔNG có db/migration)
```

### Bước 3.2 (TODO 0.5) – `application.properties`

```properties
spring.application.name=movie-service
server.port=8082

# Spring Boot 4: prefix ket noi doi tu spring.data.mongodb.* sang spring.mongodb.*
# authSource=admin vi user root duoc tao trong database admin
spring.mongodb.uri=mongodb://root:password@localhost:27017/cinema_movie?authSource=admin

# Tao index tu cac annotation @Indexed / @CompoundIndex khi khoi dong
spring.data.mongodb.auto-index-creation=true

# In cau lenh query MongoDB ra log (giong show-sql)
logging.level.org.springframework.data.mongodb.core.MongoTemplate=DEBUG
```

> Không thấy index sau khi chạy (`db.genres.getIndexes()` chỉ có `_id_`) → tính năng tạo index tự động chưa bật. Không ảnh hưởng chức năng vì Service đã tự kiểm tra trùng tên trước khi lưu; có thể tạo tay trong `mongosh`: `db.genres.createIndex({genreName: 1}, {unique: true})`.

### Bước 3.3 (TODO 4.2, 5.1, 6.1) – Enum

```java
// model/RoomType.java
package com.fudn.movieservice.model;
public enum RoomType { STANDARD, THREE_D, IMAX }
```

```java
// model/RoomStatus.java
package com.fudn.movieservice.model;
public enum RoomStatus { ACTIVE, MAINTENANCE }
```

```java
// model/AgeRating.java
package com.fudn.movieservice.model;
/** P: moi lua tuoi, T13/T16/T18: tu 13/16/18 tuoi */
public enum AgeRating { P, T13, T16, T18 }
```

```java
// model/MovieStatus.java
package com.fudn.movieservice.model;
public enum MovieStatus { COMING_SOON, NOW_SHOWING, ENDED }
```

```java
// model/ShowtimeStatus.java
package com.fudn.movieservice.model;
public enum ShowtimeStatus { SCHEDULED, CANCELLED }
```

Enum được lưu trong MongoDB dưới dạng **chuỗi** (`"NOW_SHOWING"`), giống `EnumType.STRING` của JPA.

### Bước 3.4 (TODO 4.2, 5.1, 6.1) – Document

**`model/Genre.java`**

```java
package com.fudn.movieservice.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "genres")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Genre {

    @Id                                  // map sang _id; null khi tao moi -> MongoDB sinh ObjectId
    private String genreId;

    @Indexed(unique = true)
    private String genreName;

    private String description;
}
```

**`model/CinemaRoom.java`**

```java
package com.fudn.movieservice.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "cinema_rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CinemaRoom {

    @Id
    private String roomId;

    @Indexed(unique = true)
    private String roomName;

    private RoomType roomType;
    private Integer seatRows;
    private Integer seatsPerRow;
    private RoomStatus roomStatus;

    /** Gia tri tinh toan - Spring Data map theo field nen KHONG luu vao document */
    public int getTotalSeats() {
        return seatRows * seatsPerRow;
    }
}
```

**`model/Movie.java`** – chỉ lưu `genreId` (tham chiếu), không nhúng cả object Genre:

```java
package com.fudn.movieservice.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

@Document(collection = "movies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Movie {

    @Id
    private String movieId;

    private String title;
    private String description;
    private String director;
    private Integer durationMinutes;
    private String language;
    private AgeRating ageRating;
    private LocalDate releaseDate;

    @Indexed                              // tim phim theo the loai & kiem tra BR03 nhanh hon
    private String genreId;

    private MovieStatus movieStatus;
}
```

**`model/Showtime.java`** – `ticketPrice` lưu kiểu **Decimal128** (mặc định Spring Data lưu `BigDecimal` thành **String**, không cộng/so sánh được trong MongoDB):

```java
package com.fudn.movieservice.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Document(collection = "showtimes")
@CompoundIndex(name = "room_start_idx", def = "{'roomId': 1, 'startTime': 1}")   // phuc vu query trung gio
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Showtime {

    @Id
    private String showtimeId;

    @Indexed
    private String movieId;

    private String roomId;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal ticketPrice;

    private ShowtimeStatus showtimeStatus;
}
```

> `LocalDateTime` được lưu thành kiểu `Date` (UTC) của MongoDB theo **múi giờ của máy chạy service** và đọc ra đổi ngược lại → trong `mongosh` thấy `19:00` (VN) hiển thị là `12:00Z`. Đây là bình thường.

### Bước 3.5 (TODO 4.2, 5.2, 6.2) – Repository

**`repository/GenreRepository.java`**

```java
package com.fudn.movieservice.repository;

import com.fudn.movieservice.model.Genre;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface GenreRepository extends MongoRepository<Genre, String> {
    boolean existsByGenreNameIgnoreCase(String genreName);
    boolean existsByGenreNameIgnoreCaseAndGenreIdNot(String genreName, String genreId);
}
```

**`repository/RoomRepository.java`**

```java
package com.fudn.movieservice.repository;

import com.fudn.movieservice.model.CinemaRoom;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RoomRepository extends MongoRepository<CinemaRoom, String> {
    boolean existsByRoomNameIgnoreCase(String roomName);
    boolean existsByRoomNameIgnoreCaseAndRoomIdNot(String roomName, String roomId);
}
```

**`repository/MovieRepository.java`** – tìm kiếm có điều kiện tùy chọn làm bằng `MongoTemplate` trong Service (Bước 3.8):

```java
package com.fudn.movieservice.repository;

import com.fudn.movieservice.model.Movie;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MovieRepository extends MongoRepository<Movie, String> {
    boolean existsByGenreId(String genreId);
}
```

**`repository/ShowtimeRepository.java`** (TODO 6.2) – Spring Data **tự sinh query** từ tên method. Hai khoảng `[s1, e1)` và `[s2, e2)` giao nhau khi `s1 < e2 AND e1 > s2`:

```java
package com.fudn.movieservice.repository;

import com.fudn.movieservice.model.Showtime;
import com.fudn.movieservice.model.ShowtimeStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ShowtimeRepository extends MongoRepository<Showtime, String> {

    boolean existsByRoomId(String roomId);

    boolean existsByMovieId(String movieId);

    List<Showtime> findAllByOrderByStartTimeAsc();

    List<Showtime> findByMovieIdOrderByStartTimeAsc(String movieId);

    /**
     * Sinh ra query:
     * { roomId: ?0, showtimeStatus: ?1, startTime: { $lt: ?2 }, endTime: { $gt: ?3 }, _id: { $ne: ?4 } }
     *  -> ?2 = endTime cua suat moi, ?3 = startTime cua suat moi, ?4 = id can bo qua khi update
     */
    long countByRoomIdAndShowtimeStatusAndStartTimeLessThanAndEndTimeGreaterThanAndShowtimeIdNot(
            String roomId, ShowtimeStatus status, LocalDateTime newEndTime, LocalDateTime newStartTime,
            String excludeShowtimeId);
}
```

> Viết bằng `@Query` JSON tương đương:
> `@Query(value = "{ 'roomId': ?0, 'showtimeStatus': ?1, 'startTime': { $lt: ?2 }, 'endTime': { $gt: ?3 }, '_id': { $ne: ?4 } }", count = true)`

### Bước 3.6 – DTO (ID là `String`)

**`dto/GenreRequest.java`** & **`dto/GenreResponse.java`**

```java
package com.fudn.movieservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GenreRequest(
        @NotBlank(message = "Genre name is required") @Size(max = 50) String genreName,
        @Size(max = 255) String description) {
}
```

```java
package com.fudn.movieservice.dto;

import com.fudn.movieservice.model.Genre;

public record GenreResponse(String genreId, String genreName, String description) {
    public static GenreResponse from(Genre g) {
        return new GenreResponse(g.getGenreId(), g.getGenreName(), g.getDescription());
    }
}
```

**`dto/RoomRequest.java`** & **`dto/RoomResponse.java`**

```java
package com.fudn.movieservice.dto;

import com.fudn.movieservice.model.RoomStatus;
import com.fudn.movieservice.model.RoomType;
import jakarta.validation.constraints.*;

public record RoomRequest(
        @NotBlank(message = "Room name is required") @Size(max = 50) String roomName,
        @NotNull(message = "Room type is required") RoomType roomType,
        @NotNull @Min(value = 1, message = "seatRows must be 1-26") @Max(value = 26, message = "seatRows must be 1-26") Integer seatRows,
        @NotNull @Min(value = 1, message = "seatsPerRow must be 1-30") @Max(value = 30, message = "seatsPerRow must be 1-30") Integer seatsPerRow,
        @NotNull(message = "Room status is required") RoomStatus roomStatus) {
}
```

```java
package com.fudn.movieservice.dto;

import com.fudn.movieservice.model.CinemaRoom;
import com.fudn.movieservice.model.RoomStatus;
import com.fudn.movieservice.model.RoomType;

public record RoomResponse(String roomId, String roomName, RoomType roomType,
                           int seatRows, int seatsPerRow, int totalSeats, RoomStatus roomStatus) {
    public static RoomResponse from(CinemaRoom r) {
        return new RoomResponse(r.getRoomId(), r.getRoomName(), r.getRoomType(),
                r.getSeatRows(), r.getSeatsPerRow(), r.getTotalSeats(), r.getRoomStatus());
    }
}
```

**`dto/MovieRequest.java`** & **`dto/MovieResponse.java`** – `genreName` không có trong document `movies`, Service tra từ collection `genres` rồi truyền vào:

```java
package com.fudn.movieservice.dto;

import com.fudn.movieservice.model.AgeRating;
import com.fudn.movieservice.model.MovieStatus;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record MovieRequest(
        @NotBlank(message = "Title is required") @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @Size(max = 100) String director,
        @NotNull(message = "Duration is required")
        @Min(value = 30, message = "Duration must be 30-300 minutes")
        @Max(value = 300, message = "Duration must be 30-300 minutes") Integer durationMinutes,
        @Size(max = 50) String language,
        @NotNull(message = "Age rating is required") AgeRating ageRating,
        LocalDate releaseDate,
        @NotBlank(message = "Genre id is required") String genreId,
        @NotNull(message = "Movie status is required") MovieStatus movieStatus) {
}
```

```java
package com.fudn.movieservice.dto;

import com.fudn.movieservice.model.AgeRating;
import com.fudn.movieservice.model.Movie;
import com.fudn.movieservice.model.MovieStatus;

import java.time.LocalDate;

public record MovieResponse(String movieId, String title, String description, String director,
                            Integer durationMinutes, String language, AgeRating ageRating,
                            LocalDate releaseDate, String genreId, String genreName, MovieStatus movieStatus) {
    public static MovieResponse from(Movie m, String genreName) {
        return new MovieResponse(m.getMovieId(), m.getTitle(), m.getDescription(), m.getDirector(),
                m.getDurationMinutes(), m.getLanguage(), m.getAgeRating(), m.getReleaseDate(),
                m.getGenreId(), genreName, m.getMovieStatus());
    }
}
```

**`dto/ShowtimeRequest.java`** & **`dto/ShowtimeResponse.java`** – response ghép thông tin từ 3 collection (`showtimes` + `movies` + `cinema_rooms`) để Booking Service có đủ `movieTitle`, `roomName`, `seatRows`, `seatsPerRow`:

```java
package com.fudn.movieservice.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShowtimeRequest(
        @NotBlank(message = "Movie id is required") String movieId,
        @NotBlank(message = "Room id is required") String roomId,
        @NotNull(message = "Start time is required")
        @Future(message = "Start time must be in the future") LocalDateTime startTime,
        @NotNull(message = "Ticket price is required")
        @DecimalMin(value = "10000", message = "Ticket price must be at least 10,000")
        @DecimalMax(value = "1000000", message = "Ticket price must not exceed 1,000,000") BigDecimal ticketPrice) {
}
```

```java
package com.fudn.movieservice.dto;

import com.fudn.movieservice.model.CinemaRoom;
import com.fudn.movieservice.model.Movie;
import com.fudn.movieservice.model.Showtime;
import com.fudn.movieservice.model.ShowtimeStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShowtimeResponse(String showtimeId, String movieId, String movieTitle,
                               String roomId, String roomName, int seatRows, int seatsPerRow,
                               LocalDateTime startTime, LocalDateTime endTime,
                               BigDecimal ticketPrice, ShowtimeStatus showtimeStatus) {
    public static ShowtimeResponse from(Showtime s, Movie movie, CinemaRoom room) {
        return new ShowtimeResponse(s.getShowtimeId(),
                movie.getMovieId(), movie.getTitle(),
                room.getRoomId(), room.getRoomName(), room.getSeatRows(), room.getSeatsPerRow(),
                s.getStartTime(), s.getEndTime(), s.getTicketPrice(), s.getShowtimeStatus());
    }
}
```

### Bước 3.7 (TODO 4.3) – `GenreService` & `RoomService`

**`service/GenreService.java`**

```java
package com.fudn.movieservice.service;

import com.fudn.movieservice.dto.GenreRequest;
import com.fudn.movieservice.dto.GenreResponse;
import com.fudn.movieservice.exception.ApiException;
import com.fudn.movieservice.model.Genre;
import com.fudn.movieservice.repository.GenreRepository;
import com.fudn.movieservice.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GenreService {

    private final GenreRepository genreRepository;
    private final MovieRepository movieRepository;

    public List<GenreResponse> getAll() {
        return genreRepository.findAll(Sort.by("genreName")).stream().map(GenreResponse::from).toList();
    }

    public GenreResponse getById(String id) {
        return GenreResponse.from(find(id));
    }

    public GenreResponse create(GenreRequest request) {
        if (genreRepository.existsByGenreNameIgnoreCase(request.genreName())) {
            throw ApiException.conflict("Genre name already exists: " + request.genreName());
        }
        Genre genre = new Genre();                 // genreId = null -> MongoDB sinh ObjectId
        genre.setGenreName(request.genreName());
        genre.setDescription(request.description());
        return GenreResponse.from(genreRepository.save(genre));
    }

    public GenreResponse update(String id, GenreRequest request) {
        Genre genre = find(id);
        if (genreRepository.existsByGenreNameIgnoreCaseAndGenreIdNot(request.genreName(), id)) {
            throw ApiException.conflict("Genre name already exists: " + request.genreName());
        }
        genre.setGenreName(request.genreName());
        genre.setDescription(request.description());
        return GenreResponse.from(genreRepository.save(genre));
    }

    public void delete(String id) {
        Genre genre = find(id);
        if (movieRepository.existsByGenreId(id)) {             // BR03
            throw ApiException.conflict("Cannot delete genre that still has movies");
        }
        genreRepository.delete(genre);
    }

    /** Dung chung cho MovieService de kiem tra BR15 */
    Genre find(String id) {
        return genreRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Genre not found with id: " + id));
    }
}
```

**`service/RoomService.java`**

```java
package com.fudn.movieservice.service;

import com.fudn.movieservice.dto.RoomRequest;
import com.fudn.movieservice.dto.RoomResponse;
import com.fudn.movieservice.exception.ApiException;
import com.fudn.movieservice.model.CinemaRoom;
import com.fudn.movieservice.repository.RoomRepository;
import com.fudn.movieservice.repository.ShowtimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final ShowtimeRepository showtimeRepository;

    public List<RoomResponse> getAll() {
        return roomRepository.findAll(Sort.by("roomName")).stream().map(RoomResponse::from).toList();
    }

    public RoomResponse getById(String id) {
        return RoomResponse.from(find(id));
    }

    public RoomResponse create(RoomRequest request) {
        if (roomRepository.existsByRoomNameIgnoreCase(request.roomName())) {
            throw ApiException.conflict("Room name already exists: " + request.roomName());
        }
        CinemaRoom room = new CinemaRoom();
        apply(room, request);
        return RoomResponse.from(roomRepository.save(room));
    }

    public RoomResponse update(String id, RoomRequest request) {
        CinemaRoom room = find(id);
        if (roomRepository.existsByRoomNameIgnoreCaseAndRoomIdNot(request.roomName(), id)) {
            throw ApiException.conflict("Room name already exists: " + request.roomName());
        }
        apply(room, request);
        return RoomResponse.from(roomRepository.save(room));
    }

    public void delete(String id) {
        CinemaRoom room = find(id);
        if (showtimeRepository.existsByRoomId(id)) {             // BR03
            throw ApiException.conflict("Cannot delete room that already has showtimes");
        }
        roomRepository.delete(room);
    }

    CinemaRoom find(String id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Room not found with id: " + id));
    }

    private void apply(CinemaRoom room, RoomRequest request) {
        room.setRoomName(request.roomName());
        room.setRoomType(request.roomType());
        room.setSeatRows(request.seatRows());
        room.setSeatsPerRow(request.seatsPerRow());
        room.setRoomStatus(request.roomStatus());
    }
}
```

### Bước 3.8 (TODO 5.2, 5.3) – `service/MovieService.java`

```java
package com.fudn.movieservice.service;

import com.fudn.movieservice.dto.MovieRequest;
import com.fudn.movieservice.dto.MovieResponse;
import com.fudn.movieservice.exception.ApiException;
import com.fudn.movieservice.model.Genre;
import com.fudn.movieservice.model.Movie;
import com.fudn.movieservice.model.MovieStatus;
import com.fudn.movieservice.repository.GenreRepository;
import com.fudn.movieservice.repository.MovieRepository;
import com.fudn.movieservice.repository.ShowtimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MovieService {

    private final MovieRepository movieRepository;
    private final GenreRepository genreRepository;
    private final ShowtimeRepository showtimeRepository;
    private final GenreService genreService;
    private final MongoTemplate mongoTemplate;

    // TODO 5.2: tim kiem dong bang Criteria - tham so nao null thi bo qua
    public List<MovieResponse> search(String keyword, String genreId, MovieStatus status) {
        Query query = new Query();
        if (keyword != null && !keyword.isBlank()) {
            // regex khong phan biet hoa thuong; Pattern.quote de ky tu dac biet ( . * ? ) khong bi hieu la regex
            query.addCriteria(Criteria.where("title").regex(Pattern.quote(keyword.trim()), "i"));
        }
        if (genreId != null && !genreId.isBlank()) {
            query.addCriteria(Criteria.where("genreId").is(genreId));
        }
        if (status != null) {
            query.addCriteria(Criteria.where("movieStatus").is(status));
        }
        query.with(Sort.by("title"));
        return toResponses(mongoTemplate.find(query, Movie.class));
    }

    public MovieResponse getById(String id) {
        Movie movie = find(id);
        return MovieResponse.from(movie, genreService.find(movie.getGenreId()).getGenreName());
    }

    public MovieResponse create(MovieRequest request) {
        Movie movie = new Movie();
        Genre genre = apply(movie, request);
        return MovieResponse.from(movieRepository.save(movie), genre.getGenreName());
    }

    public MovieResponse update(String id, MovieRequest request) {
        Movie movie = find(id);
        Genre genre = apply(movie, request);
        return MovieResponse.from(movieRepository.save(movie), genre.getGenreName());
    }

    public void delete(String id) {
        Movie movie = find(id);
        if (showtimeRepository.existsByMovieId(id)) {            // BR03
            throw ApiException.conflict("Cannot delete movie that already has showtimes. Set status to ENDED instead.");
        }
        movieRepository.delete(movie);
    }

    Movie find(String id) {
        return movieRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Movie not found with id: " + id));
    }

    /** Application-side join: doc tat ca genre 1 lan, tra ten theo genreId (tranh N+1 query) */
    private List<MovieResponse> toResponses(List<Movie> movies) {
        Map<String, String> genreNames = genreRepository.findAll().stream()
                .collect(Collectors.toMap(Genre::getGenreId, Genre::getGenreName));
        return movies.stream()
                .map(m -> MovieResponse.from(m, genreNames.get(m.getGenreId())))
                .toList();
    }

    private Genre apply(Movie movie, MovieRequest request) {
        Genre genre = genreService.find(request.genreId());     // BR15: 404 neu genre khong ton tai
        movie.setTitle(request.title());
        movie.setDescription(request.description());
        movie.setDirector(request.director());
        movie.setDurationMinutes(request.durationMinutes());
        movie.setLanguage(request.language());
        movie.setAgeRating(request.ageRating());
        movie.setReleaseDate(request.releaseDate());
        movie.setGenreId(genre.getGenreId());
        movie.setMovieStatus(request.movieStatus());
        return genre;
    }
}
```

> **Vì sao không nhúng (embed) Genre vào Movie?** Nhúng giúp đọc nhanh hơn (không cần join) nhưng khi đổi tên Genre phải cập nhật **mọi** Movie liên quan. Bài này chọn **tham chiếu** cho đơn giản; phần nhúng + đồng bộ là bài **Bonus**.

### Bước 3.9 (TODO 6.3, 6.4) – `service/ShowtimeService.java`

```java
package com.fudn.movieservice.service;

import com.fudn.movieservice.dto.ShowtimeRequest;
import com.fudn.movieservice.dto.ShowtimeResponse;
import com.fudn.movieservice.exception.ApiException;
import com.fudn.movieservice.model.*;
import com.fudn.movieservice.repository.MovieRepository;
import com.fudn.movieservice.repository.RoomRepository;
import com.fudn.movieservice.repository.ShowtimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShowtimeService {

    /** Gia tri khong trung voi _id nao -> dung khi tao moi (khong loai tru ban ghi nao) */
    private static final String NO_EXCLUDE = "";

    private final ShowtimeRepository showtimeRepository;
    private final MovieRepository movieRepository;
    private final RoomRepository roomRepository;
    private final MovieService movieService;
    private final RoomService roomService;

    // TODO 6.4: loc theo movieId va/hoac ngay chieu
    public List<ShowtimeResponse> search(String movieId, LocalDate date) {
        List<Showtime> showtimes = (movieId == null || movieId.isBlank())
                ? showtimeRepository.findAllByOrderByStartTimeAsc()
                : showtimeRepository.findByMovieIdOrderByStartTimeAsc(movieId);
        List<Showtime> filtered = showtimes.stream()
                .filter(s -> date == null || s.getStartTime().toLocalDate().equals(date))
                .toList();
        return toResponses(filtered);
    }

    public ShowtimeResponse getById(String id) {
        Showtime s = find(id);
        return ShowtimeResponse.from(s, movieService.find(s.getMovieId()), roomService.find(s.getRoomId()));
    }

    // TODO 6.3
    public ShowtimeResponse create(ShowtimeRequest request) {
        Showtime showtime = new Showtime();
        showtime.setShowtimeStatus(ShowtimeStatus.SCHEDULED);
        return apply(showtime, request, NO_EXCLUDE);
    }

    public ShowtimeResponse update(String id, ShowtimeRequest request) {
        Showtime showtime = find(id);
        if (showtime.getShowtimeStatus() == ShowtimeStatus.CANCELLED) {
            throw ApiException.badRequest("Cannot update a cancelled showtime");
        }
        return apply(showtime, request, id);
    }

    // TODO 6.4 – BR06: soft delete
    public void cancel(String id) {
        Showtime showtime = find(id);
        showtime.setShowtimeStatus(ShowtimeStatus.CANCELLED);
        showtimeRepository.save(showtime);
    }

    private Showtime find(String id) {
        return showtimeRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Showtime not found with id: " + id));
    }

    private ShowtimeResponse apply(Showtime showtime, ShowtimeRequest request, String excludeId) {
        Movie movie = movieService.find(request.movieId());        // BR15
        CinemaRoom room = roomService.find(request.roomId());      // BR15

        // BR04
        if (movie.getMovieStatus() == MovieStatus.ENDED) {
            throw ApiException.badRequest("Movie '" + movie.getTitle() + "' has ENDED and cannot be scheduled");
        }
        if (room.getRoomStatus() != RoomStatus.ACTIVE) {
            throw ApiException.badRequest("Room '" + room.getRoomName() + "' is not ACTIVE");
        }
        if (!request.startTime().isAfter(LocalDateTime.now())) {
            throw ApiException.badRequest("Start time must be in the future");
        }
        LocalDateTime endTime = request.startTime().plusMinutes(movie.getDurationMinutes());

        // BR05
        long overlaps = showtimeRepository
                .countByRoomIdAndShowtimeStatusAndStartTimeLessThanAndEndTimeGreaterThanAndShowtimeIdNot(
                        room.getRoomId(), ShowtimeStatus.SCHEDULED, endTime, request.startTime(), excludeId);
        if (overlaps > 0) {
            throw ApiException.conflict("Room '" + room.getRoomName() + "' already has a showtime between "
                    + request.startTime() + " and " + endTime);
        }

        showtime.setMovieId(movie.getMovieId());
        showtime.setRoomId(room.getRoomId());
        showtime.setStartTime(request.startTime());
        showtime.setEndTime(endTime);
        showtime.setTicketPrice(request.ticketPrice());
        return ShowtimeResponse.from(showtimeRepository.save(showtime), movie, room);
    }

    /** Application-side join cho danh sach: lay movies & rooms lien quan bang 2 query findAllById */
    private List<ShowtimeResponse> toResponses(List<Showtime> showtimes) {
        Map<String, Movie> movies = movieRepository
                .findAllById(showtimes.stream().map(Showtime::getMovieId).distinct().toList())
                .stream().collect(Collectors.toMap(Movie::getMovieId, Function.identity()));
        Map<String, CinemaRoom> rooms = roomRepository
                .findAllById(showtimes.stream().map(Showtime::getRoomId).distinct().toList())
                .stream().collect(Collectors.toMap(CinemaRoom::getRoomId, Function.identity()));
        return showtimes.stream()
                .map(s -> ShowtimeResponse.from(s, movies.get(s.getMovieId()), rooms.get(s.getRoomId())))
                .toList();
    }
}
```

### Bước 3.10 (TODO 4.1) – `config/DataSeeder.java`

Thay cho Flyway `V2__seed.sql`. Chạy **sau khi** ứng dụng khởi động; nếu collection `genres` đã có dữ liệu thì bỏ qua → chạy lại app không bị nhân đôi. ID cố định (chuỗi 24 hex hợp lệ – Spring tự đổi sang `ObjectId`) để Postman dùng được. Có sẵn dữ liệu "xấu" để test lỗi: Movie `…204` `ENDED`, Room `…104` `MAINTENANCE`, Showtime `…305` `CANCELLED`.

```java
package com.fudn.movieservice.config;

import com.fudn.movieservice.model.*;
import com.fudn.movieservice.repository.GenreRepository;
import com.fudn.movieservice.repository.MovieRepository;
import com.fudn.movieservice.repository.RoomRepository;
import com.fudn.movieservice.repository.ShowtimeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    // ---- ID co dinh (ObjectId 24 hex) ----
    public static final String GENRE_ACTION = "66f000000000000000000001";
    public static final String GENRE_ANIMATION = "66f000000000000000000002";
    public static final String GENRE_HORROR = "66f000000000000000000003";
    public static final String GENRE_ROMANCE = "66f000000000000000000004";
    public static final String GENRE_SCIFI = "66f000000000000000000005";

    public static final String ROOM_01 = "66f100000000000000000001";
    public static final String ROOM_02 = "66f100000000000000000002";
    public static final String ROOM_IMAX = "66f100000000000000000003";
    public static final String ROOM_04_MAINTENANCE = "66f100000000000000000004";

    public static final String MOVIE_GALAXY = "66f200000000000000000001";
    public static final String MOVIE_HAUNTED = "66f200000000000000000002";
    public static final String MOVIE_ROBOT = "66f200000000000000000003";
    public static final String MOVIE_SUMMER_ENDED = "66f200000000000000000004";

    private final GenreRepository genreRepository;
    private final RoomRepository roomRepository;
    private final MovieRepository movieRepository;
    private final ShowtimeRepository showtimeRepository;

    @Override
    public void run(String... args) {
        if (genreRepository.count() > 0) {
            log.info("MongoDB already has seed data - skip seeding");
            return;
        }

        genreRepository.saveAll(List.of(
                new Genre(GENRE_ACTION, "Hành động", "Phim hành động, võ thuật"),
                new Genre(GENRE_ANIMATION, "Hoạt hình", "Phim hoạt hình cho mọi lứa tuổi"),
                new Genre(GENRE_HORROR, "Kinh dị", "Phim kinh dị, giật gân"),
                new Genre(GENRE_ROMANCE, "Tình cảm", "Phim tình cảm, lãng mạn"),
                new Genre(GENRE_SCIFI, "Khoa học viễn tưởng", "Phim khoa học viễn tưởng")));

        roomRepository.saveAll(List.of(
                new CinemaRoom(ROOM_01, "Room 01", RoomType.STANDARD, 8, 10, RoomStatus.ACTIVE),
                new CinemaRoom(ROOM_02, "Room 02", RoomType.THREE_D, 6, 8, RoomStatus.ACTIVE),
                new CinemaRoom(ROOM_IMAX, "IMAX 01", RoomType.IMAX, 10, 12, RoomStatus.ACTIVE),
                new CinemaRoom(ROOM_04_MAINTENANCE, "Room 04", RoomType.STANDARD, 5, 8, RoomStatus.MAINTENANCE)));

        movieRepository.saveAll(List.of(
                new Movie(MOVIE_GALAXY, "Galaxy Rangers", "Đội biệt kích không gian bảo vệ dải ngân hà.",
                        "John Carter", 125, "English", AgeRating.T13, LocalDate.of(2026, 9, 20),
                        GENRE_SCIFI, MovieStatus.NOW_SHOWING),
                new Movie(MOVIE_HAUNTED, "Ngôi Nhà Ma Ám", "Một gia đình chuyển đến căn nhà cổ ở Đà Lạt.",
                        "Trần Hữu Tấn", 100, "Tiếng Việt", AgeRating.T18, LocalDate.of(2026, 9, 27),
                        GENRE_HORROR, MovieStatus.NOW_SHOWING),
                new Movie(MOVIE_ROBOT, "Robot Nhỏ Phiêu Lưu Ký", "Chú robot nhỏ đi tìm đường về nhà.",
                        "Anna Lee", 95, "English", AgeRating.P, LocalDate.of(2026, 11, 15),
                        GENRE_ANIMATION, MovieStatus.COMING_SOON),
                new Movie(MOVIE_SUMMER_ENDED, "Mùa Hè Năm Ấy", "Câu chuyện tình đầu tuổi học trò.",
                        "Nguyễn Quang Dũng", 110, "Tiếng Việt", AgeRating.T16, LocalDate.of(2026, 6, 1),
                        GENRE_ROMANCE, MovieStatus.ENDED)));

        showtimeRepository.saveAll(List.of(
                showtime("66f300000000000000000001", MOVIE_GALAXY, ROOM_01, "2026-12-20T19:00", 125, 95000, ShowtimeStatus.SCHEDULED),
                showtime("66f300000000000000000002", MOVIE_GALAXY, ROOM_IMAX, "2026-12-20T20:00", 125, 150000, ShowtimeStatus.SCHEDULED),
                showtime("66f300000000000000000003", MOVIE_HAUNTED, ROOM_02, "2026-12-21T21:00", 100, 95000, ShowtimeStatus.SCHEDULED),
                showtime("66f300000000000000000004", MOVIE_ROBOT, ROOM_01, "2026-12-22T09:00", 95, 75000, ShowtimeStatus.SCHEDULED),
                showtime("66f300000000000000000005", MOVIE_HAUNTED, ROOM_01, "2026-12-23T19:00", 100, 95000, ShowtimeStatus.CANCELLED)));

        log.info("Seeded MongoDB: {} genres, {} rooms, {} movies, {} showtimes",
                genreRepository.count(), roomRepository.count(), movieRepository.count(), showtimeRepository.count());
    }

    private static Showtime showtime(String id, String movieId, String roomId, String start,
                                     int durationMinutes, long price, ShowtimeStatus status) {
        LocalDateTime startTime = LocalDateTime.parse(start);
        return new Showtime(id, movieId, roomId, startTime, startTime.plusMinutes(durationMinutes),
                BigDecimal.valueOf(price), status);
    }
}
```

> Muốn nạp lại dữ liệu mẫu: `docker exec -it cinema-mongo mongosh -u root -p password --authenticationDatabase admin cinema_movie --eval "db.dropDatabase()"` rồi khởi động lại movie-service.

### Bước 3.11 (TODO 4.4, 5.4, 6.5) – Controller (`@PathVariable String id`)

**`controller/GenreController.java`**

```java
package com.fudn.movieservice.controller;

import com.fudn.movieservice.dto.GenreRequest;
import com.fudn.movieservice.dto.GenreResponse;
import com.fudn.movieservice.service.GenreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/genres")
@RequiredArgsConstructor
public class GenreController {

    private final GenreService genreService;

    @GetMapping
    public List<GenreResponse> getAll() {
        return genreService.getAll();
    }

    @GetMapping("/{id}")
    public GenreResponse getById(@PathVariable String id) {
        return genreService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GenreResponse create(@Valid @RequestBody GenreRequest request) {
        return genreService.create(request);
    }

    @PutMapping("/{id}")
    public GenreResponse update(@PathVariable String id, @Valid @RequestBody GenreRequest request) {
        return genreService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        genreService.delete(id);
    }
}
```

**`controller/RoomController.java`**

```java
package com.fudn.movieservice.controller;

import com.fudn.movieservice.dto.RoomRequest;
import com.fudn.movieservice.dto.RoomResponse;
import com.fudn.movieservice.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @GetMapping
    public List<RoomResponse> getAll() {
        return roomService.getAll();
    }

    @GetMapping("/{id}")
    public RoomResponse getById(@PathVariable String id) {
        return roomService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomResponse create(@Valid @RequestBody RoomRequest request) {
        return roomService.create(request);
    }

    @PutMapping("/{id}")
    public RoomResponse update(@PathVariable String id, @Valid @RequestBody RoomRequest request) {
        return roomService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        roomService.delete(id);
    }
}
```

**`controller/MovieController.java`**

```java
package com.fudn.movieservice.controller;

import com.fudn.movieservice.dto.MovieRequest;
import com.fudn.movieservice.dto.MovieResponse;
import com.fudn.movieservice.model.MovieStatus;
import com.fudn.movieservice.service.MovieService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;

    @GetMapping
    public List<MovieResponse> search(@RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) String genreId,
                                      @RequestParam(required = false) MovieStatus status) {
        return movieService.search(keyword, genreId, status);
    }

    @GetMapping("/{id}")
    public MovieResponse getById(@PathVariable String id) {
        return movieService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MovieResponse create(@Valid @RequestBody MovieRequest request) {
        return movieService.create(request);
    }

    @PutMapping("/{id}")
    public MovieResponse update(@PathVariable String id, @Valid @RequestBody MovieRequest request) {
        return movieService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        movieService.delete(id);
    }
}
```

**`controller/ShowtimeController.java`**

```java
package com.fudn.movieservice.controller;

import com.fudn.movieservice.dto.ShowtimeRequest;
import com.fudn.movieservice.dto.ShowtimeResponse;
import com.fudn.movieservice.service.ShowtimeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/showtimes")
@RequiredArgsConstructor
public class ShowtimeController {

    private final ShowtimeService showtimeService;

    @GetMapping
    public List<ShowtimeResponse> search(
            @RequestParam(required = false) String movieId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return showtimeService.search(movieId, date);
    }

    /** Booking Service goi endpoint nay qua OpenFeign */
    @GetMapping("/{id}")
    public ShowtimeResponse getById(@PathVariable String id) {
        return showtimeService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShowtimeResponse create(@Valid @RequestBody ShowtimeRequest request) {
        return showtimeService.create(request);
    }

    @PutMapping("/{id}")
    public ShowtimeResponse update(@PathVariable String id, @Valid @RequestBody ShowtimeRequest request) {
        return showtimeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable String id) {
        showtimeService.cancel(id);
    }
}
```

### Bước 3.12 – Chạy & kiểm tra movie-service

```bash
cd movie-service
mvn spring-boot:run
# log: "Seeded MongoDB: 5 genres, 4 rooms, 4 movies, 5 showtimes"
curl "http://localhost:8082/api/movies?status=NOW_SHOWING"
curl http://localhost:8082/api/showtimes/66f300000000000000000001
```

Xem dữ liệu trực tiếp trong MongoDB:

```bash
docker exec -it cinema-mongo mongosh -u root -p password --authenticationDatabase admin cinema_movie
```

```js
show collections                         // cinema_rooms, genres, movies, showtimes
db.movies.find({ movieStatus: "NOW_SHOWING" }, { title: 1, genreId: 1 })
db.showtimes.findOne()                   // ticketPrice: Decimal128('95000')
db.genres.getIndexes()                   // co index unique genreName
```

> Có thể dùng **MongoDB Compass** kết nối `mongodb://root:password@localhost:27017/?authSource=admin` để xem bằng giao diện.

**✅ Checklist Bước 3:** log seed chạy 1 lần (khởi động lại thấy "skip seeding") · `GET /api/showtimes/66f3…001` trả `seatRows`, `seatsPerRow`, `ticketPrice` · có 4 collection.

**💾 Commit gợi ý:** TODO 0.3, 0.5, 0.6 (movie) → 4.1 – 4.4 → 5.1 – 5.4 → 6.1 – 6.5 – xem bảng ở [Bước 9.4](#bước-9--quy-ước-commit-theo-từng-todo-conventional-commits).

---
## Bước 4 – booking-service với MySQL (F7, F8, F9)

### Bước 4.1 (TODO 0.3, 7.1) – Generate project & `pom.xml`

start.spring.io: Artifact `booking-service`. Dependencies: **Spring Web**, **Spring Data JPA**, **Validation**, **MySQL Driver**, **Flyway Migration**, **Lombok**, **OpenFeign**.
`pom.xml` (phần OpenFeign được đánh dấu TODO 7.1):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.0</version>
        <relativePath/>
    </parent>
    <groupId>com.fudn</groupId>
    <artifactId>booking-service</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>booking-service</name>

    <properties>
        <java.version>21</java.version>
        <!-- TODO 7.1: Spring Cloud release train khop Spring Boot 4.1.x -->
        <spring-cloud.version>2025.1.3</spring-cloud.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-flyway</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-mysql</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <!-- TODO 7.1: OpenFeign -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-openfeign</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <!-- TODO 7.1: BOM quan ly version toan bo thu vien Spring Cloud -->
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <!-- giong customer-service: maven-compiler-plugin (Lombok) + spring-boot-maven-plugin -->
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

```
booking-service/src/main/java/com/fudn/bookingservice/
├── BookingServiceApplication.java          (@EnableFeignClients)
├── client/MovieClient.java
├── controller/BookingController.java
├── dto/{ShowtimeResponse, BookingItemRequest, CreateBookingRequest, BookingDetailResponse,
│        BookingResponse, SeatMapResponse, MovieRevenueResponse, ReportResponse}.java
├── exception/   (copy 3 file, đổi package)
├── model/{Booking, BookingDetail, BookingStatus}.java
├── repository/{BookingRepository, BookingDetailRepository}.java
└── service/BookingService.java
```

### Bước 4.2 – `application.properties` & main class

```properties
spring.application.name=booking-service
server.port=8083

spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.url=jdbc:mysql://localhost:3306/cinema_booking
spring.datasource.username=root
spring.datasource.password=mysql

spring.jpa.hibernate.ddl-auto=none
spring.jpa.open-in-view=false
spring.jpa.show-sql=true

# TODO 7.4: goi TRUC TIEP movie-service (khong qua gateway)
movie.service.url=http://localhost:8082
```

**`BookingServiceApplication.java`**

```java
package com.fudn.bookingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients            // TODO 7.1: quet cac interface @FeignClient
public class BookingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingServiceApplication.class, args);
    }
}
```

### Bước 4.3 (TODO 7.2) – Flyway `V1__init.sql`

```sql
CREATE TABLE booking (
    booking_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_date   DATETIME      NOT NULL,
    total_price    DECIMAL(12,2) NOT NULL,
    customer_id    BIGINT        NOT NULL,   -- tham chieu logic sang customer-service (khong FK)
    booking_status VARCHAR(20)   NOT NULL,
    INDEX idx_booking_customer (customer_id),
    INDEX idx_booking_date (booking_date)
);

CREATE TABLE booking_detail (
    booking_detail_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id        BIGINT        NOT NULL,
    showtime_id       VARCHAR(24)   NOT NULL,  -- ObjectId cua MongoDB (movie-service), tham chieu logic
    seat_code         VARCHAR(5)    NOT NULL,
    price             DECIMAL(10,2) NOT NULL,
    -- snapshot tai thoi diem dat ve
    movie_id          VARCHAR(24)   NOT NULL,
    movie_title       VARCHAR(200)  NOT NULL,
    room_name         VARCHAR(50)   NOT NULL,
    showtime_start    DATETIME      NOT NULL,
    CONSTRAINT fk_detail_booking FOREIGN KEY (booking_id) REFERENCES booking (booking_id),
    INDEX idx_detail_showtime_seat (showtime_id, seat_code)
);
```

> Không đặt `UNIQUE(showtime_id, seat_code)` vì booking bị hủy vẫn giữ detail (lịch sử) và ghế đó phải **bán lại được**. Việc chống trùng ghế (BR09) kiểm tra trong Service (chỉ xét booking `CONFIRMED`).

### Bước 4.4 (TODO 7.3) – Model

```java
// model/BookingStatus.java
package com.fudn.bookingservice.model;
public enum BookingStatus { CONFIRMED, CANCELLED }
```

**`model/Booking.java`**

```java
package com.fudn.bookingservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "booking")
@Getter
@Setter
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bookingId;

    @Column(nullable = false)
    private LocalDateTime bookingDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Column(nullable = false)
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus bookingStatus;

    /** 1 Booking - N BookingDetail; luu Booking se luu luon cac detail (cascade) */
    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("bookingDetailId ASC")
    private List<BookingDetail> details = new ArrayList<>();

    public void addDetail(BookingDetail detail) {
        details.add(detail);
        detail.setBooking(this);
    }
}
```

**`model/BookingDetail.java`**

```java
package com.fudn.bookingservice.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "booking_detail")
@Getter
@Setter
@NoArgsConstructor
public class BookingDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bookingDetailId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(nullable = false, length = 24)
    private String showtimeId;            // ObjectId tu movie-service (MongoDB)

    @Column(nullable = false, length = 5)
    private String seatCode;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    // ---- snapshot tu movie-service ----
    @Column(nullable = false, length = 24)
    private String movieId;

    @Column(nullable = false, length = 200)
    private String movieTitle;

    @Column(nullable = false, length = 50)
    private String roomName;

    @Column(nullable = false)
    private LocalDateTime showtimeStart;
}
```

### Bước 4.5 (TODO 9.1) – Repository

**`repository/BookingRepository.java`**

```java
package com.fudn.bookingservice.repository;

import com.fudn.bookingservice.model.Booking;
import com.fudn.bookingservice.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByCustomerIdOrderByBookingDateDesc(Long customerId);

    List<Booking> findAllByOrderByBookingDateDesc();

    // TODO 9.1: [from, to) va sap xep GIAM DAN
    @Query("""
            select b from Booking b
            where b.bookingStatus = :status
              and b.bookingDate >= :from
              and b.bookingDate < :to
            order by b.bookingDate desc
            """)
    List<Booking> findForReport(@Param("status") BookingStatus status,
                                @Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to);
}
```

**`repository/BookingDetailRepository.java`**

```java
package com.fudn.bookingservice.repository;

import com.fudn.bookingservice.model.BookingDetail;
import com.fudn.bookingservice.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookingDetailRepository extends JpaRepository<BookingDetail, Long> {

    /** Danh sach ghe da ban cua 1 suat chieu (chi tinh booking CONFIRMED) */
    @Query("""
            select d.seatCode from BookingDetail d
            where d.showtimeId = :showtimeId
              and d.booking.bookingStatus = :status
            """)
    List<String> findSeatCodesByShowtime(@Param("showtimeId") String showtimeId,
                                         @Param("status") BookingStatus status);
}
```

### Bước 4.6 (TODO 7.4) – OpenFeign client

**`dto/ShowtimeResponse.java`** – bản sao (contract) của response movie-service. Các ID là **`String`** (ObjectId của MongoDB); `showtimeStatus` để kiểu `String` cho booking-service không phụ thuộc enum của service khác:

```java
package com.fudn.bookingservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShowtimeResponse(String showtimeId, String movieId, String movieTitle,
                               String roomId, String roomName, int seatRows, int seatsPerRow,
                               LocalDateTime startTime, LocalDateTime endTime,
                               BigDecimal ticketPrice, String showtimeStatus) {
}
```

**`client/MovieClient.java`**

```java
package com.fudn.bookingservice.client;

import com.fudn.bookingservice.dto.ShowtimeResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(value = "movie-service", url = "${movie.service.url}")
public interface MovieClient {

    @GetMapping("/api/showtimes/{id}")
    ShowtimeResponse getShowtime(@PathVariable("id") String id);
}
```

> Feign ném `FeignException.NotFound` khi movie-service trả 404, ném `RetryableException` (cũng là `FeignException`) khi không kết nối được → Service sẽ dịch sang `404` và `503`.

### Bước 4.7 – DTO request/response

```java
// dto/BookingItemRequest.java
package com.fudn.bookingservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BookingItemRequest(
        @NotBlank(message = "Showtime id is required") String showtimeId,
        @NotBlank(message = "Seat code is required")
        @Pattern(regexp = "^[A-Z][1-9][0-9]?$", message = "Seat code must look like A1, E10 ...") String seatCode) {
}
```

```java
// dto/CreateBookingRequest.java
package com.fudn.bookingservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateBookingRequest(
        @NotEmpty(message = "Booking must have at least 1 ticket")
        @Size(max = 8, message = "A booking can have at most 8 tickets")      // BR07
        List<@Valid BookingItemRequest> items) {
}
```

```java
// dto/BookingDetailResponse.java
package com.fudn.bookingservice.dto;

import com.fudn.bookingservice.model.BookingDetail;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BookingDetailResponse(String showtimeId, String movieId, String movieTitle, String roomName,
                                    LocalDateTime showtimeStart, String seatCode, BigDecimal price) {
    public static BookingDetailResponse from(BookingDetail d) {
        return new BookingDetailResponse(d.getShowtimeId(), d.getMovieId(), d.getMovieTitle(), d.getRoomName(),
                d.getShowtimeStart(), d.getSeatCode(), d.getPrice());
    }
}
```

```java
// dto/BookingResponse.java
package com.fudn.bookingservice.dto;

import com.fudn.bookingservice.model.Booking;
import com.fudn.bookingservice.model.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record BookingResponse(Long bookingId, LocalDateTime bookingDate, Long customerId,
                              BigDecimal totalPrice, BookingStatus bookingStatus,
                              List<BookingDetailResponse> details) {
    public static BookingResponse from(Booking b) {
        return new BookingResponse(b.getBookingId(), b.getBookingDate(), b.getCustomerId(),
                b.getTotalPrice(), b.getBookingStatus(),
                b.getDetails().stream().map(BookingDetailResponse::from).toList());
    }
}
```

```java
// dto/SeatMapResponse.java
package com.fudn.bookingservice.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SeatMapResponse(String showtimeId, String movieTitle, String roomName, LocalDateTime startTime,
                              int seatRows, int seatsPerRow, int totalSeats, int availableSeats,
                              List<String> bookedSeats) {
}
```

```java
// dto/MovieRevenueResponse.java
package com.fudn.bookingservice.dto;

import java.math.BigDecimal;

public record MovieRevenueResponse(String movieId, String movieTitle, long ticketsSold, BigDecimal revenue) {
}
```

```java
// dto/ReportResponse.java
package com.fudn.bookingservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ReportResponse(LocalDate startDate, LocalDate endDate,
                             long totalBookings, long totalTickets, BigDecimal totalRevenue,
                             List<MovieRevenueResponse> revenueByMovie,
                             List<BookingResponse> bookings) {
}
```

### Bước 4.8 (TODO 7.5, 7.6, 8.1–8.3, 9.2) – `service/BookingService.java`

```java
package com.fudn.bookingservice.service;

import com.fudn.bookingservice.client.MovieClient;
import com.fudn.bookingservice.dto.*;
import com.fudn.bookingservice.exception.ApiException;
import com.fudn.bookingservice.model.Booking;
import com.fudn.bookingservice.model.BookingDetail;
import com.fudn.bookingservice.model.BookingStatus;
import com.fudn.bookingservice.repository.BookingDetailRepository;
import com.fudn.bookingservice.repository.BookingRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingService {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final long CANCEL_BEFORE_HOURS = 2;

    private final BookingRepository bookingRepository;
    private final BookingDetailRepository bookingDetailRepository;
    private final MovieClient movieClient;

    // ======================= F7: SEAT MAP =======================

    // TODO 7.6
    public SeatMapResponse getSeatMap(String showtimeId) {
        ShowtimeResponse st = fetchShowtime(showtimeId);
        List<String> booked = bookingDetailRepository
                .findSeatCodesByShowtime(showtimeId, BookingStatus.CONFIRMED)
                .stream().sorted().toList();
        int totalSeats = st.seatRows() * st.seatsPerRow();
        return new SeatMapResponse(st.showtimeId(), st.movieTitle(), st.roomName(), st.startTime(),
                st.seatRows(), st.seatsPerRow(), totalSeats, totalSeats - booked.size(), booked);
    }

    // ======================= F7: CREATE BOOKING =======================

    // TODO 7.5
    @Transactional
    public BookingResponse create(Long customerId, CreateBookingRequest request) {
        Map<String, ShowtimeResponse> showtimeCache = new HashMap<>();   // moi showtime chi goi Feign 1 lan
        Map<String, Set<String>> bookedSeatCache = new HashMap<>();
        Set<String> requestedSeats = new HashSet<>();

        Booking booking = new Booking();
        booking.setCustomerId(customerId);
        booking.setBookingDate(LocalDateTime.now());
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        BigDecimal total = BigDecimal.ZERO;

        for (BookingItemRequest item : request.items()) {
            String seat = item.seatCode();

            // BR07: khong trung ghe trong cung request
            if (!requestedSeats.add(item.showtimeId() + "#" + seat)) {
                throw ApiException.badRequest("Duplicate seat " + seat + " of showtime " + item.showtimeId() + " in request");
            }

            // BR08 + BR14: goi movie-service qua OpenFeign
            ShowtimeResponse st = showtimeCache.computeIfAbsent(item.showtimeId(), this::fetchShowtime);
            validateShowtime(st);
            validateSeat(seat, st);

            // BR09: ghe da ban cho booking CONFIRMED khac?
            Set<String> taken = bookedSeatCache.computeIfAbsent(st.showtimeId(),
                    id -> new HashSet<>(bookingDetailRepository.findSeatCodesByShowtime(id, BookingStatus.CONFIRMED)));
            if (taken.contains(seat)) {
                throw ApiException.conflict("Seat " + seat + " of showtime " + st.showtimeId() + " is already booked");
            }

            // BR10: gia lay tu server + snapshot thong tin phim
            BookingDetail detail = new BookingDetail();
            detail.setShowtimeId(st.showtimeId());
            detail.setSeatCode(seat);
            detail.setPrice(st.ticketPrice());
            detail.setMovieId(st.movieId());
            detail.setMovieTitle(st.movieTitle());
            detail.setRoomName(st.roomName());
            detail.setShowtimeStart(st.startTime());
            booking.addDetail(detail);

            total = total.add(st.ticketPrice());
        }

        booking.setTotalPrice(total);
        Booking saved = bookingRepository.save(booking);           // cascade luu luon details
        log.info("Booking {} created for customer {} with {} ticket(s), total {}",
                saved.getBookingId(), customerId, saved.getDetails().size(), total);
        return BookingResponse.from(saved);
    }

    // ======================= F8: HISTORY & CANCEL =======================

    // TODO 8.1
    public List<BookingResponse> getMyBookings(Long customerId) {
        return bookingRepository.findByCustomerIdOrderByBookingDateDesc(customerId)
                .stream().map(BookingResponse::from).toList();
    }

    public List<BookingResponse> getAll() {
        return bookingRepository.findAllByOrderByBookingDateDesc()
                .stream().map(BookingResponse::from).toList();
    }

    // TODO 8.2
    public BookingResponse getById(Long bookingId, Long userId, String role) {
        return BookingResponse.from(findAccessible(bookingId, userId, role));
    }

    // TODO 8.3
    @Transactional
    public BookingResponse cancel(Long bookingId, Long userId, String role) {
        Booking booking = findAccessible(bookingId, userId, role);
        if (booking.getBookingStatus() != BookingStatus.CONFIRMED) {
            throw ApiException.badRequest("Only CONFIRMED bookings can be cancelled");
        }
        if (!ROLE_ADMIN.equals(role)) {                                     // BR12
            LocalDateTime deadline = LocalDateTime.now().plusHours(CANCEL_BEFORE_HOURS);
            boolean tooLate = booking.getDetails().stream()
                    .anyMatch(d -> d.getShowtimeStart().isBefore(deadline));
            if (tooLate) {
                throw ApiException.badRequest("Booking can only be cancelled at least "
                        + CANCEL_BEFORE_HOURS + " hours before the showtime");
            }
        }
        booking.setBookingStatus(BookingStatus.CANCELLED);
        return BookingResponse.from(bookingRepository.save(booking));
    }

    // ======================= F9: REPORT =======================

    // TODO 9.2
    public ReportResponse report(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {                                    // BR13
            throw ApiException.badRequest("startDate must be before or equal to endDate");
        }
        List<Booking> bookings = bookingRepository.findForReport(BookingStatus.CONFIRMED,
                startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay());

        BigDecimal totalRevenue = BigDecimal.ZERO;
        long totalTickets = 0;
        Map<String, MovieRevenueResponse> byMovie = new HashMap<>();

        for (Booking b : bookings) {
            totalRevenue = totalRevenue.add(b.getTotalPrice());
            totalTickets += b.getDetails().size();
            for (BookingDetail d : b.getDetails()) {
                byMovie.merge(d.getMovieId(),
                        new MovieRevenueResponse(d.getMovieId(), d.getMovieTitle(), 1, d.getPrice()),
                        (a, c) -> new MovieRevenueResponse(a.movieId(), a.movieTitle(),
                                a.ticketsSold() + c.ticketsSold(), a.revenue().add(c.revenue())));
            }
        }

        // Sap xep GIAM DAN theo doanh thu, bang nhau thi theo so ve
        List<MovieRevenueResponse> revenueByMovie = byMovie.values().stream()
                .sorted(Comparator.comparing(MovieRevenueResponse::revenue).reversed()
                        .thenComparing(Comparator.comparingLong(MovieRevenueResponse::ticketsSold).reversed()))
                .toList();

        return new ReportResponse(startDate, endDate, bookings.size(), totalTickets, totalRevenue,
                revenueByMovie, bookings.stream().map(BookingResponse::from).toList()); // da desc tu query
    }

    // ======================= HELPER =======================

    private ShowtimeResponse fetchShowtime(String showtimeId) {
        try {
            return movieClient.getShowtime(showtimeId);
        } catch (FeignException.NotFound e) {
            throw ApiException.notFound("Showtime not found with id: " + showtimeId);
        } catch (FeignException e) {
            log.error("Cannot call movie-service: {}", e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Movie service is unavailable. Please try again later.");
        }
    }

    private void validateShowtime(ShowtimeResponse st) {
        if (!"SCHEDULED".equals(st.showtimeStatus())) {
            throw ApiException.badRequest("Showtime " + st.showtimeId() + " is not available (" + st.showtimeStatus() + ")");
        }
        if (!st.startTime().isAfter(LocalDateTime.now())) {
            throw ApiException.badRequest("Showtime " + st.showtimeId() + " has already started");
        }
    }

    /** Seat "E5": hang E (index 4) < seatRows va so 5 <= seatsPerRow */
    private void validateSeat(String seat, ShowtimeResponse st) {
        int rowIndex = seat.charAt(0) - 'A';
        int number = Integer.parseInt(seat.substring(1));
        if (rowIndex >= st.seatRows() || number > st.seatsPerRow()) {
            char lastRow = (char) ('A' + st.seatRows() - 1);
            throw ApiException.badRequest("Seat " + seat + " does not exist in room " + st.roomName()
                    + " (rows A-" + lastRow + ", seats 1-" + st.seatsPerRow() + ")");
        }
    }

    /** BR11: Customer chi truy cap booking cua minh, Admin truy cap tat ca */
    private Booking findAccessible(Long bookingId, Long userId, String role) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> ApiException.notFound("Booking not found with id: " + bookingId));
        if (!ROLE_ADMIN.equals(role) && !booking.getCustomerId().equals(userId)) {
            throw ApiException.forbidden("You can only access your own bookings");
        }
        return booking;
    }
}
```

> **Vì sao `@Transactional` bao cả lời gọi Feign?** Để đơn giản cho bài lab. Thực tế nên gọi Feign **trước**, rồi mới mở transaction ngắn để ghi DB (tránh giữ connection DB khi chờ mạng).

### Bước 4.9 (TODO 7.7, 8.4, 9.3) – `controller/BookingController.java`

```java
package com.fudn.bookingservice.controller;

import com.fudn.bookingservice.dto.BookingResponse;
import com.fudn.bookingservice.dto.CreateBookingRequest;
import com.fudn.bookingservice.dto.ReportResponse;
import com.fudn.bookingservice.dto.SeatMapResponse;
import com.fudn.bookingservice.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private static final String USER_ID = "X-User-Id";
    private static final String USER_ROLE = "X-User-Role";

    private final BookingService bookingService;

    // Public
    @GetMapping("/showtimes/{showtimeId}/seats")
    public SeatMapResponse getSeatMap(@PathVariable String showtimeId) {
        return bookingService.getSeatMap(showtimeId);
    }

    // CUSTOMER
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(@RequestHeader(USER_ID) Long userId,
                                  @Valid @RequestBody CreateBookingRequest request) {
        return bookingService.create(userId, request);
    }

    // CUSTOMER
    @GetMapping("/my")
    public List<BookingResponse> getMyBookings(@RequestHeader(USER_ID) Long userId) {
        return bookingService.getMyBookings(userId);
    }

    // ADMIN
    @GetMapping
    public List<BookingResponse> getAll() {
        return bookingService.getAll();
    }

    // ADMIN
    @GetMapping("/report")
    public ReportResponse report(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return bookingService.report(startDate, endDate);
    }

    // Owner hoac ADMIN
    @GetMapping("/{id}")
    public BookingResponse getById(@PathVariable Long id,
                                   @RequestHeader(USER_ID) Long userId,
                                   @RequestHeader(value = USER_ROLE, required = false) String role) {
        return bookingService.getById(id, userId, role);
    }

    // Owner hoac ADMIN
    @PutMapping("/{id}/cancel")
    public BookingResponse cancel(@PathVariable Long id,
                                  @RequestHeader(USER_ID) Long userId,
                                  @RequestHeader(value = USER_ROLE, required = false) String role) {
        return bookingService.cancel(id, userId, role);
    }
}
```

### Bước 4.10 – Chạy booking-service

```bash
cd booking-service
mvn spring-boot:run
curl http://localhost:8083/api/bookings/showtimes/66f300000000000000000001/seats   # cần movie-service đang chạy
```

**✅ Checklist Bước 4:** migration chạy · seat map trả `totalSeats = 80` cho showtime `66f3…001` · cột `showtime_id` trong MySQL là `VARCHAR(24)` · tắt movie-service → seat map trả `503`.

**💾 Commit gợi ý:** TODO 0.3, 0.5, 0.6 (booking) → 7.1 – 7.7 → 8.1 – 8.4 → 9.1 – 9.3 – xem bảng ở [Bước 9.4](#bước-9--quy-ước-commit-theo-từng-todo-conventional-commits).

---
## Bước 5 – api-gateway (F10)

### Bước 5.1 (TODO 0.4) – Generate project & `pom.xml`

start.spring.io: Artifact `api-gateway`, package `com.fudn.gateway`. Dependencies: **Gateway** (bản *Server Web MVC*), **OAuth2 Resource Server**, **Spring Boot Actuator**.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.0</version>
        <relativePath/>
    </parent>
    <groupId>com.fudn</groupId>
    <artifactId>api-gateway</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>api-gateway</name>

    <properties>
        <java.version>21</java.version>
        <spring-cloud.version>2025.1.3</spring-cloud.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <!-- Spring Cloud Gateway ban Servlet (Web MVC) -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway-server-webmvc</artifactId>
        </dependency>
        <!-- Gateway la OAuth2 Resource Server: doc & verify Bearer JWT -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security-oauth2-resource-server</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

```
api-gateway/src/main/java/com/fudn/gateway/
├── ApiGatewayApplication.java
├── config/SecurityConfig.java
├── filter/UserHeaderFilter.java
└── routes/Routes.java
```

### Bước 5.2 (TODO 10.1) – `application.properties`

```properties
spring.application.name=api-gateway
server.port=9000

# Dia chi 3 service phia sau
services.customer.url=http://localhost:8081
services.movie.url=http://localhost:8082
services.booking.url=http://localhost:8083

# PHAI TRUNG voi customer-service
app.jwt.secret=fu-cinema-booking-system-secret-key-2026-mss301

management.endpoints.web.exposure.include=health
```

### Bước 5.3 (TODO 10.2) – `filter/UserHeaderFilter.java`

Hàm *before filter* chạy trước khi Gateway chuyển tiếp request: xóa mọi header `X-User-*` mà client tự gửi (chống giả mạo), sau đó nếu request đã xác thực thì đọc JWT và chèn lại.

```java
package com.fudn.gateway.filter;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.function.ServerRequest;

import java.util.function.Function;

public final class UserHeaderFilter {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_EMAIL = "X-User-Email";
    public static final String USER_ROLE = "X-User-Role";

    private UserHeaderFilter() {
    }

    public static Function<ServerRequest, ServerRequest> forwardUserInfo() {
        return request -> {
            // (1) Xoa header client tu gui len
            ServerRequest.Builder builder = ServerRequest.from(request)
                    .headers(headers -> {
                        headers.remove(USER_ID);
                        headers.remove(USER_EMAIL);
                        headers.remove(USER_ROLE);
                    });

            // (2) Chen lai tu JWT da duoc Spring Security xac thuc
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                Jwt jwt = jwtAuth.getToken();
                builder.header(USER_ID, String.valueOf(jwt.getClaims().get("uid")))
                       .header(USER_EMAIL, jwt.getSubject())
                       .header(USER_ROLE, jwt.getClaimAsString("role"));
            }
            return builder.build();
        };
    }
}
```

### Bước 5.4 (TODO 10.3) – `routes/Routes.java`

```java
package com.fudn.gateway.routes;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static com.fudn.gateway.filter.UserHeaderFilter.forwardUserInfo;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

@Configuration(proxyBeanMethods = false)
public class Routes {

    @Value("${services.customer.url}")
    private String customerServiceUrl;

    @Value("${services.movie.url}")
    private String movieServiceUrl;

    @Value("${services.booking.url}")
    private String bookingServiceUrl;

    @Bean
    public RouterFunction<ServerResponse> customerServiceRoute() {
        return route("customer_service")
                .route(path("/api/auth/**").or(path("/api/customers/**")), http())
                .before(uri(customerServiceUrl))
                .before(forwardUserInfo())
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> movieServiceRoute() {
        return route("movie_service")
                .route(path("/api/genres/**")
                        .or(path("/api/rooms/**"))
                        .or(path("/api/movies/**"))
                        .or(path("/api/showtimes/**")), http())
                .before(uri(movieServiceUrl))
                .before(forwardUserInfo())
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> bookingServiceRoute() {
        return route("booking_service")
                .route(RequestPredicates.path("/api/bookings/**"), http())
                .before(uri(bookingServiceUrl))
                .before(forwardUserInfo())
                .build();
    }
}
```

> `path("/api/movies/**")` khớp cả `/api/movies` lẫn `/api/movies/1` (`**` = 0 hoặc nhiều segment).

### Bước 5.5 (TODO 10.4) – `config/SecurityConfig.java`

Quy tắc được duyệt **từ trên xuống, khớp quy tắc nào trước thì dùng quy tắc đó** → đặt quy tắc cụ thể (public, `/me`, `/my`) **trước** quy tắc tổng quát (`/api/customers/**`, `/api/bookings/**`).

```java
package com.fudn.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // ---------- Public ----------
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/customers/register").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/genres/**", "/api/movies/**", "/api/showtimes/**",
                                "/api/bookings/showtimes/**").permitAll()

                        // ---------- customer-service ----------
                        .requestMatchers("/api/customers/me", "/api/customers/me/**").hasRole("CUSTOMER")
                        .requestMatchers("/api/customers/**").hasRole("ADMIN")

                        // ---------- movie-service (ghi) ----------
                        .requestMatchers("/api/rooms/**", "/api/genres/**", "/api/movies/**", "/api/showtimes/**")
                                .hasRole("ADMIN")

                        // ---------- booking-service ----------
                        .requestMatchers(HttpMethod.POST, "/api/bookings").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/bookings/my").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/bookings", "/api/bookings/report").hasRole("ADMIN")
                        .requestMatchers("/api/bookings/**").authenticated()   // /{id}, /{id}/cancel

                        .anyRequest().denyAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(jwtDecoder())
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    /** Verify chu ky HS256 bang CUNG secret voi customer-service; tu kiem tra exp. */
    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKey key = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /** Claim "role": "ADMIN" -> authority "ROLE_ADMIN" de dung hasRole("ADMIN"). */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
```

**Bảng phân quyền thực tế sau cấu hình:**

| Request | Không token | CUSTOMER | ADMIN |
|---|---|---|---|
| `POST /api/auth/login`, `POST /api/customers/register` | ✅ | ✅ | ✅ |
| `GET /api/movies/**`, `/api/genres/**`, `/api/showtimes/**`, `/api/bookings/showtimes/**` | ✅ | ✅ | ✅ |
| `/api/customers/me/**` | 401 | ✅ | 403 |
| `/api/customers/**` (còn lại) | 401 | 403 | ✅ |
| `/api/rooms/**`; POST/PUT/DELETE genres, movies, showtimes | 401 | 403 | ✅ |
| `POST /api/bookings`, `GET /api/bookings/my` | 401 | ✅ | 403 |
| `GET /api/bookings`, `GET /api/bookings/report` | 401 | 403 | ✅ |
| `GET /api/bookings/{id}`, `PUT /api/bookings/{id}/cancel` | 401 | ✅ (chủ sở hữu) | ✅ |

### Bước 5.6 – Chạy gateway

```bash
cd api-gateway
mvn spring-boot:run
curl http://localhost:9000/actuator/health        # {"status":"UP"}
curl -i http://localhost:9000/api/customers/me    # HTTP/1.1 401
```

**✅ Checklist Bước 5:** gateway chạy cổng 9000 · route đúng 3 service · không token → 401.

**💾 Commit gợi ý:** TODO 0.4 → 10.1 – 10.4 – xem bảng ở [Bước 9.4](#bước-9--quy-ước-commit-theo-từng-todo-conventional-commits).

---

## Bước 6 – Chạy toàn hệ thống

Thứ tự khởi động (mỗi service 1 terminal, hoặc Run trong IntelliJ):

```
1. docker compose up -d              (SQL Server + MongoDB + MySQL, đợi sqlserver healthy)
2. customer-service   :8081          (SQL Server)
3. movie-service      :8082          (MongoDB, tự seed lần đầu)
4. booking-service    :8083          (MySQL, cần movie-service khi đặt vé)
5. api-gateway        :9000
```

Kiểm tra nhanh toàn tuyến:

```bash
curl http://localhost:9000/api/movies                                  # public, qua gateway
curl -X POST http://localhost:9000/api/auth/login -H "Content-Type: application/json" \
     -d "{\"email\":\"admin@fucinema.com\",\"password\":\"@@abc123@@\"}"
```

> IntelliJ: mở thư mục `fu-cinema` → chuột phải từng `pom.xml` → **Add as Maven Project**. Sau đó dùng cửa sổ **Services** (Alt+8) để chạy/dừng cả 4 app một chỗ.

---
## Bước 7 – Kiểm thử API bằng Postman (F11)

> **Nguyên tắc:** mọi request đều gọi `{{gateway}}` = `http://localhost:9000`. Các request được sắp xếp theo **đúng thứ tự chạy** – request sau dùng biến do request trước lưu lại, nên có thể chạy cả Collection bằng **Collection Runner**.
> Chạy trên DB "sạch" (mới `docker compose up` lần đầu) thì kết quả report khớp đúng số liệu ghi trong bảng. Chạy lại nhiều lần vẫn pass vì các test dùng tên/email ngẫu nhiên và so sánh dạng `>=`.

### 7.1 (TODO 11.1) – Tạo Environment

Postman → **Environments** → **+** → đặt tên `FUCinema-Local`:

| Variable | Initial value | Ghi chú |
|---|---|---|
| `gateway` | `http://localhost:9000` | |
| `adminToken` | | tự set ở 1.1 |
| `customerToken` | | tự set ở 1.2 (an@gmail.com) |
| `customer2Token` | | tự set ở 2.4 (customer mới đăng ký) |
| `newEmail`, `newCustomerId`, `adminCreatedId` | | tự set |
| `genreId`, `roomId`, `movieId` | | tự set ở Folder 03–04 |
| `futureDate`, `futureStart`, `futureStartOverlap` | | tự set ở 5.1 |
| `showtimeId`, `showtime2Id` | | tự set ở Folder 05 |
| `bookingId`, `booking2Id`, `booking3Id` | | tự set ở Folder 06–07 |
| `today` | | tự set ở 8.1 |
| `notFoundId` | `66f9ffffffffffffffffffff` | ObjectId hợp lệ nhưng không tồn tại |
| `seedGenreActionId` | `66f000000000000000000001` | Genre "Hành động" (seed MongoDB) |
| `seedGenreScifiId` | `66f000000000000000000005` | Genre "Khoa học viễn tưởng" |
| `seedRoom2Id` | `66f100000000000000000002` | Room 02 (ACTIVE) |
| `seedRoomMaintenanceId` | `66f100000000000000000004` | Room 04 (MAINTENANCE) |
| `seedMovieEndedId` | `66f200000000000000000004` | Movie "Mùa Hè Năm Ấy" (ENDED) |

> ID của genre/room/movie/showtime là **chuỗi** (ObjectId) → trong body JSON phải đặt trong dấu nháy: `"movieId": "{{movieId}}"`. ID customer/booking là **số**.

Chọn environment `FUCinema-Local` ở góc trên bên phải trước khi chạy.

### 7.2 (TODO 11.2) – Tạo Collection & cách cấu hình request

Tạo Collection **`FUCinemaBookingSystem`** với 8 folder: `01-Auth`, `02-Customer`, `03-Genre-Room`, `04-Movie`, `05-Showtime`, `06-Booking`, `07-History-Cancel`, `08-Report`.

Với mỗi request:

1. **Method + URL** theo bảng (VD `POST {{gateway}}/api/auth/login`).
2. **Authorization** tab → *Auth Type* = **Bearer Token** → *Token* = `{{adminToken}}` / `{{customerToken}}` / `{{customer2Token}}` (cột **Auth**). Cột ghi `—` → chọn **No Auth**.
3. **Body** tab → **raw** → **JSON** (khi có body).
4. **Scripts → Post-response** (bản Postman cũ: tab **Tests**): dán script kiểm tra.
5. **Scripts → Pre-request**: chỉ những request có ghi chú.

> **Mẹo:** Đặt ở mức **Collection** → tab *Scripts → Post-response* đoạn sau để mọi request đều được kiểm tra thời gian phản hồi:
> ```js
> pm.test("Response time < 3000ms", () => pm.expect(pm.response.responseTime).to.be.below(3000));
> ```

---

### Folder 01-Auth (F1)

| # | Request | Auth | Body | Kỳ vọng |
|---|---|---|---|---|
| 1.1 | `POST /api/auth/login` | — | `{"email":"admin@fucinema.com","password":"@@abc123@@"}` | **200**, `role = ADMIN`, lưu `adminToken` |
| 1.2 | `POST /api/auth/login` | — | `{"email":"an@gmail.com","password":"123456"}` | **200**, `role = CUSTOMER`, `userId = 1`, lưu `customerToken` |
| 1.3 | `POST /api/auth/login` | — | `{"email":"an@gmail.com","password":"sai-mat-khau"}` | **401** |
| 1.4 | `POST /api/auth/login` | — | `{"email":"chi@gmail.com","password":"123456"}` | **403** (INACTIVE) |
| 1.5 | `POST /api/auth/login` | — | `{"email":"abc","password":""}` | **400** (validation) |
| 1.6 | `GET /api/customers/me` | — | | **401** (không token) |
| 1.7 | `GET /api/customers/me` | Bearer `abc.def.ghi` | | **401** (token sai) |

**Script 1.1 – Login Admin**
```js
pm.test("Status 200", () => pm.response.to.have.status(200));
const json = pm.response.json();
pm.test("Role is ADMIN", () => pm.expect(json.role).to.eql("ADMIN"));
pm.test("Has Bearer token", () => {
    pm.expect(json.tokenType).to.eql("Bearer");
    pm.expect(json.accessToken.split(".")).to.have.lengthOf(3);   // header.payload.signature
});
pm.environment.set("adminToken", json.accessToken);
```

**Script 1.2 – Login Customer**
```js
pm.test("Status 200", () => pm.response.to.have.status(200));
const json = pm.response.json();
pm.test("Role is CUSTOMER", () => pm.expect(json.role).to.eql("CUSTOMER"));
pm.test("userId = 1", () => pm.expect(json.userId).to.eql(1));
pm.environment.set("customerToken", json.accessToken);
```

**Script 1.3 → 1.7** (đổi số status cho đúng từng request)
```js
pm.test("Status 401", () => pm.response.to.have.status(401));
```
Với 1.3, 1.4, 1.5 kiểm tra thêm format lỗi chung:
```js
const err = pm.response.json();
pm.test("Error body format", () => {
    pm.expect(err).to.have.all.keys("timestamp", "status", "error", "message", "path");
    pm.expect(err.status).to.eql(pm.response.code);
});
```

> Copy `adminToken` dán vào **jwt.io** → thấy payload `{"sub":"admin@fucinema.com","uid":0,"role":"ADMIN",...}`.

---

### Folder 02-Customer (F2, F3)

| # | Request | Auth | Body | Kỳ vọng |
|---|---|---|---|---|
| 2.1 | `POST /api/customers/register` | — | xem dưới (`email = {{newEmail}}`) | **201**, không có `password`, lưu `newCustomerId` |
| 2.2 | `POST /api/customers/register` | — | như 2.1 nhưng `"email":"an@gmail.com"` | **409** |
| 2.3 | `POST /api/customers/register` | — | `{"customerName":"","telephone":"123","email":"x","customerBirthday":"2099-01-01","password":"1"}` | **400**, message nêu đủ các field sai |
| 2.4 | `POST /api/auth/login` | — | `{"email":"{{newEmail}}","password":"123456"}` | **200**, lưu `customer2Token` |
| 2.5 | `GET /api/customers/me` | customerToken | | **200**, `email = an@gmail.com` |
| 2.6 | `PUT /api/customers/me` | customerToken | `{"customerName":"Nguyễn Văn An (updated)","telephone":"0905999999","customerBirthday":"2002-05-10"}` | **200**, dữ liệu đã đổi, tiếng Việt đúng dấu (BR16 – NVARCHAR) |
| 2.7 | `PUT /api/customers/me/password` | customer2Token | `{"oldPassword":"sai","newPassword":"654321"}` | **400** |
| 2.8 | `PUT /api/customers/me/password` | customer2Token | `{"oldPassword":"123456","newPassword":"654321"}` | **204** |
| 2.9 | `POST /api/auth/login` | — | `{"email":"{{newEmail}}","password":"654321"}` | **200**, cập nhật `customer2Token` |
| 2.10 | `GET /api/customers` | customerToken | | **403** (Customer gọi API Admin) |
| 2.11 | `GET /api/customers?keyword=gmail` | adminToken | | **200**, mảng ≥ 3 phần tử |
| 2.12 | `GET /api/customers/99999` | adminToken | | **404** |
| 2.13 | `POST /api/customers` | adminToken | xem dưới | **201**, lưu `adminCreatedId` |
| 2.14 | `PUT /api/customers/{{adminCreatedId}}` | adminToken | như 2.13, đổi `customerName`, bỏ `password` | **200** |
| 2.15 | `DELETE /api/customers/{{adminCreatedId}}` | adminToken | | **204** |
| 2.16 | `GET /api/customers/{{adminCreatedId}}` | adminToken | | **200**, `customerStatus = INACTIVE` |
| 2.17 | `GET /api/customers/me` | adminToken | | **403** (Admin không có profile) |
| 2.18 | `GET /api/customers/me` | customerToken + **Header** `X-User-Id: 2` | | **200**, vẫn là `an@gmail.com` (Gateway ghi đè header giả) |

**2.1 – Pre-request** (sinh email mới mỗi lần chạy)
```js
pm.environment.set("newEmail", `user${Date.now()}@gmail.com`);
```
**2.1 – Body**
```json
{
  "customerName": "Phạm Thị Dung",
  "telephone": "0987654321",
  "email": "{{newEmail}}",
  "customerBirthday": "2004-03-15",
  "password": "123456"
}
```
**2.1 – Post-response**
```js
pm.test("Status 201", () => pm.response.to.have.status(201));
const json = pm.response.json();
pm.test("Status ACTIVE", () => pm.expect(json.customerStatus).to.eql("ACTIVE"));
pm.test("Password is NOT returned", () => pm.expect(json).to.not.have.property("password"));
pm.environment.set("newCustomerId", json.customerId);
```

**2.3 – Post-response**
```js
pm.test("Status 400", () => pm.response.to.have.status(400));
const msg = pm.response.json().message;
["customerName", "telephone", "email", "customerBirthday", "password"]
    .forEach(f => pm.test(`Message mentions ${f}`, () => pm.expect(msg).to.include(f)));
```

**2.4 / 2.9 – Post-response**
```js
pm.test("Status 200", () => pm.response.to.have.status(200));
pm.environment.set("customer2Token", pm.response.json().accessToken);
```

**2.6 – Post-response**
```js
pm.test("Status 200", () => pm.response.to.have.status(200));
const json = pm.response.json();
pm.test("Profile updated", () => {
    pm.expect(json.customerName).to.eql("Nguyễn Văn An (updated)");
    pm.expect(json.telephone).to.eql("0905999999");
});
```

**2.11 – Post-response**
```js
pm.test("Status 200", () => pm.response.to.have.status(200));
const list = pm.response.json();
pm.test("At least 3 customers", () => pm.expect(list.length).to.be.at.least(3));
pm.test("No password in list", () => list.forEach(c => pm.expect(c).to.not.have.property("password")));
```

**2.13 – Body** (pre-request: `pm.environment.set("adminEmail2", `staff${Date.now()}@gmail.com`);`)
```json
{
  "customerName": "Khách hàng do Admin tạo",
  "telephone": "0911222333",
  "email": "{{adminEmail2}}",
  "customerBirthday": "1999-09-09",
  "customerStatus": "ACTIVE",
  "password": "123456"
}
```
**2.13 – Post-response**
```js
pm.test("Status 201", () => pm.response.to.have.status(201));
pm.environment.set("adminCreatedId", pm.response.json().customerId);
```

**2.16 – Post-response**
```js
pm.test("Soft deleted -> INACTIVE", () => pm.expect(pm.response.json().customerStatus).to.eql("INACTIVE"));
```

**2.18 – Header giả mạo:** tab **Headers** thêm `X-User-Id` = `2`. Post-response:
```js
pm.test("Gateway overrides spoofed X-User-Id", () => pm.expect(pm.response.json().email).to.eql("an@gmail.com"));
```

---

### Folder 03-Genre-Room (F4)

| # | Request | Auth | Body | Kỳ vọng |
|---|---|---|---|---|
| 3.1 | `GET /api/genres` | — | | **200**, ≥ 5 phần tử |
| 3.2 | `POST /api/genres` | adminToken | `{"genreName":"Tài liệu {{$timestamp}}","description":"Phim tài liệu"}` | **201**, lưu `genreId` |
| 3.3 | `POST /api/genres` | customerToken | như 3.2 | **403** |
| 3.4 | `POST /api/genres` | adminToken | `{"genreName":"Hành động"}` | **409** |
| 3.5 | `PUT /api/genres/{{genreId}}` | adminToken | `{"genreName":"Tài liệu {{$timestamp}}","description":"Đã cập nhật"}` | **200** |
| 3.6 | `DELETE /api/genres/{{seedGenreActionId}}` | adminToken | | **409** (đang có phim) |
| 3.7 | `POST /api/rooms` | adminToken | `{"roomName":"Room Test {{$timestamp}}","roomType":"STANDARD","seatRows":5,"seatsPerRow":8,"roomStatus":"ACTIVE"}` | **201**, `totalSeats = 40`, lưu `roomId` |
| 3.8 | `GET /api/rooms` | — | | **401** (rooms chỉ Admin) |
| 3.9 | `POST /api/rooms` | adminToken | `{"roomName":"X","roomType":"VIP","seatRows":0,"seatsPerRow":8,"roomStatus":"ACTIVE"}` | **400** (enum sai) |
| 3.10 | `POST /api/rooms` | adminToken | `{"roomName":"Y","roomType":"IMAX","seatRows":0,"seatsPerRow":8,"roomStatus":"ACTIVE"}` | **400** (`seatRows` 1–26) |

> `{{$timestamp}}` là biến động có sẵn của Postman (Unix time) → tên luôn khác nhau mỗi lần chạy.

**3.2 – Post-response**
```js
pm.test("Status 201", () => pm.response.to.have.status(201));
pm.environment.set("genreId", pm.response.json().genreId);
```

**3.7 – Post-response**
```js
pm.test("Status 201", () => pm.response.to.have.status(201));
const room = pm.response.json();
pm.test("totalSeats = seatRows x seatsPerRow", () => pm.expect(room.totalSeats).to.eql(40));
pm.environment.set("roomId", room.roomId);
```

---

### Folder 04-Movie (F5)

| # | Request | Auth | Body | Kỳ vọng |
|---|---|---|---|---|
| 4.1 | `POST /api/movies` | adminToken | xem dưới | **201**, lưu `movieId` |
| 4.2 | `GET /api/movies?keyword=galaxy` | — | | **200**, có "Galaxy Rangers" |
| 4.3 | `GET /api/movies?status=NOW_SHOWING&genreId={{seedGenreScifiId}}` | — | | **200**, mọi phần tử đúng status & genre |
| 4.4 | `PUT /api/movies/{{movieId}}` | adminToken | như 4.1, đổi `"language":"Tiếng Việt"` | **200** |
| 4.5 | `GET /api/movies/{{notFoundId}}` | — | | **404** |
| 4.6 | `POST /api/movies` | adminToken | như 4.1 với `"durationMinutes":10` | **400** |
| 4.7 | `POST /api/movies` | adminToken | như 4.1 với `"genreId":"{{notFoundId}}"` | **404** (BR15 – Mongo không có FK) |
| 4.8 | `DELETE /api/genres/{{genreId}}` | adminToken | | **409** (genre đã có phim 4.1) |

**4.1 – Body**
```json
{
  "title": "Hành Trình Phương Nam",
  "description": "Phim tài liệu về miền Tây sông nước.",
  "director": "Lê Hoàng",
  "durationMinutes": 120,
  "language": "Tiếng Việt",
  "ageRating": "P",
  "releaseDate": "2026-10-01",
  "genreId": "{{genreId}}",
  "movieStatus": "NOW_SHOWING"
}
```
**4.1 – Post-response**
```js
pm.test("Status 201", () => pm.response.to.have.status(201));
const m = pm.response.json();
pm.test("Genre mapped", () => pm.expect(m.genreId).to.eql(pm.environment.get("genreId")));
pm.test("movieId is ObjectId", () => pm.expect(m.movieId).to.match(/^[0-9a-f]{24}$/));
pm.environment.set("movieId", m.movieId);
```

**4.3 – Post-response**
```js
pm.test("Status 200", () => pm.response.to.have.status(200));
pm.response.json().forEach(m => pm.test(`Movie ${m.movieId} matches filter`, () => {
    pm.expect(m.movieStatus).to.eql("NOW_SHOWING");
    pm.expect(m.genreId).to.eql(pm.environment.get("seedGenreScifiId"));
}));
```

---

### Folder 05-Showtime (F6)

| # | Request | Auth | Body | Kỳ vọng |
|---|---|---|---|---|
| 5.1 | `POST /api/showtimes` | adminToken | `{"movieId":"{{movieId}}","roomId":"{{roomId}}","startTime":"{{futureStart}}","ticketPrice":95000}` | **201**, `endTime = start + 120'`, lưu `showtimeId` |
| 5.2 | `POST /api/showtimes` | adminToken | như 5.1, `"startTime":"{{futureStartOverlap}}"` | **409** (trùng giờ cùng phòng) |
| 5.3 | `POST /api/showtimes` | adminToken | như 5.1, `"roomId":"{{seedRoom2Id}}"` | **201** (cùng giờ nhưng khác phòng → OK), lưu `showtime2Id` |
| 5.4 | `POST /api/showtimes` | adminToken | `"movieId":"{{seedMovieEndedId}}"` (ENDED), `"roomId":"{{roomId}}"` | **400** |
| 5.5 | `POST /api/showtimes` | adminToken | `"movieId":"{{movieId}}"`, `"roomId":"{{seedRoomMaintenanceId}}"` (MAINTENANCE) | **400** |
| 5.6 | `POST /api/showtimes` | adminToken | `"startTime":"2020-01-01T10:00:00"` | **400** |
| 5.7 | `GET /api/showtimes?movieId={{movieId}}&date={{futureDate}}` | — | | **200**, 2 suất |
| 5.8 | `GET /api/showtimes/{{showtimeId}}` | — | | **200**, `seatRows=5`, `seatsPerRow=8` |
| 5.9 | `DELETE /api/showtimes/{{showtime2Id}}` | adminToken | | **204** |
| 5.10 | `GET /api/showtimes/{{showtime2Id}}` | — | | **200**, `showtimeStatus = CANCELLED` |
| 5.11 | `DELETE /api/rooms/{{roomId}}` | adminToken | | **409** (phòng đã có suất chiếu) |
| 5.12 | `DELETE /api/movies/{{movieId}}` | adminToken | | **409** (phim đã có suất chiếu) |
| 5.13 | `POST /api/showtimes` | adminToken | như 5.1, `"movieId":"{{notFoundId}}"` | **404** (BR15) |

**5.1 – Pre-request** (tính ngày chiếu = hôm nay + 7, 19:00)
```js
const pad = n => String(n).padStart(2, "0");
const d = new Date();
d.setDate(d.getDate() + 7);
const day = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
pm.environment.set("futureDate", day);
pm.environment.set("futureStart", `${day}T19:00:00`);
pm.environment.set("futureStartOverlap", `${day}T20:00:00`);   // 20:00 nam trong 19:00-21:00
```
**5.1 – Post-response**
```js
pm.test("Status 201", () => pm.response.to.have.status(201));
const s = pm.response.json();
pm.test("endTime = startTime + duration (120')", () => {
    const minutes = (new Date(s.endTime) - new Date(s.startTime)) / 60000;
    pm.expect(minutes).to.eql(120);
});
pm.test("Status SCHEDULED", () => pm.expect(s.showtimeStatus).to.eql("SCHEDULED"));
pm.environment.set("showtimeId", s.showtimeId);
```
**5.2 – Post-response**
```js
pm.test("Status 409 - overlap", () => pm.response.to.have.status(409));
```
**5.3 – Post-response**
```js
pm.test("Status 201", () => pm.response.to.have.status(201));
pm.environment.set("showtime2Id", pm.response.json().showtimeId);
```
**5.7 – Post-response**
```js
pm.test("Status 200", () => pm.response.to.have.status(200));
const list = pm.response.json();
pm.test("Filter by movie & date", () => {
    pm.expect(list.length).to.be.at.least(1);
    list.forEach(s => {
        pm.expect(s.movieId).to.eql(pm.environment.get("movieId"));
        pm.expect(s.startTime.substring(0, 10)).to.eql(pm.environment.get("futureDate"));
    });
});
```

---

### Folder 06-Booking (F7)

| # | Request | Auth | Body | Kỳ vọng |
|---|---|---|---|---|
| 6.1 | `GET /api/bookings/showtimes/{{showtimeId}}/seats` | — | | **200**, `totalSeats=40`, `availableSeats=40` |
| 6.2 | `POST /api/bookings` | customerToken | `{"items":[{"showtimeId":"{{showtimeId}}","seatCode":"E5"},{"showtimeId":"{{showtimeId}}","seatCode":"E6"}]}` | **201**, `totalPrice=190000`, lưu `bookingId` |
| 6.3 | `POST /api/bookings` | customer2Token | `{"items":[{"showtimeId":"{{showtimeId}}","seatCode":"E5"}]}` | **409** (ghế đã bán) |
| 6.4 | `POST /api/bookings` | customer2Token | `seatCode = "E9"` | **400** (phòng chỉ có ghế 1–8) |
| 6.5 | `POST /api/bookings` | customer2Token | `seatCode = "F1"` | **400** (phòng chỉ có hàng A–E) |
| 6.6 | `POST /api/bookings` | customer2Token | 2 item cùng `"A2"` | **400** (trùng ghế trong request) |
| 6.7 | `POST /api/bookings` | customer2Token | `"showtimeId":"{{notFoundId}}"` | **404** |
| 6.8 | `POST /api/bookings` | customer2Token | `"showtimeId":"{{showtime2Id}}"` (đã hủy) | **400** |
| 6.9 | `POST /api/bookings` | customer2Token | 9 item `A1`..`A8`,`B1` | **400** (tối đa 8 vé) |
| 6.10 | `POST /api/bookings` | customer2Token | `{"items":[]}` | **400** |
| 6.11 | `POST /api/bookings` | adminToken | như 6.2 | **403** (Admin không đặt vé) |
| 6.12 | `POST /api/bookings` | — | như 6.2 | **401** |
| 6.13 | `POST /api/bookings` | customer2Token | `{"items":[{"showtimeId":"{{showtimeId}}","seatCode":"A1"}]}` | **201**, lưu `booking2Id` |
| 6.14 | `GET /api/bookings/showtimes/{{showtimeId}}/seats` | — | | `bookedSeats = ["A1","E5","E6"]`, `availableSeats=37` |

**6.1 – Post-response**
```js
pm.test("Status 200", () => pm.response.to.have.status(200));
const m = pm.response.json();
pm.test("40 seats, all available", () => {
    pm.expect(m.totalSeats).to.eql(40);
    pm.expect(m.availableSeats).to.eql(40);
    pm.expect(m.bookedSeats).to.be.empty;
});
```
**6.2 – Post-response**
```js
pm.test("Status 201", () => pm.response.to.have.status(201));
const b = pm.response.json();
pm.test("CONFIRMED with 2 tickets", () => {
    pm.expect(b.bookingStatus).to.eql("CONFIRMED");
    pm.expect(b.details).to.have.lengthOf(2);
});
pm.test("totalPrice = sum of ticket prices (server-side)", () => {
    const sum = b.details.reduce((acc, d) => acc + Number(d.price), 0);
    pm.expect(Number(b.totalPrice)).to.eql(sum);
    pm.expect(Number(b.totalPrice)).to.eql(190000);
});
pm.test("Snapshot movie info", () => pm.expect(b.details[0].movieTitle).to.eql("Hành Trình Phương Nam"));
pm.environment.set("bookingId", b.bookingId);
```
**6.3 – Post-response**
```js
pm.test("Status 409 - seat taken", () => pm.response.to.have.status(409));
pm.test("Message mentions seat", () => pm.expect(pm.response.json().message).to.include("E5"));
```
**6.9 – Body** (9 vé)
```json
{
  "items": [
    {"showtimeId": "{{showtimeId}}", "seatCode": "A1"}, {"showtimeId": "{{showtimeId}}", "seatCode": "A2"},
    {"showtimeId": "{{showtimeId}}", "seatCode": "A3"}, {"showtimeId": "{{showtimeId}}", "seatCode": "A4"},
    {"showtimeId": "{{showtimeId}}", "seatCode": "A5"}, {"showtimeId": "{{showtimeId}}", "seatCode": "A6"},
    {"showtimeId": "{{showtimeId}}", "seatCode": "A7"}, {"showtimeId": "{{showtimeId}}", "seatCode": "A8"},
    {"showtimeId": "{{showtimeId}}", "seatCode": "B1"}
  ]
}
```
**6.13 – Post-response**
```js
pm.test("Status 201", () => pm.response.to.have.status(201));
pm.environment.set("booking2Id", pm.response.json().bookingId);
```
**6.14 – Post-response**
```js
const m = pm.response.json();
pm.test("Booked seats updated", () => {
    pm.expect(m.bookedSeats).to.have.members(["A1", "E5", "E6"]);
    pm.expect(m.availableSeats).to.eql(37);
});
```

**6.15 – Test Movie Service bị sập (BR14, làm tay – không đưa vào Runner):** dừng movie-service → gửi lại 6.13 với ghế `B2` → kỳ vọng **503** `"Movie service is unavailable..."` → bật lại movie-service.

---

### Folder 07-History-Cancel (F8)

| # | Request | Auth | Body | Kỳ vọng |
|---|---|---|---|---|
| 7.1 | `GET /api/bookings/my` | customerToken | | **200**, chỉ booking của customer 1, mới nhất trước |
| 7.2 | `GET /api/bookings/{{bookingId}}` | customer2Token | | **403** (không phải chủ) |
| 7.3 | `GET /api/bookings/{{bookingId}}` | adminToken | | **200** |
| 7.4 | `PUT /api/bookings/{{booking2Id}}/cancel` | customerToken | | **403** |
| 7.5 | `PUT /api/bookings/{{booking2Id}}/cancel` | customer2Token | | **200**, `CANCELLED` |
| 7.6 | `PUT /api/bookings/{{booking2Id}}/cancel` | customer2Token | | **400** (đã hủy) |
| 7.7 | `POST /api/bookings` | customerToken | `{"items":[{"showtimeId":"{{showtimeId}}","seatCode":"A1"}]}` | **201** – ghế A1 đã được giải phóng, lưu `booking3Id` |
| 7.8 | `GET /api/bookings` | adminToken | | **200**, tất cả booking |
| 7.9 | `GET /api/bookings/my` | adminToken | | **403** |
| 7.10 | `GET /api/bookings/99999` | adminToken | | **404** |

**7.1 – Post-response**
```js
pm.test("Status 200", () => pm.response.to.have.status(200));
const list = pm.response.json();
pm.test("Only my bookings", () => list.forEach(b => pm.expect(b.customerId).to.eql(1)));
pm.test("Sorted by bookingDate DESC", () => {
    for (let i = 1; i < list.length; i++) {
        pm.expect(list[i - 1].bookingDate >= list[i].bookingDate).to.be.true;
    }
});
```
**7.5 – Post-response**
```js
pm.test("Status 200", () => pm.response.to.have.status(200));
pm.test("Cancelled", () => pm.expect(pm.response.json().bookingStatus).to.eql("CANCELLED"));
```
**7.7 – Post-response**
```js
pm.test("Seat A1 can be re-booked after cancel", () => pm.response.to.have.status(201));
pm.environment.set("booking3Id", pm.response.json().bookingId);
```

---

### Folder 08-Report (F9)

| # | Request | Auth | Kỳ vọng |
|---|---|---|---|
| 8.1 | `GET /api/bookings/report?startDate={{today}}&endDate={{today}}` | adminToken | **200**, số liệu đúng, sắp xếp giảm dần, không tính booking hủy |
| 8.2 | `GET /api/bookings/report?startDate=2026-12-31&endDate=2026-01-01` | adminToken | **400** |
| 8.3 | `GET /api/bookings/report?startDate={{today}}&endDate={{today}}` | customerToken | **403** |
| 8.4 | `GET /api/bookings/report?startDate={{today}}` | adminToken | **400** (thiếu `endDate`) |
| 8.5 | `GET /api/bookings/report?startDate=2020-01-01&endDate=2020-01-31` | adminToken | **200**, `totalBookings = 0`, `totalRevenue = 0` |

**8.1 – Pre-request**
```js
const pad = n => String(n).padStart(2, "0");
const d = new Date();
pm.environment.set("today", `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`);
```
**8.1 – Post-response**
```js
pm.test("Status 200", () => pm.response.to.have.status(200));
const r = pm.response.json();

pm.test("Summary is consistent", () => {
    pm.expect(r.totalBookings).to.eql(r.bookings.length);
    const tickets = r.bookings.reduce((a, b) => a + b.details.length, 0);
    const revenue = r.bookings.reduce((a, b) => a + Number(b.totalPrice), 0);
    pm.expect(r.totalTickets).to.eql(tickets);
    pm.expect(Number(r.totalRevenue)).to.eql(revenue);
});
pm.test("Only CONFIRMED bookings", () =>
    r.bookings.forEach(b => pm.expect(b.bookingStatus).to.eql("CONFIRMED")));
pm.test("Cancelled booking excluded", () => {
    const ids = r.bookings.map(b => b.bookingId);
    pm.expect(ids).to.not.include(Number(pm.environment.get("booking2Id")));
});
pm.test("bookings sorted by bookingDate DESC", () => {
    for (let i = 1; i < r.bookings.length; i++)
        pm.expect(r.bookings[i - 1].bookingDate >= r.bookings[i].bookingDate).to.be.true;
});
pm.test("revenueByMovie sorted by revenue DESC", () => {
    for (let i = 1; i < r.revenueByMovie.length; i++)
        pm.expect(Number(r.revenueByMovie[i - 1].revenue)).to.be.at.least(Number(r.revenueByMovie[i].revenue));
});
```

> **Số liệu kỳ vọng khi chạy lần đầu trên DB sạch:** booking 1 (E5, E6 = 190.000) + booking 3 (A1 = 95.000) → `totalBookings = 2`, `totalTickets = 3`, `totalRevenue = 285000`. Booking 2 (đã hủy) **không** được tính.

---

### 7.2b – Kiểm tra trực tiếp trong 3 database (sau khi chạy Collection)

Postman chỉ thấy API; phần này xác nhận dữ liệu được lưu **đúng chỗ, đúng kiểu**:

```bash
# SQL Server – tieng Viet luu dung (BR16), customer moi dang ky co mat
docker exec -it cinema-sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "Fucinema@2026" -C \
        -d cinema_customer -Q "SELECT TOP 5 customer_id, customer_name, email, customer_status FROM customer ORDER BY customer_id DESC"

# MongoDB – showtime vua tao: ticketPrice la Decimal128, movieId/roomId la chuoi ObjectId
docker exec -it cinema-mongo mongosh -u root -p password --authenticationDatabase admin cinema_movie \
        --eval "db.showtimes.find().sort({_id:-1}).limit(2)"

# MySQL – booking_detail luu showtime_id dang VARCHAR(24), snapshot movie_title
docker exec -it cinema-mysql mysql -uroot -pmysql cinema_booking \
        -e "SELECT b.booking_id, b.booking_status, d.showtime_id, d.seat_code, d.movie_title FROM booking b JOIN booking_detail d ON d.booking_id = b.booking_id ORDER BY d.booking_detail_id DESC LIMIT 5;"
```

| Kiểm tra | Kỳ vọng |
|---|---|
| SQL Server `customer_name` | `Nguyễn Văn An (updated)` hiển thị đúng dấu |
| MongoDB `showtimes.ticketPrice` | `Decimal128('95000')` (không phải `"95000"`) |
| MongoDB `_id` | `ObjectId('…')` – giống `showtimeId` trả về ở Postman 5.1 |
| MySQL `booking_detail.showtime_id` | Chuỗi 24 ký tự trùng `{{showtimeId}}` |

### 7.3 (TODO 11.3) – Chạy Collection Runner & nộp bài

1. Chuột phải Collection `FUCinemaBookingSystem` → **Run collection**.
2. Chọn Environment `FUCinema-Local`, giữ thứ tự folder 01 → 08, **Run**.
3. Kết quả mong đợi: **tất cả test Passed** (0 Failed). Chụp màn hình kết quả cho README.
4. Export: chuột phải Collection → **Export** (v2.1) → `FUCinemaBookingSystem.postman_collection.json`; Environments → `...` → **Export** → `FUCinema-Local.postman_environment.json`.

**💾 Commit gợi ý:** lưu 2 file export vào `fu-cinema/postman/`, commit TODO 11.1 – 11.3, merge về `main` và đánh tag `v1.0.0` – xem bảng ở [Bước 9.4](#bước-9--quy-ước-commit-theo-từng-todo-conventional-commits).

**Thứ tự chạy & phụ thuộc biến (tóm tắt):**

```
1.1 adminToken ─┬─► 2.x, 3.x, 4.x, 5.x, 7.3, 7.8, 8.x
1.2 customerToken ─► 2.5, 2.6, 6.2, 7.1, 7.7
2.1 newEmail ─► 2.4 customer2Token ─► 2.8 ─► 2.9 customer2Token (mật khẩu mới) ─► 6.3…6.13, 7.2, 7.5
3.2 genreId ─► 4.1 movieId ─┐
3.7 roomId ─────────────────┴► 5.1 showtimeId ─► 6.x, 7.7
                               5.3 showtime2Id ─► 5.9 (hủy) ─► 6.8
6.2 bookingId ─► 7.2, 7.3        6.13 booking2Id ─► 7.4, 7.5, 7.6, 8.1
```

---

## Bước 8 – Xử lý lỗi thường gặp

| Hiện tượng | Nguyên nhân | Cách xử lý |
|---|---|---|
| `Communications link failure` khi start booking-service | MySQL chưa chạy / sai port | `docker compose up -d`, `docker ps` |
| customer-service: `Login failed for user 'sa'` | Sai mật khẩu, hoặc SQL Server chưa khởi động xong | Đợi `cinema-sqlserver` **healthy**; mật khẩu khớp `MSSQL_SA_PASSWORD` |
| customer-service: `Cannot open database "cinema_customer"` | `sqlserver-init` chưa chạy / lỗi | `docker logs cinema-sqlserver-init`; chạy lại `docker compose up sqlserver-init` |
| `PKIX path building failed` / `SSL ... trustServerCertificate` | Driver mssql-jdbc mã hóa kết nối mặc định, chứng chỉ container tự ký | Thêm `;encrypt=true;trustServerCertificate=true` vào URL |
| `Unsupported Database: Microsoft SQL Server` (Flyway) | Thiếu module `flyway-sqlserver` | Thêm dependency `org.flywaydb:flyway-sqlserver` |
| Tên tiếng Việt thành `Nguy?n V?n An` | Cột `VARCHAR` thay vì `NVARCHAR`, hoặc seed thiếu tiền tố `N'...'` | Sửa bằng migration mới `V3__...` (`ALTER COLUMN ... NVARCHAR(100)`) |
| `cinema-sqlserver` Exited ngay sau khi chạy | Mật khẩu `sa` yếu / Docker thiếu RAM | `docker logs cinema-sqlserver`; tăng RAM Docker ≥ 2 GB |
| movie-service: `Command failed with error 18 (AuthenticationFailed)` | URI thiếu `?authSource=admin` | `spring.mongodb.uri=mongodb://root:password@localhost:27017/cinema_movie?authSource=admin` |
| movie-service vẫn kết nối `localhost:27017/test` | Dùng prefix cũ `spring.data.mongodb.uri` (Boot 3) | Boot 4 dùng `spring.mongodb.uri` |
| `ticketPrice` trong MongoDB là chuỗi `"95000"` | Thiếu `@Field(targetType = FieldType.DECIMAL128)` | Thêm annotation, xóa dữ liệu cũ, seed lại |
| Dữ liệu seed MongoDB bị nhân đôi | `DataSeeder` không kiểm tra `count() > 0` | Thêm điều kiện bỏ qua khi đã có dữ liệu |
| `GET /api/movies/abc` trả 404 | ID không phải ObjectId thì không tìm thấy | Bình thường – ID Mongo là chuỗi 24 hex |
| `Unknown database 'cinema_booking'` | `mysql/init.sql` không chạy (thư mục data đã tồn tại từ trước) | `docker compose down`, xóa `docker/mysql/data`, `up -d` lại |
| `Port 3306/27017 is already allocated` | Container `mysql`/`mongo` của Part 1 đang chạy | `docker stop mysql mongo` |
| `FlywayValidateException: checksum mismatch` | Sửa file `V1`/`V2` sau khi đã chạy | Tạo file `V3__...` thay vì sửa; hoặc `DROP DATABASE` rồi tạo lại |
| Flyway không chạy, bảng không được tạo | Thiếu `spring-boot-starter-flyway` (Boot 4 bắt buộc) | Thêm starter + `flyway-mysql` |
| `cannot find symbol getXxx()` | Lombok chưa được xử lý | Kiểm tra `annotationProcessorPaths`; IntelliJ bật *Enable annotation processing* |
| Mọi request có token đều **401** | `app.jwt.secret` ở gateway khác customer-service / token hết hạn (60') / thiếu chữ `Bearer` | Đồng bộ secret, login lại |
| `KeyLengthException` / lỗi secret quá ngắn | HS256 cần secret **≥ 32 byte** | Dùng chuỗi dài hơn |
| Đúng role mà vẫn **403** | Claim `role` sai tên, hoặc thứ tự `requestMatchers` sai (quy tắc tổng quát đặt trước quy tắc cụ thể) | Xem payload ở jwt.io; đưa `/me`, `/my`, public lên trước |
| `401 Missing user context header 'X-User-Id'` | Gọi thẳng 8081/8083 thay vì qua 9000 | Luôn gọi `{{gateway}}` |
| Đặt vé trả **503** | movie-service tắt hoặc `movie.service.url` sai | Bật movie-service, kiểm tra properties |
| Đặt vé trả **400** "has already started" | Dùng suất chiếu seed đã qua ngày | Tạo suất chiếu mới (5.1) |
| Gateway trả **404** | Path không khớp route nào (`/api/booking` thiếu `s`…) | Đối chiếu `Routes.java` |
| `LazyInitializationException` (customer/booking) | Map Entity → DTO ngoài `@Transactional` (vì `open-in-view=false`) | Map trong Service, không trả Entity ra Controller |
| Report luôn rỗng | Ngày `today` ở Postman khác ngày server; hoặc lọc sai `[start, end+1)` | Kiểm tra múi giờ máy, `findForReport` |

---

## Bước 9 – Quy ước commit theo từng TODO (Conventional Commits)

> **Mục tiêu:** lịch sử Git đọc vào là thấy ngay đã làm TODO nào, theo thứ tự nào. Giảng viên chấm được tiến độ qua `git log`, và khi lỗi thì `git revert` đúng một TODO.
> **Nguyên tắc vàng:** **1 TODO = 1 commit** (TODO quá nhỏ thì gộp 2–3 TODO liền nhau cùng service). **Chỉ commit khi code build được** (`mvn -q compile`).

### 9.1 Cấu trúc commit message

```
<type>(<scope>): <subject>          <- dòng tiêu đề, bắt buộc
                                     <- dòng trống
<body>                               <- tùy chọn: giải thích VÌ SAO / làm gì
                                     <- dòng trống
Refs: TODO <x.y>                     <- footer: liên kết TODO trong đề
```

**Quy tắc dòng tiêu đề:**

| Quy tắc | Đúng | Sai |
|---|---|---|
| Viết tiếng Anh, thể **mệnh lệnh** (imperative) | `add`, `implement`, `fix` | `added`, `adding`, `fixes` |
| Chữ thường đầu câu, **không** dấu chấm cuối | `feat(movie): add showtime entity` | `feat(movie): Add showtime entity.` |
| Tối đa **72 ký tự** | | |
| Nói **kết quả**, không nói việc làm chung chung | `feat(booking): reject seats outside room layout` | `update code`, `fix bug`, `done TODO 7` |

**`type` – loại thay đổi:**

| type | Khi nào dùng |
|---|---|
| `feat` | Thêm tính năng / API / nghiệp vụ mới |
| `fix` | Sửa lỗi |
| `build` | Thay đổi `pom.xml`, dependency, phiên bản, Maven plugin |
| `chore` | Cấu hình, hạ tầng, file không phải code nghiệp vụ (`docker-compose`, `.gitignore`, properties) |
| `refactor` | Đổi cấu trúc code, **không** đổi hành vi |
| `test` | Thêm/sửa test (JUnit, Postman collection) |
| `docs` | README, tài liệu |
| `style` | Format code, không đổi logic |

**`scope` – phạm vi (dùng cố định trong cả dự án):**

| scope | Thư mục |
|---|---|
| `infra` | `docker-compose.yml`, `sqlserver/`, `mysql/` |
| `customer` | `customer-service/` |
| `movie` | `movie-service/` |
| `booking` | `booking-service/` |
| `gateway` | `api-gateway/` |
| `postman` | `postman/` |
| *(bỏ trống)* | Thay đổi toàn repo: `chore: ...`, `docs: ...` |

### 9.2 Khởi tạo Git (làm **trước** Bước 1)

Dùng **một repo chung (monorepo)** đặt tại thư mục gốc `fu-cinema/`:

```bash
cd fu-cinema
git init -b main
git config user.name  "Nguyen Van A"
git config user.email "anv@fpt.edu.vn"
```

File **`fu-cinema/.gitignore`**:

```gitignore
target/
# du lieu cua cac container database (MySQL, MongoDB) - KHONG commit
docker/
.idea/
*.iml
.vscode/
*.log
.DS_Store
```

```bash
git add .gitignore
git commit -m "chore: initialize repository with gitignore"
```

> ⚠️ Start.spring.io sinh sẵn `.git`/`.gitignore` riêng trong từng project con? Hãy **xóa thư mục `.git`** con (nếu có) để chỉ còn một repo ở `fu-cinema/`. File `.gitignore` con có thể giữ lại.

**Nhánh (khuyến nghị):** mỗi nhóm tính năng một nhánh, xong checklist thì merge về `main`:

```bash
git switch -c feature/f7-create-booking
# ... code + commit tung TODO ...
git switch main
git merge --no-ff feature/f7-create-booking -m "merge: F7 create booking"
```

Tên nhánh: `feature/f0-infrastructure`, `feature/f1-authentication`, `feature/f2-customer-profile`, `feature/f3-admin-customers`, `feature/f4-genre-room`, `feature/f5-movies`, `feature/f6-showtimes`, `feature/f7-create-booking`, `feature/f8-history-cancel`, `feature/f9-report`, `feature/f10-gateway`, `feature/f11-postman`.

### 9.3 Quy trình mỗi lần commit

```bash
mvn -q -f customer-service/pom.xml compile   # (1) build service vua sua -> phai thanh cong
git status                                   # (2) xem file thay doi, khong co target/, docker/
git add customer-service/src/main/java/com/fudn/customerservice/security/JwtService.java
git commit -m "feat(customer): add JwtService to sign HS256 access tokens" -m "Refs: TODO 1.3"
```

> `git add <file cụ thể>` thay vì `git add .` để mỗi commit chỉ chứa đúng TODO đó.
> Hai tham số `-m` tạo ra tiêu đề và footer (cách nhau bởi dòng trống). Commit cần body dài → dùng `git commit` (mở editor).

### 9.4 Bảng commit gợi ý cho từng TODO

#### F0 – Hạ tầng & khởi tạo (nhánh `feature/f0-infrastructure`)

| TODO | Commit message |
|---|---|
| — | `chore: initialize repository with gitignore` |
| 0.1 | `chore(infra): add docker compose for sql server, mongodb and mysql` |
| 0.2 | `chore(infra): add init scripts for sql server and mysql databases` |
| 0.3 | `build(customer): bootstrap customer-service with sql server driver and flyway` |
| 0.3 | `build(movie): bootstrap movie-service with spring data mongodb` |
| 0.3 | `build(booking): bootstrap booking-service with mysql and flyway` |
| 0.4 | `build(gateway): bootstrap api-gateway with gateway webmvc and resource server` |
| 0.5 | `chore(customer): configure sql server datasource and jpa settings` |
| 0.5 | `chore(movie): configure mongodb connection uri and auto index creation` |
| 0.5 | `chore(booking): configure port, datasource and movie service url` |
| 0.6 | `feat(customer): add global exception handler with unified error body` |
| 0.6 | `feat(movie): add global exception handler with unified error body` |
| 0.6 | `feat(booking): add global exception handler with unified error body` |

> Bước 0.3/0.4 chỉ commit **project vừa sinh + pom.xml đã chỉnh**, chưa có code nghiệp vụ. Kiểm tra `mvn -q compile` trước khi commit.

#### F1 – Authentication (nhánh `feature/f1-authentication`)

| TODO | Commit message |
|---|---|
| 1.1 | `chore(customer): configure admin account and jwt properties` |
| 1.2 | `feat(customer): add bcrypt password encoder bean` |
| 1.3 | `feat(customer): add JwtService to sign HS256 access tokens` |
| 1.4 | `feat(customer): implement login for admin and customers` |
| 1.5 | `feat(customer): expose POST /api/auth/login` |

> Lưu ý thứ tự thực tế: TODO 1.4 cần entity `Customer` → làm **TODO 2.1, 2.2 trước** rồi mới commit 1.4. Commit theo thứ tự **làm**, không bắt buộc theo số TODO.

#### F2 – Customer: register & profile (nhánh `feature/f2-customer-profile`)

| TODO | Commit message |
|---|---|
| 2.1 | `feat(customer): add t-sql migrations with nvarchar and unicode seed data` |
| 2.2 | `feat(customer): add Customer entity and repository` |
| 2.3 | `feat(customer): add register, profile and password DTOs with validation` |
| 2.4 | `feat(customer): implement customer registration with unique email` |
| 2.5 | `feat(customer): implement view, update profile and change password` |
| 2.6 | `feat(customer): expose register and /me endpoints` |

#### F3 – Admin: manage customers (nhánh `feature/f3-admin-customers`)

| TODO | Commit message |
|---|---|
| 3.1 | `feat(customer): add AdminCustomerRequest DTO` |
| 3.2 | `feat(customer): implement admin customer search and crud with soft delete` |
| 3.3 | `feat(customer): expose admin customer crud endpoints` |

#### F4 – Genre & Room (nhánh `feature/f4-genre-room`)

| TODO | Commit message |
|---|---|
| 4.1 | `feat(movie): add DataSeeder for genres, rooms, movies and showtimes` |
| 4.2 | `feat(movie): add Genre and CinemaRoom documents with unique indexes` |
| 4.3 | `feat(movie): implement genre and room services with delete guards` |
| 4.4 | `feat(movie): expose genre and room endpoints` |

#### F5 – Movie (nhánh `feature/f5-movies`)

| TODO | Commit message |
|---|---|
| 5.1 | `feat(movie): add Movie document referencing genre id` |
| 5.2 | `feat(movie): search movies with MongoTemplate criteria` |
| 5.3 | `feat(movie): implement movie crud with genre existence check` |
| 5.4 | `feat(movie): expose movie endpoints` |

#### F6 – Showtime (nhánh `feature/f6-showtimes`)

| TODO | Commit message |
|---|---|
| 6.1 | `feat(movie): add Showtime document with decimal128 price` |
| 6.2 | `feat(movie): add derived query counting overlapping showtimes` |
| 6.3 | `feat(movie): validate and schedule showtimes with computed end time` |
| 6.4 | `feat(movie): implement showtime cancel and search by movie and date` |
| 6.5 | `feat(movie): expose showtime endpoints` |

#### F7 – Create booking (nhánh `feature/f7-create-booking`)

| TODO | Commit message |
|---|---|
| 7.1 | `build(booking): add openfeign and spring cloud bom` |
| 7.2 | `feat(booking): add mysql migration with varchar object id columns` |
| 7.3 | `feat(booking): add Booking and BookingDetail entities` |
| 7.4 | `feat(booking): add MovieClient feign client with string showtime id` |
| 7.5 | `feat(booking): implement booking creation with seat validation` |
| 7.6 | `feat(booking): add seat map for a showtime` |
| 7.7 | `feat(booking): expose create booking and seat map endpoints` |

#### F8 – History & cancel (nhánh `feature/f8-history-cancel`)

| TODO | Commit message |
|---|---|
| 8.1 | `feat(booking): add customer booking history sorted by date` |
| 8.2 | `feat(booking): restrict booking detail to owner or admin` |
| 8.3 | `feat(booking): allow cancelling bookings 2 hours before showtime` |
| 8.4 | `feat(booking): expose history, detail, cancel and admin list endpoints` |

#### F9 – Report (nhánh `feature/f9-report`)

| TODO | Commit message |
|---|---|
| 9.1 | `feat(booking): add report query by booking date range` |
| 9.2 | `feat(booking): implement revenue report sorted descending` |
| 9.3 | `feat(booking): expose GET /api/bookings/report` |

#### F10 – API Gateway (nhánh `feature/f10-gateway`)

| TODO | Commit message |
|---|---|
| 10.1 | `chore(gateway): configure service urls and jwt secret` |
| 10.2 | `feat(gateway): forward user context headers from jwt` |
| 10.3 | `feat(gateway): add routes for customer, movie and booking services` |
| 10.4 | `feat(gateway): secure endpoints with jwt and role-based rules` |

#### F11 – Postman & nộp bài (nhánh `feature/f11-postman`)

| TODO | Commit message |
|---|---|
| 11.1 | `test(postman): add local environment` |
| 11.2 | `test(postman): add collection with test scripts for F1-F10` |
| 11.3 | `docs: add run guide and test accounts to README` |
| — | `docs: add collection runner result screenshot` |

Sau khi merge F11 về `main`, đánh tag phiên bản nộp bài:

```bash
git tag -a v1.0.0 -m "Assignment 01 submission"
```

### 9.5 Commit có body – ví dụ đầy đủ

Dùng cho TODO có logic nghiệp vụ quan trọng (6.3, 7.5, 8.3, 9.2, 10.4):

```text
feat(booking): implement booking creation with seat validation

- fetch each showtime once via MovieClient and cache it per request
- reject duplicate seats, seats outside room layout and taken seats
- compute ticket price and total on the server side
- snapshot movie title, room name and start time into booking_detail

Refs: TODO 7.5
Closes: BR07, BR08, BR09, BR10, BR14
```

### 9.6 Commit sửa lỗi phát hiện khi test Postman

Lỗi tìm thấy khi chạy Collection → commit `fix` riêng, ghi rõ test case:

| Tình huống | Commit message |
|---|---|
| Test 6.14 fail: ghế của booking đã hủy vẫn bị tính | `fix(booking): exclude cancelled bookings from booked seats` |
| Test 2.18 fail: header giả mạo lọt qua gateway | `fix(gateway): strip client supplied X-User headers` |
| Test 5.2 fail: hai suất chiếu chạm biên bị báo trùng | `fix(movie): treat back-to-back showtimes as non-overlapping` |
| Test 8.1 fail: thiếu booking cuối ngày | `fix(booking): include whole end date in report range` |
| Admin gọi `/api/customers/me` nhận 500 | `fix(gateway): deny admin access to customer profile endpoints` |
| Tên khách hàng mất dấu trên SQL Server | `fix(customer): store customer name as nvarchar` |
| `ticketPrice` lưu dạng chuỗi trong MongoDB | `fix(movie): persist ticket price as decimal128` |
| Seed MongoDB bị nhân đôi khi restart | `fix(movie): skip seeding when collections already have data` |

Footer cho commit fix: `Refs: Postman 6.14`.

### 9.7 Lịch sử mong đợi

```bash
git log --oneline --graph
```

```
*   merge: F11 postman tests
|\
| * docs: add run guide and test accounts to README
| * test(postman): add collection with test scripts for F1-F10
| * test(postman): add local environment
|/
*   merge: F10 gateway
|\
| * feat(gateway): secure endpoints with jwt and role-based rules
| * feat(gateway): add routes for customer, movie and booking services
| * feat(gateway): forward user context headers from jwt
| * chore(gateway): configure service urls and jwt secret
|/
...
* chore(infra): add docker compose for sql server, mongodb and mysql
* chore: initialize repository with gitignore
```

**Checklist commit khi nộp bài:**
- [ ] ≥ 1 commit cho mỗi TODO (khoảng 55–65 commit), không có commit kiểu `update`, `final`, `fix bug`
- [ ] Mọi tiêu đề đúng dạng `type(scope): subject`, ≤ 72 ký tự
- [ ] Không commit `target/`, `docker/`, `.idea/`, file chứa mật khẩu thật
- [ ] Mỗi commit build được (thử `git checkout <hash>` rồi `mvn -q compile` ở vài commit ngẫu nhiên)
- [ ] Có tag `v1.0.0` trên `main`

> **Tùy chọn:** cài **commitlint** + **husky** (cần Node.js) để chặn commit sai định dạng ngay khi `git commit`: `npm i -D @commitlint/cli @commitlint/config-conventional husky`, tạo `commitlint.config.js` với `export default { extends: ['@commitlint/config-conventional'] };`.

---

## Phụ lục – Đối chiếu Checklist (Assignment1.md mục 7) ↔ Test Postman

| Checklist | Test case |
|---|---|
| Login Admin/Customer, sai mật khẩu, INACTIVE | 1.1 – 1.5 |
| Không token / token sai / sai role | 1.6, 1.7, 2.10, 2.17, 3.3, 3.8, 6.11, 6.12, 7.9, 8.3 |
| Header giả mạo bị ghi đè | 2.18 |
| Register, trùng email, validation | 2.1 – 2.3 |
| Profile, đổi mật khẩu | 2.5 – 2.9 |
| Admin CRUD customer, soft delete | 2.11 – 2.16 |
| Genre/Room CRUD + BR03 | 3.1 – 3.10, 4.8, 5.11, 5.12 |
| Movie CRUD + search | 4.1 – 4.7 |
| Showtime BR04, BR05, BR06, filter | 5.1 – 5.10 |
| Seat map | 6.1, 6.14 |
| Đặt vé + BR07 → BR10 | 6.2 – 6.13 |
| BR14 (503) | 6.15 (thủ công) |
| History, BR11, BR12, giải phóng ghế | 7.1 – 7.10 |
| Report + sort DESC + BR13 | 8.1 – 8.5 |
| BR15 – tham chiếu không tồn tại (MongoDB không có FK) | 4.7, 5.13 |
| BR16 – Unicode trên SQL Server, kiểu dữ liệu từng DB | 2.6, mục 7.2b |
