# Saga Pattern – Demo: Order Saga (Orchestration) với Spring Boot 4.1.1 + Docker

> **Chapter 06 – Transactions in Microservices** 
> Stack: **Java 21 · Spring Boot 4.1.1 (Spring Framework 7) · Spring MVC · RestClient · Spring Data JPA · Spring Data MongoDB · Lombok · Docker Compose · Postman**.
> **Database per service** – mỗi service một loại database riêng, chạy trong Docker:
>
> | Service | Port | Database | Docker image |
> |---|---|---|---|
> | `order-service` | 8081 | **MySQL 8.4** – `orderdb` | `mysql:8.4` |
> | `inventory-service` | 8082 | **MongoDB 8.0** – `inventorydb` (replica set 1 node) | `mongo:8.0` |
> | `payment-service` | 8083 | **SQL Server 2022** – `paymentdb` | `mcr.microsoft.com/mssql/server:2022-latest` |
>
> Không cần Kafka hay Eureka.

---

## Mục lục

1. [Mục tiêu](#1-mục-tiêu)
2. [Bài toán và kiến trúc](#2-bài-toán-và-kiến-trúc)
3. [Yêu cầu TODO](#3-yêu-cầu-todo)
4. [Chuẩn bị môi trường và thay đổi của Spring Boot 4.1.1](#4-chuẩn-bị-môi-trường-và-thay-đổi-của-spring-boot-411)
5. [Hướng dẫn step-by-step](#5-hướng-dẫn-step-by-step)
6. [Chạy và kiểm thử (Postman)](#6-chạy-và-kiểm-thử-postman)
7. [Đối chiếu code với lý thuyết](#7-đối-chiếu-code-với-lý-thuyết)
8. [Bài tập mở rộng](#8-bài-tập-mở-rộng)
9. [Lỗi thường gặp](#9-lỗi-thường-gặp)
10. [Câu hỏi ôn tập](#10-câu-hỏi-ôn-tập)

---

## 1. Mục tiêu

Sau bài demo, sinh viên có thể:

- Giải thích vì sao `@Transactional` **không** rollback được dữ liệu ở service khác (Slide 4, 8), nhất là khi mỗi service dùng **một loại database khác nhau** (MySQL, SQL Server, MongoDB).
- Chia một nghiệp vụ phân tán thành **chuỗi local transaction** (Slide 20, 22).
- Viết **compensating transaction** đúng 3 yêu cầu: *Idempotent, Reversible, Reliable* (Slide 25).
- Cài đặt **Saga Orchestrator** điều phối các bước và hoàn tác theo thứ tự ngược (Slide 24).
- Theo dõi **trạng thái Saga**: `PENDING → EXECUTING → COMPLETED` hoặc `FAILED → COMPENSATING → COMPENSATED` (Slide 27).
- Đóng gói các service và database bằng **Docker Compose**, kiểm thử tự động các kịch bản bằng **Postman**.

---

## 2. Bài toán và kiến trúc

### 2.1. Nghiệp vụ "Đặt hàng"

Khách hàng đặt mua sản phẩm. Hệ thống phải thực hiện 3 việc trên **3 service và 3 database khác loại**:

| Bước | Service | Database | Local transaction | Compensating transaction |
|---|---|---|---|---|
| 1 | `order-service` (8081) | MySQL | Tạo order trạng thái `PENDING` | `cancelOrder` → `CANCELLED` |
| 2 | `inventory-service` (8082) | MongoDB | `reserve`: trừ tồn kho, ghi `Reservation` | `release`: cộng lại tồn kho |
| 3 | `payment-service` (8083) | SQL Server | `pay`: trừ tiền trong ví, ghi `Payment` | `refund`: hoàn tiền |
| 4 | `order-service` | MySQL | `confirmOrder` → `CONFIRMED` | – |

> Đây đúng là ví dụ *Partial Failure* ở Slide 5: Order tạo xong, Inventory trừ kho xong, nhưng Payment thất bại. Nếu không có Saga, kho bị trừ mà đơn không được thanh toán.
>
> Ba database khác loại khiến việc dùng một transaction chung (2PC/XA) gần như không khả thi: MongoDB không tham gia XA cùng MySQL và SQL Server. Saga là lựa chọn tự nhiên.

### 2.2. Kiến trúc (Orchestration) trong Docker

```
                           POST /api/orders
                                 │
┌──────────────────────── Docker network: saga-demo_default ─────────────────────────┐
│                                ▼                                                    │
│  ┌──────────────── order-service :8081 ────────────────┐      ┌──────────────┐      │
│  │ OrderController ─► OrderSagaOrchestrator ─► Order-  │─────►│ mysql :3306  │      │
│  │                       │          │         Service  │      │  (orderdb)   │      │
│  └───────────────────────┼──────────┼──────────────────┘      └──────────────┘      │
│          reserve/release │          │ pay/refund                                    │
│                          ▼          ▼                                               │
│     inventory-service :8082     payment-service :8083                               │
│              │                          │                                           │
│              ▼                          ▼                                           │
│     ┌────────────────┐         ┌──────────────────┐                                 │
│     │ mongodb :27017 │         │ sqlserver :1433  │                                 │
│     │ (inventorydb)  │         │   (paymentdb)    │                                 │
│     └────────────────┘         └──────────────────┘                                 │
└─────────────────────────────────────────────────────────────────────────────────────┘
  Cổng mở ra máy host: 8081, 8082, 8083 · MySQL 3307 · SQL Server 14330 · MongoDB 27017
```

> Trong network Docker, các container gọi nhau bằng **tên service** (`mysql`, `mongodb`, `payment-service`…), không dùng `localhost`.
> Từ máy host (IntelliJ, Postman), dùng `localhost` + cổng đã map.

### 2.3. Luồng xử lý

**Thành công (happy path):**

```
createPendingOrder ─► reserve ─► pay ─► confirmOrder
 saga: PENDING ─► EXECUTING ───────────► COMPLETED   | order: CONFIRMED
```

**Thất bại ở bước Payment:**

```
createPendingOrder ─► reserve ─► pay ✗
                                  │
             saga: FAILED ─► COMPENSATING
                                  │
               release (hoàn tác bước 2) ─► cancelOrder (hoàn tác bước 1)
                                  │
             saga: COMPENSATED     | order: CANCELLED
```

**Dữ liệu mẫu ban đầu:**

| Product (MongoDB) | Tên | Giá | Tồn kho |   | Ví (SQL Server) | Số dư |
|---|---|---|---|---|---|---|
| `P1` | Laptop | 1000 | 10 |   | `C1` | 5000 |
| `P2` | Mouse | 20 | 5 |   | `C2` | 100 |

---

## 3. Yêu cầu TODO

> Sinh viên hoàn thành lần lượt các TODO. Mỗi TODO có **tiêu chí đạt** để tự kiểm tra. Lời giải chi tiết nằm ở mục 5.

### Phần 0 – Hạ tầng Docker

**TODO-0: Docker Compose cho 3 database**
- Viết `docker-compose.yml` chạy MySQL (`orderdb`), SQL Server (`paymentdb`), MongoDB (replica set `rs0`), có `healthcheck` và `volume`.
- ✅ Tiêu chí: `docker compose ps` hiển thị 3 container ở trạng thái `healthy`.

### Phần A – inventory-service (cổng 8082, MongoDB)

**TODO-1: Document và dữ liệu mẫu**
- Tạo `@Document` `Product(id, name, price, stock)` và `Reservation(orderId, productId, quantity, status)`; `status` là enum `RESERVED | RELEASED`.
- Nạp sẵn `P1`, `P2` bằng `CommandLineRunner` (MongoDB không dùng `data.sql`), chỉ nạp khi collection rỗng.
- ✅ Tiêu chí: `GET /api/inventory/products` trả về 2 sản phẩm.

**TODO-2: Local transaction `reserve` và compensation `release`**
- Khai báo `MongoTransactionManager` để `@Transactional` hoạt động với MongoDB.
- `POST /api/inventory/reserve` với body `{orderId, productId, quantity}`:
  - Không tìm thấy sản phẩm → **404 NOT_FOUND**.
  - Không đủ hàng → **409 CONFLICT** kèm message.
  - Đủ hàng → trừ `stock`, lưu `Reservation` trạng thái `RESERVED`.
  - Gọi lại cùng `orderId` → **không trừ kho lần 2** (idempotent).
- `POST /api/inventory/release/{orderId}`:
  - Nếu reservation đang `RESERVED` → cộng lại kho, đổi sang `RELEASED`.
  - Gọi nhiều lần, hoặc gọi khi chưa reserve → không lỗi, không cộng kho thêm (idempotent).
- ✅ Tiêu chí: gọi `release` 2 lần liên tiếp, tồn kho chỉ tăng 1 lần.

### Phần B – payment-service (cổng 8083, SQL Server)

**TODO-3: Entity và dữ liệu mẫu**
- Tạo entity `Wallet(customerId, balance)` và `Payment(orderId, customerId, amount, status)`; `status` là enum `PAID | REFUNDED`.
- Nạp sẵn ví `C1 = 5000`, `C2 = 100` bằng `data.sql` viết theo kiểu **idempotent** (không lỗi khi restart).

**TODO-4: Local transaction `pay` và compensation `refund`**
- `POST /api/payments/pay` với body `{orderId, customerId, amount}`: không có ví → **404**; số dư không đủ → **402 PAYMENT_REQUIRED**; đủ → trừ tiền, lưu `Payment(PAID)`; idempotent theo `orderId`.
- `POST /api/payments/refund/{orderId}`: hoàn tiền nếu đang `PAID`, idempotent.
- ✅ Tiêu chí: `C2` mua đơn 1000 → nhận 402 và message "Số dư không đủ…".

### Phần C – order-service (cổng 8081, MySQL) – trọng tâm Saga

**TODO-5: Entity Order, trạng thái và Saga log**
- `Order(id, customerId, productId, quantity, amount, status, sagaStatus, failureReason)`.
- `OrderStatus`: `PENDING | CONFIRMED | CANCELLED`.
- `SagaStatus`: đúng 6 trạng thái của Slide 27: `PENDING | EXECUTING | COMPLETED | FAILED | COMPENSATING | COMPENSATED`.
- `SagaLog(orderId, step, result, message, time)` để ghi lại từng bước (State Management – Slide 25).

**TODO-6: OrderService – mỗi method là một local transaction**
- `createPendingOrder`, `updateSagaStatus`, `markFailed`, `confirmOrder`, `cancelOrder` (compensation của bước 1), `log`.

**TODO-7: Client gọi service khác**
- `InventoryClient.reserve / release`, `PaymentClient.pay / refund` dùng `RestClient`, tạo từ bean `RestClient.Builder` mà Spring Boot 4 auto-configure (starter `spring-boot-starter-restclient`).
- Lỗi 4xx/5xx hoặc service không phản hồi đều ném `RestClientException`.

**TODO-8: OrderSagaOrchestrator**
- Thực hiện tuần tự: tạo order → reserve → pay → confirm.
- Sau mỗi bước **thành công**, đẩy compensating action tương ứng vào một **stack**.
- Khi một bước thất bại: `markFailed` → `COMPENSATING` → chạy compensation theo thứ tự **ngược (LIFO)**, **retry tối đa 3 lần** bằng `RetryTemplate` (có sẵn trong Spring Framework 7) → `cancelOrder` → `COMPENSATED`.
- Orchestrator **không** được đánh `@Transactional`.

**TODO-9: REST API**
- `POST /api/orders` – chạy Saga, trả về order kèm `status` và `sagaStatus`.
- `GET /api/orders` – danh sách order.
- `GET /api/orders/{id}/saga-logs` – các bước Saga của một order.

### Phần D – Docker hoá và kiểm thử

**TODO-10: Dockerfile cho 3 service**, bổ sung 3 service vào `docker-compose.yml`, chạy toàn bộ hệ thống bằng `docker compose up -d --build`.

**TODO-11: Postman** – Import collection, chạy **Collection Runner** cho KB1 → KB5, KB7 (tất cả test phải xanh), chạy thủ công KB6 (và KB8 nếu làm). Chụp kết quả Runner và saga-logs.

### Gợi ý barem (10 điểm)

| Phần | Nội dung | Điểm |
|---|---|---|
| 0 | TODO-0: compose 3 database, healthcheck | 1.0 |
| A | TODO-1, TODO-2 (MongoDB transaction, idempotent) | 1.5 |
| B | TODO-3, TODO-4 (SQL Server, idempotent) | 1.5 |
| C | TODO-5, 6, 7 | 1.5 |
| C | TODO-8: orchestrator, compensation đúng thứ tự, retry | 2.5 |
| C | TODO-9 | 0.5 |
| D | TODO-10, TODO-11: chạy bằng Docker, Postman pass đủ kịch bản | 1.5 |

---

## 4. Chuẩn bị môi trường và thay đổi của Spring Boot 4.1.1

### 4.1. Công cụ

- **JDK 21** (Spring Boot 4 yêu cầu tối thiểu Java 17), Maven 3.9+ (hoặc Maven Wrapper đi kèm project tạo từ Initializr).
- **Docker Desktop** (Windows: bật WSL 2). Cấp cho Docker **tối thiểu 4 GB RAM**, vì riêng SQL Server cần khoảng 2 GB.
- IntelliJ IDEA (khuyên dùng).
- **Postman** (bản desktop, để gọi được `localhost`).
- Tuỳ chọn để xem dữ liệu: MySQL Workbench, SSMS / Azure Data Studio, MongoDB Compass.

Cấu trúc thư mục cuối cùng:

```
saga-demo/
├── docker-compose.yml
├── Saga_Postman_Collection.json
├── inventory-service/   (port 8082, MongoDB)    + Dockerfile
├── payment-service/     (port 8083, SQL Server) + Dockerfile
└── order-service/       (port 8081, MySQL)      + Dockerfile  ← chứa Orchestrator
```

### 4.2. Những thay đổi khi chuyển từ Spring Boot 3.x lên 4.1.1 (liên quan đến demo)

| Chủ đề | Spring Boot 3.x | Spring Boot 4.1.1 | Ảnh hưởng tới demo |
|---|---|---|---|
| Nền tảng | Spring Framework 6, Jakarta EE 10, Hibernate 6 | **Spring Framework 7**, **Jakarta EE 11**, **Hibernate 7** | Import vẫn là `jakarta.persistence.*`, code entity không đổi |
| Starter web | `spring-boot-starter-web` | **`spring-boot-starter-webmvc`** (tên cũ đã deprecated) | Đổi tên trong `pom.xml` |
| RestClient | Có sẵn khi dùng starter web | Tách thành module riêng: **`spring-boot-starter-restclient`** (auto-configure `RestClient.Builder`) | order-service thêm starter này và inject `RestClient.Builder` |
| Hiện message lỗi | `server.error.include-message` | **`spring.web.error.include-message`** | Đổi tên property ở inventory/payment |
| Timeout HTTP client | `spring.http.client.connect-timeout` | **`spring.http.clients.connect-timeout`**, **`spring.http.clients.read-timeout`** | Cấu hình ở order-service |
| MongoDB | `spring.data.mongodb.uri` | **`spring.mongodb.uri`** (các property kết nối chuyển sang `spring.mongodb.*`) | inventory-service |
| Jackson | Jackson 2 (`com.fasterxml.jackson.databind`) | **Jackson 3** (package `tools.jackson.databind`; annotation vẫn là `com.fasterxml.jackson.annotation`) | Ngày giờ (`LocalDateTime`) mặc định trả về dạng chuỗi ISO, không phải mảng số |
| Retry | Phải thêm thư viện `spring-retry` | **`RetryTemplate`, `RetryPolicy`, `@Retryable` có sẵn trong Spring Framework 7** (`org.springframework.core.retry`) | Orchestrator dùng `RetryTemplate` cho compensation |
| HTTP Interface | `@HttpExchange` + tự tạo proxy | Có thêm **`@ImportHttpServices`** và property `spring.http.serviceclient.<group>.base-url` | Bài tập mở rộng EXT-6 |
| Test | `@MockBean`, `spring-boot-starter-test` | `@MockitoBean`; test starter tách theo module (`spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`…) | Không dùng trong demo, lưu ý khi viết test |
| Lombok | Thường không cần cấu hình thêm | Nên khai báo Lombok trong `annotationProcessorPaths` của `maven-compiler-plugin` (Initializr đã sinh sẵn) | Xem `pom.xml` ở Step 1 |

> Mẹo: nếu nâng cấp project cũ, có thể dùng OpenRewrite recipe `org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0` để đổi tên starter và property tự động.

---

## 5. Hướng dẫn step-by-step

### Step 1 – Tạo 3 project bằng Spring Initializr

Vào <https://start.spring.io> và tạo 3 project:

| Mục | Giá trị |
|---|---|
| Project / Language | Maven / Java |
| Spring Boot | **4.1.1** |
| Group | `com.mss301` |
| Artifact | `inventory-service`, `payment-service`, `order-service` |
| Java | 21 |

| Project | Dependencies trên Initializr |
|---|---|
| inventory-service | **Spring Web, Spring Data MongoDB, Lombok** |
| payment-service | **Spring Web, Spring Data JPA, MS SQL Server Driver, Lombok** |
| order-service | **Spring Web, Spring Data JPA, MySQL Driver, HTTP Client (RestClient), Lombok** |

`pom.xml` mẫu của **order-service** (đầy đủ nhất):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.1</version>
        <relativePath/>
    </parent>
    <groupId>com.mss301</groupId>
    <artifactId>order-service</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <properties>
        <java.version>21</java.version>
    </properties>
    <dependencies>
        <!-- Spring Boot 4: spring-boot-starter-web đổi tên thành spring-boot-starter-webmvc -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>
        <!-- Spring Boot 4: RestClient.Builder được auto-configure bởi starter riêng -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-restclient</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
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
                            <version>${lombok.version}</version>
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

Hai project còn lại giống hệt, chỉ khác `artifactId` và khối `<dependencies>`:

**inventory-service** (MongoDB):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-mongodb</artifactId>
</dependency>
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
```

**payment-service** (SQL Server):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>com.microsoft.sqlserver</groupId>
    <artifactId>mssql-jdbc</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
```

> Phiên bản driver (`mysql-connector-j`, `mssql-jdbc`, MongoDB driver) do Spring Boot quản lý, không cần ghi `<version>`.

---

### Step 2 – Hạ tầng database bằng Docker Compose (TODO-0)

Tạo `saga-demo/docker-compose.yml`. Ở bước này mới có **3 database**; 3 service sẽ được thêm vào ở Step 10.

```yaml
name: saga-demo

services:
  # ---------- order-service DB ----------
  mysql:
    image: mysql:8.4
    container_name: saga-mysql
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: orderdb
    ports:
      - "3307:3306"            # host 3307 để không đụng MySQL cài sẵn trên máy
    volumes:
      - mysql-data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-uroot", "-proot"]
      interval: 5s
      timeout: 5s
      retries: 20

  # ---------- payment-service DB ----------
  sqlserver:
    image: mcr.microsoft.com/mssql/server:2022-latest
    container_name: saga-sqlserver
    environment:
      ACCEPT_EULA: "Y"
      MSSQL_SA_PASSWORD: "Saga@12345"   # phải đủ mạnh: chữ hoa, thường, số, ký tự đặc biệt
      MSSQL_PID: "Developer"
    ports:
      - "14330:1433"           # host 14330 để không đụng SQL Server cài sẵn trên máy
    volumes:
      - mssql-data:/var/opt/mssql
    healthcheck:
      # Vừa kiểm tra SQL Server sẵn sàng, vừa tạo database paymentdb nếu chưa có
      test: ["CMD-SHELL", "/opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P 'Saga@12345' -C -Q \"IF DB_ID('paymentdb') IS NULL CREATE DATABASE paymentdb\" || exit 1"]
      interval: 10s
      timeout: 10s
      retries: 30
      start_period: 20s

  # ---------- inventory-service DB ----------
  mongodb:
    image: mongo:8.0
    container_name: saga-mongodb
    command: ["--replSet", "rs0", "--bind_ip_all"]   # transaction trong MongoDB cần replica set
    ports:
      - "27017:27017"
    volumes:
      - mongo-data:/data/db
    healthcheck:
      # Lần đầu: khởi tạo replica set 1 node; healthy khi node đã là PRIMARY
      test: ["CMD", "mongosh", "--quiet", "--eval", "try { rs.status() } catch (e) { rs.initiate({_id: 'rs0', members: [{_id: 0, host: 'localhost:27017'}]}) }; quit(db.hello().isWritablePrimary ? 0 : 1)"]
      interval: 5s
      timeout: 10s
      retries: 30
      start_period: 10s

volumes:
  mysql-data:
  mssql-data:
  mongo-data:
```

Chạy:

```bash
cd saga-demo
docker compose up -d mysql sqlserver mongodb
docker compose ps          # chờ cả 3 container chuyển sang (healthy)
```

> **Vì sao MongoDB phải chạy replica set?** Bước `reserve` ghi vào **2 collection** (`product` và `reservation`). Muốn 2 thao tác này là một local transaction ACID, MongoDB yêu cầu chạy dạng replica set (một node cũng được). Chạy MongoDB đơn lẻ sẽ gặp lỗi *"Transaction numbers are only allowed on a replica set member or mongos"*.
>
> **Vì sao chuỗi kết nối Mongo có `directConnection=true`?** Replica set khai báo member là `localhost:27017`. Với `directConnection=true`, driver kết nối thẳng vào node được chỉ định mà không "khám phá" lại địa chỉ member, nên dùng được cả từ máy host lẫn từ container khác.

---

### Step 3 – inventory-service với MongoDB (TODO-1, TODO-2)

Package gốc: `com.mss301.inventory`

```
com.mss301.inventory
├── InventoryServiceApplication.java
├── config/MongoConfig.java, DataSeeder.java
├── controller/InventoryController.java
├── dto/ReserveRequest.java
├── entity/Product.java, Reservation.java, ReservationStatus.java
├── repository/ProductRepository.java, ReservationRepository.java
└── service/InventoryService.java
```

**3.1. `src/main/resources/application.properties`**

```properties
server.port=8082
spring.application.name=inventory-service

# Spring Boot 4: spring.data.mongodb.uri -> spring.mongodb.uri
# Chạy trong Docker sẽ được ghi đè bằng biến môi trường MONGO_URI
spring.mongodb.uri=${MONGO_URI:mongodb://localhost:27017/inventorydb?directConnection=true}

# Spring Boot 4: server.error.include-message -> spring.web.error.include-message
spring.web.error.include-message=always
```

> - `${MONGO_URI:...}`: đọc biến môi trường `MONGO_URI`, nếu không có thì dùng giá trị sau dấu `:` (chạy từ IntelliJ).
> - `include-message=always`: body lỗi trả về có trường `message`, để order-service đọc được lý do thất bại.

**3.2. Document**

`entity/Product.java`

```java
package com.mss301.inventory.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("product")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Product {
    @Id
    private String id;
    private String name;
    private double price;
    private int stock;
}
```

`entity/ReservationStatus.java`

```java
package com.mss301.inventory.entity;

public enum ReservationStatus { RESERVED, RELEASED }
```

`entity/Reservation.java`

```java
package com.mss301.inventory.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("reservation")
@Getter @Setter @NoArgsConstructor
public class Reservation {
    @Id
    private Long orderId;          // 1 order = 1 reservation -> dùng orderId làm _id
    private String productId;
    private int quantity;
    private ReservationStatus status;   // MongoDB lưu enum dạng chuỗi
}
```

> **Lưu ý:** `@Id` ở đây là `org.springframework.data.annotation.Id`, **không phải** `jakarta.persistence.Id`.
>
> **Vì sao cần collection `reservation`?** Compensation phải *Reversible*: muốn cộng lại kho thì phải biết order nào đã giữ bao nhiêu sản phẩm. Trạng thái `RELEASED` giúp `release` trở thành *Idempotent*.

**3.3. Repository**

```java
package com.mss301.inventory.repository;

import com.mss301.inventory.entity.Product;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProductRepository extends MongoRepository<Product, String> {
}
```

```java
package com.mss301.inventory.repository;

import com.mss301.inventory.entity.Reservation;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ReservationRepository extends MongoRepository<Reservation, Long> {
}
```

**3.4. Cấu hình transaction và dữ liệu mẫu**

`config/MongoConfig.java`

```java
package com.mss301.inventory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

@Configuration
public class MongoConfig {

    // Spring Boot KHÔNG tự tạo transaction manager cho MongoDB.
    // Thiếu bean này thì @Transactional trong InventoryService không có tác dụng.
    @Bean
    MongoTransactionManager transactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory);
    }
}
```

`config/DataSeeder.java`

```java
package com.mss301.inventory.config;

import com.mss301.inventory.entity.Product;
import com.mss301.inventory.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class DataSeeder {

    // MongoDB không có data.sql -> nạp dữ liệu mẫu khi khởi động, chỉ nạp nếu còn trống
    @Bean
    CommandLineRunner seedProducts(ProductRepository repo) {
        return args -> {
            if (repo.count() == 0) {
                repo.saveAll(List.of(
                        new Product("P1", "Laptop", 1000, 10),
                        new Product("P2", "Mouse", 20, 5)));
            }
        };
    }
}
```

**3.5. DTO – `dto/ReserveRequest.java`**

```java
package com.mss301.inventory.dto;

public record ReserveRequest(Long orderId, String productId, int quantity) {
}
```

**3.6. Service – `service/InventoryService.java`**

```java
package com.mss301.inventory.service;

import com.mss301.inventory.dto.ReserveRequest;
import com.mss301.inventory.entity.*;
import com.mss301.inventory.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final ProductRepository productRepository;
    private final ReservationRepository reservationRepository;

    // LOCAL TRANSACTION của bước 2: trừ kho + ghi reservation
    // (2 collection trong cùng inventorydb -> MongoDB multi-document transaction)
    @Transactional
    public Reservation reserve(ReserveRequest req) {
        // Idempotent: nếu orderId này đã được reserve rồi thì trả lại kết quả cũ
        var existing = reservationRepository.findById(req.orderId());
        if (existing.isPresent()) {
            log.info("Order {} đã được reserve trước đó -> bỏ qua", req.orderId());
            return existing.get();
        }

        Product product = productRepository.findById(req.productId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy sản phẩm " + req.productId()));

        if (product.getStock() < req.quantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Không đủ hàng: còn " + product.getStock() + ", cần " + req.quantity());
        }

        product.setStock(product.getStock() - req.quantity());
        productRepository.save(product);   // MongoDB KHÔNG có dirty checking như JPA -> phải save

        Reservation r = new Reservation();
        r.setOrderId(req.orderId());
        r.setProductId(req.productId());
        r.setQuantity(req.quantity());
        r.setStatus(ReservationStatus.RESERVED);
        log.info("RESERVE order={} product={} qty={} -> stock còn {}",
                req.orderId(), req.productId(), req.quantity(), product.getStock());
        return reservationRepository.save(r);
    }

    // COMPENSATING TRANSACTION của bước 2: cộng lại kho
    @Transactional
    public void release(Long orderId) {
        var opt = reservationRepository.findById(orderId);
        if (opt.isEmpty() || opt.get().getStatus() == ReservationStatus.RELEASED) {
            // Idempotent: chưa reserve hoặc đã release rồi -> không làm gì
            log.info("RELEASE order={} -> không có gì để hoàn tác", orderId);
            return;
        }
        Reservation r = opt.get();
        Product product = productRepository.findById(r.getProductId()).orElseThrow();
        product.setStock(product.getStock() + r.getQuantity());
        r.setStatus(ReservationStatus.RELEASED);
        productRepository.save(product);
        reservationRepository.save(r);
        log.info("RELEASE order={} -> trả lại {} {}, stock = {}",
                orderId, r.getQuantity(), product.getId(), product.getStock());
    }
}
```

> - `@Transactional` ở đây là đúng: thao tác chỉ chạm vào **một** database (`inventorydb`), nên ACID vẫn được đảm bảo trong phạm vi bước này.
> - Khác với JPA: Spring Data MongoDB **không** tự ghi thay đổi khi transaction commit, nên sau khi sửa object phải gọi `save(...)`.

**3.7. Controller – `controller/InventoryController.java`**

```java
package com.mss301.inventory.controller;

import com.mss301.inventory.dto.ReserveRequest;
import com.mss301.inventory.entity.*;
import com.mss301.inventory.repository.*;
import com.mss301.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final ProductRepository productRepository;
    private final ReservationRepository reservationRepository;

    @PostMapping("/reserve")
    public Reservation reserve(@RequestBody ReserveRequest req) {
        return inventoryService.reserve(req);
    }

    @PostMapping("/release/{orderId}")
    public String release(@PathVariable Long orderId) {
        inventoryService.release(orderId);
        return "RELEASED";
    }

    @GetMapping("/products")
    public List<Product> products() {
        return productRepository.findAll();
    }

    @GetMapping("/reservations")
    public List<Reservation> reservations() {
        return reservationRepository.findAll();
    }
}
```

**3.8. Main class** (Initializr đã sinh sẵn)

```java
package com.mss301.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class InventoryServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}
```

✅ **Kiểm tra nhanh:** chạy app rồi mở `http://localhost:8082/api/inventory/products`.

---

### Step 4 – payment-service với SQL Server (TODO-3, TODO-4)

Package gốc: `com.mss301.payment`.

**4.1. `application.properties`**

```properties
server.port=8083
spring.application.name=payment-service

spring.datasource.url=${DB_URL:jdbc:sqlserver://localhost:14330;databaseName=paymentdb;encrypt=true;trustServerCertificate=true}
spring.datasource.username=${DB_USER:sa}
spring.datasource.password=${DB_PASSWORD:Saga@12345}

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false

# Chạy data.sql SAU khi Hibernate tạo bảng, và chạy cả với DB không nhúng (SQL Server)
spring.jpa.defer-datasource-initialization=true
spring.sql.init.mode=always

spring.web.error.include-message=always
```

> - `trustServerCertificate=true`: SQL Server trong Docker dùng chứng chỉ tự ký; thiếu tham số này sẽ gặp lỗi `PKIX path building failed`.
> - `spring.sql.init.mode=always`: mặc định Spring Boot **chỉ** chạy `data.sql` với DB nhúng (H2…). Với SQL Server/MySQL phải bật thủ công.
> - `ddl-auto=update`: dữ liệu được giữ lại qua các lần restart (lưu trong Docker volume).

**4.2. `src/main/resources/data.sql`** – viết kiểu idempotent vì chạy lại mỗi lần khởi động

```sql
IF NOT EXISTS (SELECT 1 FROM wallet WHERE customer_id = 'C1') INSERT INTO wallet (customer_id, balance) VALUES ('C1', 5000);
IF NOT EXISTS (SELECT 1 FROM wallet WHERE customer_id = 'C2') INSERT INTO wallet (customer_id, balance) VALUES ('C2', 100);
```

**4.3. Entity**

```java
package com.mss301.payment.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

@Entity
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Wallet {
    @Id
    private String customerId;
    private double balance;
}
```

```java
package com.mss301.payment.entity;

public enum PaymentStatus { PAID, REFUNDED }
```

```java
package com.mss301.payment.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter @Setter @NoArgsConstructor
public class Payment {
    @Id
    private Long orderId;
    private String customerId;
    private double amount;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;
}
```

**4.4. Repository**

```java
package com.mss301.payment.repository;

import com.mss301.payment.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletRepository extends JpaRepository<Wallet, String> {
}
```

```java
package com.mss301.payment.repository;

import com.mss301.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
```

**4.5. DTO**

```java
package com.mss301.payment.dto;

public record PaymentRequest(Long orderId, String customerId, double amount) {
}
```

**4.6. Service – `service/PaymentService.java`**

```java
package com.mss301.payment.service;

import com.mss301.payment.dto.PaymentRequest;
import com.mss301.payment.entity.*;
import com.mss301.payment.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final WalletRepository walletRepository;
    private final PaymentRepository paymentRepository;

    // LOCAL TRANSACTION của bước 3: trừ tiền ví + ghi payment (cùng paymentdb trên SQL Server)
    @Transactional
    public Payment pay(PaymentRequest req) {
        var existing = paymentRepository.findById(req.orderId());
        if (existing.isPresent()) {               // idempotent
            return existing.get();
        }

        Wallet wallet = walletRepository.findById(req.customerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy ví của " + req.customerId()));

        if (wallet.getBalance() < req.amount()) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "Số dư không đủ: còn " + wallet.getBalance() + ", cần " + req.amount());
        }

        wallet.setBalance(wallet.getBalance() - req.amount());   // JPA dirty checking tự UPDATE

        Payment p = new Payment();
        p.setOrderId(req.orderId());
        p.setCustomerId(req.customerId());
        p.setAmount(req.amount());
        p.setStatus(PaymentStatus.PAID);
        log.info("PAY order={} customer={} amount={} -> balance còn {}",
                req.orderId(), req.customerId(), req.amount(), wallet.getBalance());
        return paymentRepository.save(p);
    }

    // COMPENSATING TRANSACTION của bước 3: hoàn tiền
    @Transactional
    public void refund(Long orderId) {
        var opt = paymentRepository.findById(orderId);
        if (opt.isEmpty() || opt.get().getStatus() == PaymentStatus.REFUNDED) {
            log.info("REFUND order={} -> không có gì để hoàn tác", orderId);
            return;
        }
        Payment p = opt.get();
        Wallet wallet = walletRepository.findById(p.getCustomerId()).orElseThrow();
        wallet.setBalance(wallet.getBalance() + p.getAmount());
        p.setStatus(PaymentStatus.REFUNDED);
        log.info("REFUND order={} -> hoàn {} cho {}, balance = {}",
                orderId, p.getAmount(), p.getCustomerId(), wallet.getBalance());
    }
}
```

**4.7. Controller**

```java
package com.mss301.payment.controller;

import com.mss301.payment.dto.PaymentRequest;
import com.mss301.payment.entity.*;
import com.mss301.payment.repository.*;
import com.mss301.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final WalletRepository walletRepository;
    private final PaymentRepository paymentRepository;

    @PostMapping("/pay")
    public Payment pay(@RequestBody PaymentRequest req) {
        return paymentService.pay(req);
    }

    @PostMapping("/refund/{orderId}")
    public String refund(@PathVariable Long orderId) {
        paymentService.refund(orderId);
        return "REFUNDED";
    }

    @GetMapping("/wallets")
    public List<Wallet> wallets() {
        return walletRepository.findAll();
    }

    @GetMapping
    public List<Payment> payments() {
        return paymentRepository.findAll();
    }
}
```

**4.8. Main class**

```java
package com.mss301.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
```

✅ **Kiểm tra nhanh:** `http://localhost:8083/api/payments/wallets`.

---

### Step 5 – order-service với MySQL: cấu hình và entity (TODO-5)

Package gốc: `com.mss301.order`

```
com.mss301.order
├── OrderServiceApplication.java
├── config/RestClientConfig.java
├── client/InventoryClient.java, PaymentClient.java
├── controller/OrderController.java
├── dto/CreateOrderRequest.java, ReserveRequest.java, PaymentRequest.java
├── entity/Order.java, OrderStatus.java, SagaStatus.java, SagaLog.java
├── repository/OrderRepository.java, SagaLogRepository.java
├── saga/OrderSagaOrchestrator.java     ← trái tim của Saga
└── service/OrderService.java
```

**5.1. `application.properties`**

```properties
server.port=8081
spring.application.name=order-service

spring.datasource.url=${DB_URL:jdbc:mysql://localhost:3307/orderdb?createDatabaseIfNotExist=true&allowPublicKeyRetrieval=true&useSSL=false}
spring.datasource.username=${DB_USER:root}
spring.datasource.password=${DB_PASSWORD:root}
spring.jpa.hibernate.ddl-auto=update

# Địa chỉ các service tham gia Saga (trong Docker sẽ ghi đè bằng tên container)
services.inventory.url=${INVENTORY_URL:http://localhost:8082}
services.payment.url=${PAYMENT_URL:http://localhost:8083}

# Spring Boot 4: timeout chung cho mọi HTTP client được auto-configure (spring.http.clients.*)
spring.http.clients.connect-timeout=2s
spring.http.clients.read-timeout=5s

# CHỈ DÙNG CHO DEMO KB8: dừng N ms trước bước thanh toán để kịp tắt service
saga.demo.payment-delay-ms=${PAYMENT_DELAY_MS:0}
```

> `allowPublicKeyRetrieval=true&useSSL=false`: MySQL 8.4 dùng `caching_sha2_password`; khi kết nối không SSL, driver cần được phép lấy public key, nếu không sẽ báo *"Public Key Retrieval is not allowed"*.

**5.2. Enum trạng thái**

```java
package com.mss301.order.entity;

public enum OrderStatus { PENDING, CONFIRMED, CANCELLED }
```

```java
package com.mss301.order.entity;

// Đúng 6 trạng thái ở Slide 27
public enum SagaStatus { PENDING, EXECUTING, COMPLETED, FAILED, COMPENSATING, COMPENSATED }
```

> **Phân biệt 2 trạng thái:**
> - `OrderStatus` là trạng thái **nghiệp vụ** mà khách hàng nhìn thấy.
> - `SagaStatus` là trạng thái **kỹ thuật** của tiến trình Saga, phục vụ việc theo dõi và khôi phục.

**5.3. Entity `Order` và `SagaLog`**

```java
package com.mss301.order.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "orders")   // "order" là từ khóa SQL -> đặt tên bảng khác
@Getter @Setter @NoArgsConstructor
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // MySQL AUTO_INCREMENT
    private Long id;

    private String customerId;
    private String productId;
    private int quantity;
    private double amount;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    private SagaStatus sagaStatus;

    private String failureReason;
}
```

```java
package com.mss301.order.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter @Setter @NoArgsConstructor
public class SagaLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;
    private String step;      // VD: RESERVE_INVENTORY, PROCESS_PAYMENT, COMPENSATION
    private String result;    // SUCCESS / FAILED
    @Column(length = 1000)
    private String message;
    private LocalDateTime time;

    public SagaLog(Long orderId, String step, String result, String message) {
        this.orderId = orderId;
        this.step = step;
        this.result = result;
        this.message = message;
        this.time = LocalDateTime.now();
    }
}
```

> `message` để `length = 1000` vì thông báo lỗi mạng (`I/O error on POST request…`) có thể dài hơn 255 ký tự mặc định của `VARCHAR`.

**5.4. Repository**

```java
package com.mss301.order.repository;

import com.mss301.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
```

```java
package com.mss301.order.repository;

import com.mss301.order.entity.SagaLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SagaLogRepository extends JpaRepository<SagaLog, Long> {
    List<SagaLog> findByOrderIdOrderByIdAsc(Long orderId);
}
```

**5.5. DTO**

```java
package com.mss301.order.dto;

public record CreateOrderRequest(String customerId, String productId, int quantity, double amount) {
}
```

```java
package com.mss301.order.dto;

public record ReserveRequest(Long orderId, String productId, int quantity) {
}
```

```java
package com.mss301.order.dto;

public record PaymentRequest(Long orderId, String customerId, double amount) {
}
```

---

### Step 6 – order-service: OrderService (TODO-6)

`service/OrderService.java`

```java
package com.mss301.order.service;

import com.mss301.order.dto.CreateOrderRequest;
import com.mss301.order.entity.*;
import com.mss301.order.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Mỗi method = 1 LOCAL TRANSACTION trên orderdb (MySQL)
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final SagaLogRepository sagaLogRepository;

    @Transactional
    public Order createPendingOrder(CreateOrderRequest req) {
        Order order = new Order();
        order.setCustomerId(req.customerId());
        order.setProductId(req.productId());
        order.setQuantity(req.quantity());
        order.setAmount(req.amount());
        order.setStatus(OrderStatus.PENDING);
        order.setSagaStatus(SagaStatus.PENDING);
        return orderRepository.save(order);
    }

    @Transactional
    public void updateSagaStatus(Long orderId, SagaStatus sagaStatus) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setSagaStatus(sagaStatus);
    }

    @Transactional
    public void markFailed(Long orderId, String reason) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setSagaStatus(SagaStatus.FAILED);
        order.setFailureReason(reason);
    }

    @Transactional
    public void confirmOrder(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setStatus(OrderStatus.CONFIRMED);
        order.setSagaStatus(SagaStatus.COMPLETED);
    }

    // Compensating transaction của bước 1 (Slide 26: cancelOrder)
    @Transactional
    public void cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setStatus(OrderStatus.CANCELLED);
        order.setSagaStatus(SagaStatus.COMPENSATED);
    }

    public void log(Long orderId, String step, String result, String message) {
        sagaLogRepository.save(new SagaLog(orderId, step, result, message));
    }

    public Order findById(Long id) {
        return orderRepository.findById(id).orElseThrow();
    }
}
```

> Mỗi method có `@Transactional` riêng nên **commit ngay** khi method kết thúc. Nhờ vậy trạng thái Saga (`EXECUTING`, `FAILED`…) được lưu xuống MySQL ngay cả khi các bước sau lỗi, và đó chính là "track saga execution state".

---

### Step 7 – order-service: RestClient và các Client (TODO-7)

**7.1. `config/RestClientConfig.java`**

```java
package com.mss301.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    // RestClient.Builder do Spring Boot 4 auto-configure (spring-boot-starter-restclient):
    // đã gắn sẵn Jackson 3 converter và timeout spring.http.clients.*.
    // Bean này là prototype -> mỗi lần inject nhận một builder mới, không ảnh hưởng lẫn nhau.
    @Bean
    public RestClient inventoryRestClient(RestClient.Builder builder,
                                          @Value("${services.inventory.url}") String url) {
        return builder.baseUrl(url).build();
    }

    @Bean
    public RestClient paymentRestClient(RestClient.Builder builder,
                                        @Value("${services.payment.url}") String url) {
        return builder.baseUrl(url).build();
    }
}
```

**7.2. `client/InventoryClient.java`**

```java
package com.mss301.order.client;

import com.mss301.order.dto.ReserveRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class InventoryClient {

    private final RestClient restClient;

    public InventoryClient(@Qualifier("inventoryRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public void reserve(Long orderId, String productId, int quantity) {
        restClient.post()
                .uri("/api/inventory/reserve")
                .body(new ReserveRequest(orderId, productId, quantity))
                .retrieve()          // 4xx/5xx -> ném RestClientResponseException
                .toBodilessEntity();
    }

    public void release(Long orderId) {
        restClient.post()
                .uri("/api/inventory/release/{orderId}", orderId)
                .retrieve()
                .toBodilessEntity();
    }
}
```

**7.3. `client/PaymentClient.java`**

```java
package com.mss301.order.client;

import com.mss301.order.dto.PaymentRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PaymentClient {

    private final RestClient restClient;

    public PaymentClient(@Qualifier("paymentRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public void pay(Long orderId, String customerId, double amount) {
        restClient.post()
                .uri("/api/payments/pay")
                .body(new PaymentRequest(orderId, customerId, amount))
                .retrieve()
                .toBodilessEntity();
    }

    public void refund(Long orderId) {
        restClient.post()
                .uri("/api/payments/refund/{orderId}", orderId)
                .retrieve()
                .toBodilessEntity();
    }
}
```

> **Các loại exception cần biết:**
>
> | Tình huống | Exception | Lớp cha |
> |---|---|---|
> | Service trả về 4xx (409, 402, 404…) | `HttpClientErrorException` | `RestClientResponseException` |
> | Service trả về 5xx | `HttpServerErrorException` | `RestClientResponseException` |
> | Service tắt / container bị stop / timeout | `ResourceAccessException` | `RestClientException` |
>
> Tất cả đều là `RestClientException`, nên orchestrator chỉ cần một khối `catch`.
>
> Dùng **OpenFeign** thay cho `RestClient` cũng được (Chapter 4, cần Spring Cloud bản tương thích Boot 4); khi đó bắt `FeignException`. Hoặc dùng **HTTP Interface** của Spring Framework 7 (xem EXT-6).

---

### Step 8 – order-service: Saga Orchestrator (TODO-8) ⭐

`saga/OrderSagaOrchestrator.java`

```java
package com.mss301.order.saga;

import com.mss301.order.client.InventoryClient;
import com.mss301.order.client.PaymentClient;
import com.mss301.order.dto.CreateOrderRequest;
import com.mss301.order.entity.*;
import com.mss301.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

// KHÔNG đặt @Transactional ở đây: orchestrator gọi qua mạng sang service khác,
// mỗi bước đã là 1 local transaction riêng trên database riêng.
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderSagaOrchestrator {

    private final OrderService orderService;
    private final InventoryClient inventoryClient;
    private final PaymentClient paymentClient;

    // Spring Framework 7: RetryTemplate có sẵn trong spring-core (không cần thư viện spring-retry).
    // maxRetries(2) = 1 lần chạy đầu + 2 lần thử lại = tối đa 3 lần, cách nhau 1 giây.
    private final RetryTemplate compensationRetry = new RetryTemplate(
            RetryPolicy.builder()
                    .includes(RestClientException.class)
                    .maxRetries(2)
                    .delay(Duration.ofSeconds(1))
                    .build());

    // Chỉ dùng cho demo KB8 (mặc định 0 = không dừng)
    @Value("${saga.demo.payment-delay-ms:0}")
    private long paymentDelayMs;

    public Order placeOrder(CreateOrderRequest req) {
        // ===== Bước 1: tạo order PENDING (local transaction ở orderdb) =====
        Order order = orderService.createPendingOrder(req);
        Long orderId = order.getId();
        orderService.log(orderId, "CREATE_ORDER", "SUCCESS", "Order PENDING");
        orderService.updateSagaStatus(orderId, SagaStatus.EXECUTING);

        // Ngăn xếp các compensating action của những bước ĐÃ thành công
        Deque<Runnable> compensations = new ArrayDeque<>();

        try {
            // ===== Bước 2: giữ hàng (inventory-service / MongoDB) =====
            inventoryClient.reserve(orderId, req.productId(), req.quantity());
            orderService.log(orderId, "RESERVE_INVENTORY", "SUCCESS", "Đã giữ " + req.quantity() + " " + req.productId());
            compensations.push(() -> inventoryClient.release(orderId));

            if (paymentDelayMs > 0) {
                log.warn("[DEMO] Chờ {} ms trước khi thanh toán order={}", paymentDelayMs, orderId);
                sleep(paymentDelayMs);
            }

            // ===== Bước 3: thanh toán (payment-service / SQL Server) =====
            paymentClient.pay(orderId, req.customerId(), req.amount());
            orderService.log(orderId, "PROCESS_PAYMENT", "SUCCESS", "Đã thu " + req.amount());
            compensations.push(() -> paymentClient.refund(orderId));

            // ===== Bước cuối: xác nhận order =====
            orderService.confirmOrder(orderId);
            orderService.log(orderId, "CONFIRM_ORDER", "SUCCESS", "Saga COMPLETED");

        } catch (RestClientException ex) {
            String reason = extractReason(ex);
            log.warn("Saga order={} thất bại: {}", orderId, reason);
            orderService.log(orderId, "SAGA", "FAILED", reason);
            orderService.markFailed(orderId, reason);
            compensate(orderId, compensations);
        }
        return orderService.findById(orderId);
    }

    // Chạy compensation theo thứ tự NGƯỢC (LIFO) rồi hủy order
    private void compensate(Long orderId, Deque<Runnable> compensations) {
        orderService.updateSagaStatus(orderId, SagaStatus.COMPENSATING);
        boolean allOk = true;
        while (!compensations.isEmpty()) {
            Runnable action = compensations.pop();
            allOk &= runWithRetry(orderId, action);
        }
        if (!allOk) {
            // Giữ nguyên COMPENSATING để admin/job khác xử lý tiếp, KHÔNG báo COMPENSATED giả
            log.error("Compensation cho order={} chưa xong, cần xử lý thủ công", orderId);
            return;
        }
        orderService.cancelOrder(orderId);          // compensation của bước 1
        orderService.log(orderId, "CANCEL_ORDER", "SUCCESS", "Saga COMPENSATED");
    }

    // Compensation phải "Reliable" -> thử lại tối đa 3 lần (Slide 25)
    private boolean runWithRetry(Long orderId, Runnable action) {
        AtomicInteger attempt = new AtomicInteger();
        try {
            compensationRetry.execute(() -> {
                int n = attempt.incrementAndGet();
                try {
                    action.run();
                } catch (RestClientException ex) {
                    orderService.log(orderId, "COMPENSATION", "FAILED", "Lần thử " + n + ": " + ex.getMessage());
                    throw ex;                       // ném lại để RetryTemplate thử lần tiếp theo
                }
                orderService.log(orderId, "COMPENSATION", "SUCCESS", "Lần thử " + n);
                return null;
            });
            return true;
        } catch (RetryException ex) {               // đã hết số lần thử
            return false;
        }
    }

    private String extractReason(RestClientException ex) {
        if (ex instanceof RestClientResponseException r) {
            try {
                Map<?, ?> body = r.getResponseBodyAs(Map.class);
                if (body != null && body.get("message") != null) {
                    return r.getStatusCode().value() + " - " + body.get("message");
                }
            } catch (Exception ignored) { }
            return r.getStatusCode().toString();
        }
        return "Service không phản hồi: " + ex.getMessage();   // VD: container bị stop
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
```

**Giải thích các điểm mấu chốt:**

1. **Không có `@Transactional` ở orchestrator.** Nếu bọc cả hàm trong một transaction, khi Payment lỗi, Spring chỉ rollback được `orderdb` (MySQL). Kho trong MongoDB vẫn bị trừ, vì `@Transactional` không vượt qua được lời gọi HTTP (Slide 8), càng không thể trải qua 3 hệ quản trị CSDL khác nhau. Hơn nữa, giữ connection DB trong lúc chờ mạng sẽ làm giảm hiệu năng.
2. **Stack `compensations` (LIFO).** Chỉ những bước **đã thành công** mới được đẩy compensation vào stack, rồi được hoàn tác theo thứ tự ngược lại. Ví dụ Payment lỗi thì stack chỉ có `release`, nên không gọi `refund` vô ích.
3. **Bước 1 hoàn tác cuối cùng** bằng `cancelOrder`, đúng ví dụ ở Slide 26.
4. **Retry bằng `RetryTemplate`** (Spring Framework 7) cho compensation, đáp ứng yêu cầu *Reliable*. `includes(RestClientException.class)` nghĩa là chỉ thử lại khi lỗi gọi HTTP. Nếu hết lượt vẫn lỗi, `execute` ném `RetryException` và Saga **dừng ở `COMPENSATING`** để người vận hành biết cần xử lý, thay vì báo `COMPENSATED` sai sự thật.
5. **Idempotent** được đảm bảo ở phía inventory/payment, nên việc retry `release` hay `refund` nhiều lần vẫn an toàn.

> Cách khác: đặt `@Retryable(includes = RestClientException.class, maxRetries = 2, delay = 1000)` lên method `release`/`refund` của client và bật `@EnableResilientMethods` trên một class `@Configuration`. Demo dùng `RetryTemplate` để ghi được log từng lần thử vào `SagaLog`.

---

### Step 9 – order-service: Controller (TODO-9)

```java
package com.mss301.order.controller;

import com.mss301.order.dto.CreateOrderRequest;
import com.mss301.order.entity.*;
import com.mss301.order.repository.*;
import com.mss301.order.saga.OrderSagaOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderSagaOrchestrator orchestrator;
    private final OrderRepository orderRepository;
    private final SagaLogRepository sagaLogRepository;

    @PostMapping
    public Order placeOrder(@RequestBody CreateOrderRequest req) {
        return orchestrator.placeOrder(req);
    }

    @GetMapping
    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    @GetMapping("/{id}/saga-logs")
    public List<SagaLog> sagaLogs(@PathVariable Long id) {
        return sagaLogRepository.findByOrderIdOrderByIdAsc(id);
    }
}
```

```java
package com.mss301.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
```

---

### Step 10 – Docker hoá 3 service (TODO-10)

**10.1. `Dockerfile`** – đặt trong **mỗi** thư mục service (nội dung giống hệt nhau)

```dockerfile
# ---------- Stage 1: build bằng Maven ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# ---------- Stage 2: image chạy, chỉ có JRE ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

`.dockerignore` (cùng thư mục):

```
target/
.idea/
*.iml
```

**10.2. Bổ sung 3 service vào `docker-compose.yml`** (thêm vào dưới mục `services:`, trước `volumes:`)

```yaml
  inventory-service:
    build: ./inventory-service
    container_name: inventory-service
    ports:
      - "8082:8082"
    environment:
      MONGO_URI: mongodb://mongodb:27017/inventorydb?directConnection=true
    depends_on:
      mongodb:
        condition: service_healthy

  payment-service:
    build: ./payment-service
    container_name: payment-service
    ports:
      - "8083:8083"
    environment:
      DB_URL: jdbc:sqlserver://sqlserver:1433;databaseName=paymentdb;encrypt=true;trustServerCertificate=true
      DB_USER: sa
      DB_PASSWORD: Saga@12345
    depends_on:
      sqlserver:
        condition: service_healthy

  order-service:
    build: ./order-service
    container_name: order-service
    ports:
      - "8081:8081"
    environment:
      DB_URL: jdbc:mysql://mysql:3306/orderdb?createDatabaseIfNotExist=true&allowPublicKeyRetrieval=true&useSSL=false
      DB_USER: root
      DB_PASSWORD: root
      INVENTORY_URL: http://inventory-service:8082
      PAYMENT_URL: http://payment-service:8083
      PAYMENT_DELAY_MS: ${PAYMENT_DELAY_MS:-0}      # chỉ dùng cho KB8
    depends_on:
      mysql:
        condition: service_healthy
      inventory-service:
        condition: service_started
      payment-service:
        condition: service_started
```

> Chú ý: **bên trong** Docker, cổng của DB là cổng container (`mysql:3306`, `sqlserver:1433`), không phải cổng đã map ra host (`3307`, `14330`).

---

## 6. Chạy và kiểm thử (Postman)

### 6.1. Khởi động – chọn 1 trong 2 cách

**Cách A – DB chạy Docker, service chạy IntelliJ** (tiện debug, đặt breakpoint):

```bash
cd saga-demo
docker compose up -d mysql sqlserver mongodb
docker compose ps        # chờ 3 container (healthy)
```

Sau đó Run lần lượt **inventory-service → payment-service → order-service** trong IntelliJ (hoặc `mvnw spring-boot:run` trong từng thư mục). Các property dùng giá trị mặc định `localhost:3307 / 14330 / 27017`.

**Cách B – Tất cả chạy Docker:**

```bash
cd saga-demo
docker compose up -d --build
docker compose ps
docker compose logs -f order-service      # xem log Saga (Ctrl + C để thoát)
```

**Reset dữ liệu về ban đầu** (xoá luôn volume của 3 DB):

```bash
docker compose down -v
docker compose up -d --build              # hoặc chỉ 3 DB nếu dùng cách A
```

> Khác với H2 in-memory, dữ liệu giờ nằm trong Docker volume nên **không mất khi restart service**. Muốn chạy lại các kịch bản với số liệu "đẹp" như mục 6.3, hãy reset trước. Collection Postman (mục 6.4) so sánh **độ chênh lệch** trước/sau nên vẫn chạy đúng khi không reset.

### 6.2. Xem dữ liệu trực tiếp trong từng database

| Service | Công cụ GUI | Kết nối từ máy host |
|---|---|---|
| order (MySQL) | MySQL Workbench / DBeaver | `localhost:3307`, user `root` / `root`, DB `orderdb` |
| payment (SQL Server) | SSMS / Azure Data Studio | `localhost,14330`, user `sa` / `Saga@12345`, tick *Trust server certificate* |
| inventory (MongoDB) | MongoDB Compass | `mongodb://localhost:27017/?directConnection=true` |

Hoặc dùng lệnh:

```bash
# MySQL – order
docker exec -it saga-mysql mysql -uroot -proot orderdb -e "SELECT id, status, saga_status, failure_reason FROM orders;"

# SQL Server – payment
docker exec -it saga-sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "Saga@12345" -C -d paymentdb -Q "SELECT * FROM wallet; SELECT * FROM payment;"

# MongoDB – inventory
docker exec -it saga-mongodb mongosh inventorydb --quiet --eval "db.product.find(); db.reservation.find()"
```

### 6.3. Các kịch bản và kết quả mong đợi

Tóm tắt (số liệu tính từ **dữ liệu vừa reset**, chạy theo đúng thứ tự):

| KB | Request `POST /api/orders` | Lỗi ở bước | `status` / `sagaStatus` | `failureReason` | Kho / ví sau KB |
|---|---|---|---|---|---|
| KB1 | `C1, P1, 2, 2000` | – | `CONFIRMED / COMPLETED` | `null` | P1 = 8, C1 = 3000 |
| KB2 | `C2, P1, 1, 1000` | Payment (402) | `CANCELLED / COMPENSATED` | `402 - Số dư không đủ…` | P1 = 8 (trừ rồi trả), C2 = 100 |
| KB3 | `C1, P2, 100, 2000` | Inventory (409) | `CANCELLED / COMPENSATED` | `409 - Không đủ hàng…` | P2 = 5, C1 = 3000 |
| KB4 | `C1, P9, 1, 10` | Inventory (404) | `CANCELLED / COMPENSATED` | `404 - Không tìm thấy sản phẩm P9` | không đổi |
| KB5 | `C9, P2, 1, 20` | Payment (404) | `CANCELLED / COMPENSATED` | `404 - Không tìm thấy ví của C9` | P2 = 5 (trừ rồi trả) |
| KB6 | `C1, P2, 1, 20` (payment tắt) | Network | `CANCELLED / COMPENSATED` | `Service không phản hồi: I/O error…` | P2 = 5 (trừ rồi trả) |
| KB7 | Gọi lặp lại reserve/pay/release/refund | – | – | – | **không đổi** (idempotent) |
| KB8 | `C1, P2, 1, 20` (tắt cả payment + inventory) | Network + compensation lỗi | `PENDING / COMPENSATING` | `Service không phản hồi…` | P2 = 4 (**treo**, cần khôi phục) |

#### KB1 – Happy path

```json
{ "id": 1, "customerId": "C1", "productId": "P1", "quantity": 2, "amount": 2000.0,
  "status": "CONFIRMED", "sagaStatus": "COMPLETED", "failureReason": null }
```

Saga logs: `CREATE_ORDER → RESERVE_INVENTORY → PROCESS_PAYMENT → CONFIRM_ORDER`.

#### KB2 – Thanh toán thất bại → compensate Inventory

```json
{ "id": 2, "status": "CANCELLED", "sagaStatus": "COMPENSATED",
  "failureReason": "402 - Số dư không đủ: còn 100.0, cần 1000.0", ... }
```

- Tồn kho: `P1` bị trừ còn 7, rồi được **release** về lại **8**. Ví `C2` vẫn **100**. Reservation của order 2 có `status = RELEASED`.
- `GET /api/orders/2/saga-logs` (Jackson 3 trả `time` dạng chuỗi ISO, ví dụ `"2026-10-16T11:30:12.345"`):

| step | result | message |
|---|---|---|
| CREATE_ORDER | SUCCESS | Order PENDING |
| RESERVE_INVENTORY | SUCCESS | Đã giữ 1 P1 |
| SAGA | FAILED | 402 - Số dư không đủ… |
| COMPENSATION | SUCCESS | Lần thử 1 |
| CANCEL_ORDER | SUCCESS | Saga COMPENSATED |

- Log của inventory-service (`docker compose logs inventory-service`): `RESERVE order=2 ...`, sau đó `RELEASE order=2 -> trả lại 1 P1, stock = 8`.

#### KB3 – Hết hàng → lỗi ngay bước 2

- `failureReason = "409 - Không đủ hàng: còn 5, cần 100"`.
- Stack compensation **rỗng**, nên không gọi `release` mà chỉ `cancelOrder`. Saga logs: `CREATE_ORDER → SAGA → CANCEL_ORDER`.

#### KB4 – Sản phẩm không tồn tại

- Giống KB3 nhưng lỗi 404 từ inventory. Saga logs: `CREATE_ORDER → SAGA → CANCEL_ORDER`.

#### KB5 – Ví không tồn tại

- Reserve `P2` thành công, pay trả **404** → `release` → `cancelOrder`. Saga logs giống KB2.

#### KB6 – payment-service bị tắt (network failure)

1. Tắt payment-service: `docker compose stop payment-service` (cách A: Stop trong IntelliJ).
2. Gửi request KB6.
3. Kết quả: `failureReason` bắt đầu bằng `"Service không phản hồi: I/O error on POST request..."`, order `CANCELLED / COMPENSATED`, `P2` trở về **5**.
4. Bật lại: `docker compose start payment-service`. Ví **giữ nguyên** số dư vì dữ liệu nằm trong SQL Server volume.

#### KB7 – Idempotent

- Gọi `POST /api/inventory/release/2` thêm 1–2 lần: response vẫn `RELEASED`, `P1` **vẫn là 8**, log in `không có gì để hoàn tác`.
- Gọi lại `POST /api/inventory/reserve` với `{"orderId":1,"productId":"P1","quantity":2}`: trả về reservation cũ, tồn kho **không bị trừ lần 2**.
- Gọi lại `POST /api/payments/pay` với `{"orderId":1,"customerId":"C1","amount":2000}`: trả về payment cũ, ví **không bị trừ lần 2**.
- Gọi `POST /api/payments/refund/2` (order 2 chưa từng thanh toán): không lỗi, không cộng tiền.

#### KB8 (tuỳ chọn) – Compensation cũng lỗi

1. Khởi động lại order-service với độ trễ 15 giây trước bước thanh toán:
   - PowerShell: `$env:PAYMENT_DELAY_MS=15000; docker compose up -d order-service`
   - Git Bash: `PAYMENT_DELAY_MS=15000 docker compose up -d order-service`
   - Cách A (IntelliJ): thêm biến môi trường `PAYMENT_DELAY_MS=15000` vào Run Configuration, hoặc đặt breakpoint trước `paymentClient.pay(...)`.
2. Gửi request KB8 từ Postman (request sẽ "treo" khoảng 15 giây).
3. **Trong 15 giây đó**, chạy: `docker compose stop payment-service inventory-service`.
4. Kết quả: order giữ `status = PENDING`, `sagaStatus = COMPENSATING`; saga-logs có **3 dòng `COMPENSATION FAILED`** và không có `CANCEL_ORDER`.
5. Bật lại: `docker compose start inventory-service payment-service`, rồi khởi động lại order-service không có độ trễ: `$env:PAYMENT_DELAY_MS=0; docker compose up -d order-service`.
6. Quan sát: `P2 = 4` vẫn bị giữ vì `release` chưa chạy được. Đây là lý do cần cơ chế khôi phục Saga (EXT-4). Có thể gọi tay `POST /api/inventory/release/{id}` (an toàn vì idempotent).

### 6.4. Kiểm thử bằng Postman

#### a) Import collection

1. Mở Postman → **Import** → chọn file **`Saga_Postman_Collection.json`** (đặt cùng thư mục với tài liệu này).
2. Collection **"MSS301 - Saga Demo (Spring Boot 4.1.1)"** có sẵn **Collection Variables**:

| Biến | Giá trị mặc định | Ý nghĩa |
|---|---|---|
| `orderUrl` | `http://localhost:8081` | order-service |
| `inventoryUrl` | `http://localhost:8082` | inventory-service |
| `paymentUrl` | `http://localhost:8083` | payment-service |
| `kb1OrderId`, `kb2OrderId`… | (tự sinh) | id order của từng kịch bản, được lưu bởi test script |
| `stock_P1`, `bal_C1`… | (tự sinh) | tồn kho / số dư lúc Snapshot |

Cấu trúc collection:

```
MSS301 - Saga Demo (Spring Boot 4.1.1)
├── 0 - Kiểm tra service          (3 GET: products, wallets, orders)
├── KB1 - Happy path
│   ├── [KB1] 1. Snapshot kho & ví
│   ├── [KB1] 2. Đặt hàng
│   ├── [KB1] 3. Kiểm tra kho & ví
│   └── [KB1] 4. Saga logs
├── KB2 - Thanh toán thất bại (402)        (4 request như trên + kiểm tra reservation RELEASED)
├── KB3 - Hết hàng (409)
├── KB4 - Sản phẩm không tồn tại (404 inventory)
├── KB5 - Ví không tồn tại (404 payment)
├── KB7 - Idempotent (gọi lặp lại)         (release ×2, reserve lại, pay lại, refund order chưa trả)
└── Thủ công (không chạy bằng Runner)
    ├── KB6 - payment-service bị tắt
    └── KB8 - Compensation cũng lỗi
```

Mỗi kịch bản đi theo mẫu **Snapshot → Hành động → Kiểm tra → Saga logs**: test script lưu tồn kho và số dư trước, rồi so sánh **độ chênh lệch** sau khi chạy Saga. Nhờ vậy collection chạy đúng cả khi database không được reset.

#### b) Các test script chính

**Snapshot** (tab *Scripts → Post-response* của `GET {{inventoryUrl}}/api/inventory/products`):

```javascript
pm.test("HTTP 200", () => pm.response.to.have.status(200));
// Lưu tồn kho hiện tại: stock_P1, stock_P2
pm.response.json().forEach(p => pm.collectionVariables.set("stock_" + p.id, p.stock));
// Lưu số dư ví hiện tại: bal_C1, bal_C2
pm.sendRequest(pm.collectionVariables.get("paymentUrl") + "/api/payments/wallets", (err, res) => {
    pm.test("Đọc được số dư ví", () => pm.expect(err).to.be.null);
    res.json().forEach(w => pm.collectionVariables.set("bal_" + w.customerId, w.balance));
});
```

**Đặt hàng – KB1** (`POST {{orderUrl}}/api/orders`, body `{"customerId":"C1","productId":"P1","quantity":2,"amount":2000}`):

```javascript
pm.test("HTTP 200", () => pm.response.to.have.status(200));
const o = pm.response.json();
pm.collectionVariables.set("kb1OrderId", o.id);
pm.test("Order status = CONFIRMED", () => pm.expect(o.status).to.eql("CONFIRMED"));
pm.test("Saga status = COMPLETED", () => pm.expect(o.sagaStatus).to.eql("COMPLETED"));
pm.test("Không có failureReason", () => pm.expect(o.failureReason).to.be.null);
```

Với các kịch bản lỗi, chỉ thay giá trị mong đợi, ví dụ KB2:

```javascript
pm.test("Order status = CANCELLED", () => pm.expect(o.status).to.eql("CANCELLED"));
pm.test("Saga status = COMPENSATED", () => pm.expect(o.sagaStatus).to.eql("COMPENSATED"));
pm.test("failureReason bắt đầu bằng 402", () => pm.expect(o.failureReason).to.match(/^402/));
```

**Kiểm tra kho & ví – KB1** (`GET {{inventoryUrl}}/api/inventory/products`):

```javascript
// Độ chênh lệch mong đợi so với lúc Snapshot
const expectStock = { "P1": -2 };
const expectBal = { "C1": -2000 };
pm.test("HTTP 200", () => pm.response.to.have.status(200));
pm.response.json().forEach(p => {
    if (p.id in expectStock) {
        const before = Number(pm.collectionVariables.get("stock_" + p.id));
        pm.test(`Tồn kho ${p.id}: ${before} -> ${before + expectStock[p.id]}`,
            () => pm.expect(p.stock).to.eql(before + expectStock[p.id]));
    }
});
pm.sendRequest(pm.collectionVariables.get("paymentUrl") + "/api/payments/wallets", (err, res) => {
    res.json().forEach(w => {
        if (w.customerId in expectBal) {
            const before = Number(pm.collectionVariables.get("bal_" + w.customerId));
            pm.test(`Số dư ${w.customerId}: ${before} -> ${before + expectBal[w.customerId]}`,
                () => pm.expect(w.balance).to.eql(before + expectBal[w.customerId]));
        }
    });
});
```

**Saga logs – KB2** (`GET {{orderUrl}}/api/orders/{{kb2OrderId}}/saga-logs`):

```javascript
pm.test("HTTP 200", () => pm.response.to.have.status(200));
const steps = pm.response.json().map(l => l.step);
pm.test("Thứ tự bước Saga đúng", () => pm.expect(steps).to.eql(
    ["CREATE_ORDER", "RESERVE_INVENTORY", "SAGA", "COMPENSATION", "CANCEL_ORDER"]));
```

#### c) Bảng tổng hợp test của từng kịch bản

| KB | Kiểm tra trên response đặt hàng | Kho / ví (chênh lệch) | Thứ tự `step` trong saga-logs |
|---|---|---|---|
| KB1 | `CONFIRMED`, `COMPLETED`, `failureReason = null` | P1 −2, C1 −2000 | CREATE_ORDER, RESERVE_INVENTORY, PROCESS_PAYMENT, CONFIRM_ORDER |
| KB2 | `CANCELLED`, `COMPENSATED`, reason `^402` | P1 0, C2 0; reservation = `RELEASED` | CREATE_ORDER, RESERVE_INVENTORY, SAGA, COMPENSATION, CANCEL_ORDER |
| KB3 | `CANCELLED`, `COMPENSATED`, reason `^409` | P2 0, C1 0 | CREATE_ORDER, SAGA, CANCEL_ORDER |
| KB4 | `CANCELLED`, `COMPENSATED`, reason `^404` | P1 0, P2 0, C1 0 | CREATE_ORDER, SAGA, CANCEL_ORDER |
| KB5 | `CANCELLED`, `COMPENSATED`, reason `^404` | P2 0 | CREATE_ORDER, RESERVE_INVENTORY, SAGA, COMPENSATION, CANCEL_ORDER |
| KB6 | `CANCELLED`, `COMPENSATED`, reason `^Service không phản hồi` | P2 0 | giống KB2 |
| KB7 | release → `"RELEASED"`; reserve lại → `RESERVED`; pay lại → `PAID` | tất cả 0 | – |
| KB8 | `PENDING`, `COMPENSATING`, reason `^Service không phản hồi` | (P2 bị giữ 1) | có 3 dòng `COMPENSATION FAILED`, không có `CANCEL_ORDER` |

#### d) Chạy tự động bằng Collection Runner

1. Click chuột phải vào collection → **Run collection**.
2. **Bỏ chọn** thư mục *"Thủ công (không chạy bằng Runner)"*.
3. Giữ nguyên thứ tự: `0 → KB1 → KB2 → KB3 → KB4 → KB5 → KB7` (KB7 dùng `kb1OrderId`, `kb2OrderId` do KB1, KB2 sinh ra).
4. Bấm **Run** → tất cả test phải **Passed**. Chụp màn hình kết quả để nộp (TODO-11).

Chạy bằng dòng lệnh (tuỳ chọn, dùng Postman CLI hoặc Newman):

```bash
npm install -g newman
newman run Saga_Postman_Collection.json --folder "0 - Kiểm tra service" --folder "KB1 - Happy path" \
  --folder "KB2 - Thanh toán thất bại (402)" --folder "KB3 - Hết hàng (409)" \
  --folder "KB4 - Sản phẩm không tồn tại (404 inventory)" --folder "KB5 - Ví không tồn tại (404 payment)" \
  --folder "KB7 - Idempotent (gọi lặp lại)"
```

#### e) Chạy KB6 và KB8 thủ công

- **KB6:** chạy `[KB6] 1. Snapshot` → `docker compose stop payment-service` → `[KB6] 2. Đặt hàng` → `docker compose start payment-service` → `[KB6] 3. Kiểm tra` → `[KB6] 4. Saga logs`.
- **KB8:** làm theo các bước ở mục 6.3, gửi `[KB8] 2. Đặt hàng` rồi tắt payment + inventory trong 15 giây; sau khi có response, bật lại các service và gửi `[KB8] 4. Saga logs`.

---

## 7. Đối chiếu code với lý thuyết

| Lý thuyết (Slide) | Thể hiện trong code |
|---|---|
| Database per service | order → MySQL, inventory → MongoDB, payment → SQL Server; mỗi DB một container riêng |
| Chuỗi local transactions (20, 22) | `createPendingOrder` (MySQL), `reserve` (MongoDB transaction), `pay` (SQL Server), `confirmOrder` – mỗi cái `@Transactional` trên DB riêng |
| Orchestration (24) | `OrderSagaOrchestrator.placeOrder()` gửi lệnh tuần tự và quyết định bước tiếp theo |
| Compensating transaction (25, 26) | `release`, `refund`, `cancelOrder` |
| Idempotent | Kiểm tra `Reservation` / `Payment` đã tồn tại hoặc đã `RELEASED` / `REFUNDED` |
| Reversible | Lưu `quantity`, `amount` để hoàn tác chính xác |
| Reliable | `RetryTemplate` tối đa 3 lần; lỗi thì dừng ở `COMPENSATING` |
| Track state, logging (25, 27) | `Order.sagaStatus` + bảng `saga_log` + `log.info` |
| Handle failures (5) | `catch (RestClientException)` cho cả lỗi nghiệp vụ (4xx) và lỗi mạng (container bị stop) |
| Eventual consistency (6, 21) | Trong lúc Saga chạy, kho có thể đã trừ mà đơn vẫn `PENDING`; cuối cùng mọi thứ nhất quán |
| Không dùng 2PC (15) | Không lock tài nguyên xuyên service, không có coordinator chặn (blocking); MongoDB cũng không tham gia XA được |

**So sánh nhanh với Choreography (Slide 23):**

| | Orchestration (demo này) | Choreography |
|---|---|---|
| Điều phối | 1 orchestrator trung tâm | Các service tự phản ứng theo event |
| Giao tiếp | Command (REST) | Event (Kafka/RabbitMQ) |
| Theo dõi luồng | Dễ (xem `SagaLog`) | Khó, dễ thành "event spaghetti" |
| Coupling | Orchestrator biết mọi service | Lỏng hơn |

---

## 8. Bài tập mở rộng

**EXT-1: Thêm bước Shipping (để thấy `refund` được gọi)**
- Tạo `shipping-service` (8084) với database tuỳ chọn (ví dụ PostgreSQL trong Docker), `POST /api/shipping/create` (lỗi nếu `quantity > 3`) và `POST /api/shipping/cancel/{orderId}`.
- Thêm bước 4 vào orchestrator, sau `pay`. Khi shipping lỗi, phải thấy thứ tự compensation là **refund → release → cancelOrder**. Viết thêm 1 thư mục Postman kiểm tra C1 không bị mất tiền.

**EXT-2: Timeout**
- Chỉnh `spring.http.clients.read-timeout=2s` (hoặc riêng cho từng client), giả lập payment chậm bằng `Thread.sleep(5000)` trong `pay`.
- Thảo luận: nếu timeout xảy ra **sau khi** payment đã trừ tiền thì sao? Gợi ý: nhờ `refund` idempotent, có thể luôn đẩy `refund` vào stack **trước** khi gọi `pay`.

**EXT-3: Choreography với Kafka (Chapter 3/4)**
- Thêm Kafka vào `docker-compose.yml`. order-service publish `OrderCreatedEvent`; inventory nghe và publish `InventoryReservedEvent` hoặc `InventoryFailedEvent`; payment nghe và publish `PaymentCompletedEvent` hoặc `PaymentFailedEvent`; order và inventory nghe các event `*Failed` để tự compensate.
- So sánh độ khó debug với bản Orchestration.

**EXT-4: Khôi phục Saga bị treo (dùng cho KB8)**
- Bật `@EnableScheduling`, viết `@Scheduled(fixedDelay = 60000)` job quét các order có `sagaStatus = COMPENSATING` hoặc `EXECUTING` quá 1 phút, rồi gọi lại `release` + `refund` + `cancelOrder` (an toàn vì idempotent).
- Hoặc thêm API `POST /api/orders/{id}/compensate` để admin bấm khôi phục, kèm request Postman kiểm tra `P2` được trả lại.

**EXT-5: Outbox Pattern (Slide 12)**
- Thay vì gọi REST trực tiếp, ghi command vào bảng `outbox` trong **cùng transaction** với order (MySQL), rồi một job đọc outbox và gửi đi, để không mất message khi service crash giữa chừng.

**EXT-6: HTTP Interface của Spring Framework 7 thay cho RestClient viết tay**
- Khai báo interface:

  ```java
  @HttpExchange("/api/inventory")
  public interface InventoryApi {
      @PostExchange("/reserve")
      void reserve(@RequestBody ReserveRequest req);

      @PostExchange("/release/{orderId}")
      void release(@PathVariable Long orderId);
  }
  ```

- Trên `OrderServiceApplication` thêm `@ImportHttpServices(group = "inventory", types = InventoryApi.class)` và trong `application.properties`: `spring.http.serviceclient.inventory.base-url=${INVENTORY_URL:http://localhost:8082}`. Làm tương tự cho `PaymentApi`. Exception ném ra vẫn là `RestClientException`, nên orchestrator không cần đổi.

---

## 9. Lỗi thường gặp

| Lỗi | Nguyên nhân | Cách sửa |
|---|---|---|
| `Cannot resolve spring-boot-starter-web` / cảnh báo deprecated | Spring Boot 4 đổi tên starter | Dùng `spring-boot-starter-webmvc` |
| `No qualifying bean of type 'RestClient.Builder'` | Thiếu module RestClient của Boot 4 | Thêm `spring-boot-starter-restclient` |
| `failureReason` chỉ có `409 CONFLICT`, không có message | Vẫn dùng property cũ `server.error.include-message` | Đổi thành `spring.web.error.include-message=always` |
| inventory kết nối `localhost:27017` dù đã khai báo URI | Dùng property cũ `spring.data.mongodb.uri` | Đổi thành `spring.mongodb.uri` |
| `Transaction numbers are only allowed on a replica set member or mongos` | MongoDB chạy standalone | Chạy với `--replSet rs0` và `rs.initiate(...)` như Step 2 |
| `No qualifying bean of type 'TransactionManager'` ở inventory | Chưa khai báo `MongoTransactionManager` | Thêm `MongoConfig` (Step 3.4) |
| Kho không thay đổi sau khi reserve | Quên `productRepository.save(product)` – MongoDB không có dirty checking | Gọi `save` sau khi sửa |
| `PKIX path building failed` (SQL Server) | Driver từ chối chứng chỉ tự ký | Thêm `encrypt=true;trustServerCertificate=true` vào JDBC URL |
| `Cannot open database "paymentdb"` | DB chưa được tạo (container chưa healthy) | Chờ `docker compose ps` báo `healthy`; healthcheck sẽ tạo DB |
| Container SQL Server tự tắt ngay | Mật khẩu `sa` quá yếu, hoặc Docker thiếu RAM | Dùng mật khẩu mạnh, cấp ≥ 4 GB RAM cho Docker |
| `Public Key Retrieval is not allowed` (MySQL) | MySQL 8.4 + kết nối không SSL | Thêm `allowPublicKeyRetrieval=true&useSSL=false` |
| `Invalid object name 'wallet'` / ví rỗng | `data.sql` không chạy với DB không nhúng | `spring.sql.init.mode=always` + `spring.jpa.defer-datasource-initialization=true` |
| `Violation of PRIMARY KEY` khi restart payment | `data.sql` chèn lại dữ liệu đã có | Viết `IF NOT EXISTS ... INSERT` như Step 4.2 |
| `Connection refused` khi order-service chạy trong Docker | Dùng `localhost` bên trong container | Dùng tên service: `mysql:3306`, `inventory-service:8082`… |
| `Bind for 0.0.0.0:3307 failed: port is already allocated` | Cổng host đã bị chiếm | Đổi cổng bên trái trong `ports` và JDBC URL tương ứng |
| `Data truncation: Data too long for column 'message'` | Thông báo lỗi mạng dài hơn 255 ký tự | `@Column(length = 1000)` cho `SagaLog.message` |
| `Syntax error ... ORDER` | Bảng tên `order` trùng từ khóa SQL | `@Table(name = "orders")` |
| `NoUniqueBeanDefinitionException: RestClient` | Có 2 bean `RestClient` | Dùng `@Qualifier` trong constructor của client (không dùng `@RequiredArgsConstructor` cho class này) |
| Kho không được cộng lại khi Payment lỗi | Quên push compensation, hoặc đặt `@Transactional` ở orchestrator và tưởng rằng nó sẽ rollback | Xem lại Step 8 |
| Getter/setter "cannot find symbol" | Lombok chưa được chạy làm annotation processor | Khai báo Lombok trong `annotationProcessorPaths` (Step 1); IntelliJ: bật *Annotation Processing* |
| Postman: `Could not send request` tới `localhost` | Dùng Postman web | Dùng Postman Desktop (hoặc Postman Agent) |
| Postman KB7 lỗi `kb1OrderId` rỗng | Chạy KB7 trước KB1/KB2 | Chạy Runner theo đúng thứ tự |

---

## 10. Câu hỏi ôn tập

1. Nếu đặt `@Transactional` lên `placeOrder()` của orchestrator thì khi Payment lỗi, dữ liệu ở `inventorydb` (MongoDB) có được rollback không? Vì sao?
2. Vì sao compensation phải chạy theo thứ tự **ngược** với thứ tự thực hiện?
3. Trong KB3, vì sao không cần gọi `release`?
4. Điều gì xảy ra nếu `release` **không** idempotent và orchestrator retry 3 lần?
5. Saga đảm bảo những thuộc tính nào của ACID và đánh đổi thuộc tính nào? (Gợi ý: Saga **không** có Isolation, vì service khác có thể đọc tồn kho "tạm thời bị trừ".)
6. So sánh Saga với 2PC về: blocking, hiệu năng, single point of failure, mức độ nhất quán. Vì sao 2PC gần như không áp dụng được với tổ hợp MySQL + SQL Server + MongoDB?
7. Khi nào nên chọn Choreography thay vì Orchestration?
8. Trạng thái `COMPENSATING` kéo dài mãi (KB8) có nghĩa là gì? Nêu cách xử lý.
9. Vì sao `reserve` trong MongoDB cần replica set và `MongoTransactionManager`? Nếu bỏ đi thì bước `reserve` còn là local transaction ACID không?
10. Vì sao test Postman so sánh **độ chênh lệch** thay vì giá trị tuyệt đối của tồn kho và số dư?

---

> **Tóm tắt một câu:** *Saga = chuỗi local transaction (mỗi service một database riêng) + compensating transaction theo thứ tự ngược + theo dõi trạng thái, đổi tính nhất quán tức thì (ACID) lấy tính sẵn sàng và khả năng mở rộng (eventual consistency).*
