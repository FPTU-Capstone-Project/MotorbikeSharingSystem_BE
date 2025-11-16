# Pull and Run Backend from DockerHub

Hướng dẫn pull và chạy Docker image backend từ DockerHub.

## Prerequisites

- Docker đã được cài đặt
- Image backend đã được push lên DockerHub

## DockerHub Username Configuration

Scripts sử dụng username mặc định từ file `.dockerhub-config`:
```bash
DEFAULT_DOCKERHUB_USERNAME="khoatdse172986"
```

**Cách thay đổi username:**

1. **Chỉnh sửa file config:**
   ```bash
   nano .dockerhub-config
   # Thay đổi DEFAULT_DOCKERHUB_USERNAME="your-username"
   ```

2. **Hoặc override bằng biến môi trường:**
   ```bash
   export DOCKERHUB_USERNAME="your-username"
   ```

3. **Hoặc nhập khi chạy** (nhấn Enter để dùng default)

## Scripts Available

### 1. `pull-and-run-from-dockerhub.sh` (Full Setup)

Script đầy đủ, tự động setup database và backend.

**Features:**
- Pull image từ DockerHub
- Tạo Docker network
- Tùy chọn chạy PostgreSQL container
- Chạy backend với cấu hình đầy đủ
- Hiển thị logs real-time

**Usage:**
```bash
# Cấp quyền thực thi
chmod +x pull-and-run-from-dockerhub.sh

# Chạy script
./pull-and-run-from-dockerhub.sh
```

Script sẽ hỏi:
1. DockerHub username (người đã push image)
   - Mặc định: `khoapham1912` (nhấn Enter để dùng default)
   - Hoặc nhập username khác nếu bạn đã push với tên khác
2. Có muốn chạy PostgreSQL container không

**Environment Variables (Optional):**
```bash
export DB_HOST=postgres-db
export DB_PORT=5432
export DB_NAME=mssus_db
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
export BACKEND_PORT=8080
export SPRING_PROFILE=prod
```

---

### 2. `run-backend-simple.sh` (Quick Run)

Script đơn giản, chỉ chạy backend (giả định database đã có sẵn).

**Features:**
- Pull và chạy backend nhanh chóng
- Sử dụng network host
- Kết nối database localhost

**Usage:**
```bash
# Cấp quyền thực thi
chmod +x run-backend-simple.sh

# Chạy với username
./run-backend-simple.sh <dockerhub-username> [port]

# Hoặc script sẽ hỏi username
./run-backend-simple.sh
```

**Examples:**
```bash
# Chạy với port mặc định 8080
./run-backend-simple.sh yourusername

# Chạy với port tùy chỉnh
./run-backend-simple.sh yourusername 9090

# Với custom database
DB_URL="jdbc:postgresql://remote-host:5432/mydb" \
DB_USERNAME="admin" \
DB_PASSWORD="secret" \
./run-backend-simple.sh yourusername
```

---

## Docker Commands Reference

### View Logs
```bash
# Real-time logs
docker logs -f motorbike-backend

# Last 100 lines
docker logs --tail 100 motorbike-backend
```

### Container Management
```bash
# Stop container
docker stop motorbike-backend

# Start container
docker start motorbike-backend

# Restart container
docker restart motorbike-backend

# Remove container
docker rm -f motorbike-backend
```

### Database Container (if using full setup)
```bash
# Check database status
docker logs postgres-db

# Connect to database
docker exec -it postgres-db psql -U postgres -d mssus_db

# Stop database
docker stop postgres-db
```

### Debugging
```bash
# Execute commands inside container
docker exec -it motorbike-backend /bin/sh

# Check container stats
docker stats motorbike-backend

# Inspect container
docker inspect motorbike-backend

# Check network
docker network inspect motorbike-network
```

---

## Configuration

### Backend Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | `prod` | Spring profile |
| `SERVER_PORT` | `8080` | Internal server port |
| `DB_URL` | `jdbc:postgresql://postgres-db:5432/mssus_db` | Database URL |
| `DB_USERNAME` | `postgres` | Database username |
| `DB_PASSWORD` | `postgres` | Database password |
| `JPA_DDL_AUTO` | `update` | Hibernate DDL auto |
| `FLYWAY_ENABLED` | `true` | Enable Flyway migration |
| `CORS_ALLOWED_ORIGINS` | `*` | Allowed CORS origins |

### Network Configuration

- **Network Name**: `motorbike-network`
- **Backend Port**: `8080` (host) → `8080` (container)
- **Database Port**: `5432` (host) → `5432` (container)

---

## Troubleshooting

### Image not found
```bash
# Check if image exists on DockerHub
docker search <username>/motorbike-backend

# Pull manually
docker pull <username>/motorbike-backend:latest
```

### Port already in use
```bash
# Check what's using the port
lsof -i :8080

# Use different port
BACKEND_PORT=9090 ./pull-and-run-from-dockerhub.sh
```

### Database connection failed
```bash
# Check if database is running
docker ps | grep postgres

# Check database logs
docker logs postgres-db

# Test connection
docker exec -it postgres-db pg_isready -U postgres
```

### Container keeps restarting
```bash
# Check logs for errors
docker logs motorbike-backend

# Check container exit code
docker inspect motorbike-backend | grep -A 5 State
```

---

## Advanced Usage

### Run with custom configuration
```bash
docker run -d \
    --name motorbike-backend \
    -p 8080:8080 \
    -e SPRING_PROFILES_ACTIVE=prod \
    -e DB_URL="jdbc:postgresql://your-db-host:5432/your-db" \
    -e DB_USERNAME="your-username" \
    -e DB_PASSWORD="your-password" \
    -e JWT_SECRET="your-secret-key" \
    -e CORS_ALLOWED_ORIGINS="https://your-frontend.com" \
    --restart unless-stopped \
    <username>/motorbike-backend:latest
```

### Run with volume mount for logs
```bash
docker run -d \
    --name motorbike-backend \
    -p 8080:8080 \
    -v $(pwd)/logs:/app/logs \
    <username>/motorbike-backend:latest
```

### Run with specific version
```bash
# Pull specific version
docker pull <username>/motorbike-backend:20250116-123456

# Run specific version
docker run -d \
    --name motorbike-backend \
    -p 8080:8080 \
    <username>/motorbike-backend:20250116-123456
```

---

## Health Check

### Check if backend is running
```bash
# Check container status
docker ps | grep motorbike-backend

# Check health endpoint
curl http://localhost:8080/actuator/health

# Check API
curl http://localhost:8080/api/v1/health
```

### Monitor Resources
```bash
# Real-time resource usage
docker stats motorbike-backend

# Memory and CPU
docker inspect motorbike-backend | grep -A 5 Memory
```

---

## Cleanup

### Remove everything
```bash
# Stop and remove containers
docker stop motorbike-backend postgres-db 2>/dev/null || true
docker rm motorbike-backend postgres-db 2>/dev/null || true

# Remove network
docker network rm motorbike-network 2>/dev/null || true

# Remove volumes (WARNING: This deletes database data)
docker volume rm postgres_data 2>/dev/null || true

# Remove images
docker rmi <username>/motorbike-backend:latest
docker rmi postgres:13-alpine
```

---

## Related Files

- `push-to-dockerhub.sh` - Script để push images lên DockerHub
- `Dockerfile` - Dockerfile để build backend image
- `docker-compose.yml` - Compose file cho full stack

---

## Support

Nếu gặp vấn đề:
1. Check logs: `docker logs -f motorbike-backend`
2. Check container status: `docker ps -a | grep motorbike`
3. Check network: `docker network ls`
4. Restart container: `docker restart motorbike-backend`
