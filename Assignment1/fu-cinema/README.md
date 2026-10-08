# FUCinemaBookingSystem (Assignment 01 - MSS301)

Cinema ticket booking built with 3 microservices + 1 API Gateway. Every service owns a different database.

| Service | Port | Database | Notes |
|---|---|---|---|
| customer-service | 8081 | SQL Server 2022 `cinema_customer` | Flyway (T-SQL), BCrypt, issues JWT HS256 |
| movie-service | 8082 | MongoDB 7 `cinema_movie` | `DataSeeder`, `MongoTemplate` + `Criteria` |
| booking-service | 8083 | MySQL 8 `cinema_booking` | Flyway, OpenFeign -> movie-service |
| api-gateway | 9000 | - | JWT check, role rules, `X-User-*` headers |

## Host ports of the databases

The Docker containers are published on non-default host ports because this machine already runs local
`MSSQLSERVER` (1433/1434) and `MySQL80` (3306) services:

| Container | Host port | Container port |
|---|---|---|
| `cinema-sqlserver` | **14330** | 1433 |
| `cinema-mongo` | 27017 | 27017 |
| `cinema-mysql` | **3307** | 3306 |

`customer-service` and `booking-service` `application.properties` already point to these ports.

## Start order

```bash
cd Assignment1/fu-cinema
docker compose up -d                       # wait until cinema-sqlserver is healthy
(cd customer-service && ./mvnw spring-boot:run)   # :8081
(cd movie-service    && ./mvnw spring-boot:run)   # :8082 (seeds MongoDB on first start)
(cd booking-service  && ./mvnw spring-boot:run)   # :8083
(cd api-gateway      && ./mvnw spring-boot:run)   # :9000
```

Always call the API through the gateway: `http://localhost:9000`.

## Test accounts

| Role | Email | Password | Note |
|---|---|---|---|
| Admin | `admin@fucinema.com` | `@@abc123@@` | stored in `customer-service` properties |
| Customer | `an@gmail.com` | `123456` | id 1, ACTIVE |
| Customer | `binh@gmail.com` | `123456` | id 2, ACTIVE |
| Customer | `chi@gmail.com` | `123456` | id 3, INACTIVE (login returns 403) |

## Postman / Newman

Files are in `postman/`:

- `FUCinemaBookingSystem.postman_collection.json` - 8 folders, 85 requests with test scripts
- `FUCinema-Local.postman_environment.json` - environment `FUCinema-Local`

Import both into Postman, select the `FUCinema-Local` environment, then run the collection with the Collection Runner
(folders 01 -> 08 in this order). Command line alternative:

```bash
cd postman
npx --yes newman run FUCinemaBookingSystem.postman_collection.json -e FUCinema-Local.postman_environment.json
```

Expected result: 85 requests, 218 assertions, 0 failed.
Test 6.15 (movie-service down -> `503`, BR14) is manual: stop movie-service, then `POST /api/bookings`.

## Reset everything

```bash
docker compose down -v && rm -rf docker && docker compose up -d
```
