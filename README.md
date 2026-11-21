# CashCanvas

Bank-statement analyser for Indian users: upload a CSV/XLS export, get every transaction
cleaned up, categorised and summarised on a dashboard.

> Work in progress. The full README (architecture, privacy, measurements) lands on Day 6.

## Run locally

Requirements: Java 21, Maven 3.9+, Docker.

```bash
docker compose up -d            # MySQL 8.4 + Redis 7.4
cd backend
mvn spring-boot:run             # dev profile, http://localhost:8080
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

## Test

```bash
cd backend
mvn verify                      # unit + Testcontainers integration tests (Docker must be running)
```
