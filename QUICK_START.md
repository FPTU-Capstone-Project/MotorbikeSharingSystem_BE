# Quick Start - Pull and Run Backend từ DockerHub

## 🚀 Cách Nhanh Nhất

```bash
# Chỉ cần chạy (nhấn Enter để dùng username mặc định)
./pull-and-run-from-dockerhub.sh
```

## 📝 Thông Tin

- **DockerHub Username mặc định:** `khoatdse172986`
- **Image:** `khoatdse172986/motorbike-backend:latest`
- **Port:** `8080` (có thể thay đổi)

## 🔧 Thay Đổi Username

### Cách 1: Chỉnh sửa file config (khuyến nghị)
```bash
nano .dockerhub-config
# Thay đổi: DEFAULT_DOCKERHUB_USERNAME="your-username"
```

### Cách 2: Biến môi trường
```bash
export DOCKERHUB_USERNAME="your-username"
./pull-and-run-from-dockerhub.sh
```

### Cách 3: Nhập khi chạy
```bash
./pull-and-run-from-dockerhub.sh
# Khi hỏi username, nhập username của bạn
# Hoặc nhấn Enter để dùng mặc định
```

## 📦 Scripts Available

### 1. `pull-and-run-from-dockerhub.sh` - Full Setup
- Pull image từ DockerHub
- Tự động tạo PostgreSQL container (nếu cần)
- Tự động tạo network
- Chạy backend với full configuration

**Usage:**
```bash
./pull-and-run-from-dockerhub.sh
```

### 2. `run-backend-simple.sh` - Quick Run
- Chỉ chạy backend (không tạo database)
- Dùng khi bạn đã có database riêng
- Nhẹ và nhanh hơn

**Usage:**
```bash
# Dùng default username
./run-backend-simple.sh

# Chỉ định username
./run-backend-simple.sh your-username

# Chỉ định username và port
./run-backend-simple.sh your-username 9090
```

### 3. `push-to-dockerhub.sh` - Push Image
- Push image lên DockerHub
- Tự động tag với timestamp
- Dùng username từ config

**Usage:**
```bash
./push-to-dockerhub.sh
```

## 🌍 Environment Variables

Có thể override các giá trị mặc định:

```bash
# Database
export DB_HOST=postgres-db
export DB_PORT=5432
export DB_NAME=mssus_db
export DB_USERNAME=postgres
export DB_PASSWORD=postgres

# Backend
export BACKEND_PORT=8080
export SPRING_PROFILE=prod

# DockerHub
export DOCKERHUB_USERNAME=your-username

# Chạy script
./pull-and-run-from-dockerhub.sh
```

## ✅ Verify

Sau khi chạy, kiểm tra:

```bash
# Check containers
docker ps

# Check logs
docker logs motorbike-backend

# Test API
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/v1/health
```

## 🛑 Stop & Clean

```bash
# Stop backend
docker stop motorbike-backend

# Stop cả database
docker stop motorbike-backend postgres-db

# Remove containers
docker rm motorbike-backend postgres-db

# Remove network
docker network rm motorbike-network
```

## 📚 Thêm Thông Tin

Xem file `DOCKER_RUN_GUIDE.md` để biết chi tiết đầy đủ.

## 🐛 Troubleshooting

### Port đã được sử dụng
```bash
export BACKEND_PORT=9090
./pull-and-run-from-dockerhub.sh
```

### Image không tìm thấy
```bash
# Kiểm tra image trên DockerHub
docker pull khoatdse172986/motorbike-backend:latest

# Hoặc dùng username khác
export DOCKERHUB_USERNAME=your-username
./pull-and-run-from-dockerhub.sh
```

### Database connection failed
```bash
# Kiểm tra PostgreSQL đang chạy
docker ps | grep postgres

# Restart database
docker restart postgres-db

# Check logs
docker logs postgres-db
```

---

**Happy Dockering! 🐳**
