# Docker Deployment Guide

This guide explains how to use Docker images for the Motorbike Sharing System backend.

## Available Scripts

### 1. `push-to-dockerhub.sh`
Push existing local images to DockerHub.

**Usage:**
```bash
./push-to-dockerhub.sh
```

**What it does:**
- Tags local `motorbike-dev-app:latest` image
- Pushes to DockerHub as `khoatdse172986/motorbike-backend:latest`
- Creates timestamped version for rollback

---

### 2. `push-to-ghcr.sh`
Push images to GitHub Container Registry (requires PAT token).

**Usage:**
```bash
./push-to-ghcr.sh
```

**Prerequisites:**
- Create GitHub Personal Access Token
- Scopes: `write:packages`, `read:packages`, `delete:packages`

---

### 3. `update-and-restart.sh`
Pull latest image from DockerHub and restart backend container.

**Usage:**
```bash
./update-and-restart.sh
```

**What it does:**
- Pulls latest image from DockerHub
- Stops current container
- Starts new container with latest image
- Automatically connects to database and redis

**Use this when:**
- New version pushed to DockerHub
- Want to update local environment without rebuilding

---

### 4. `build-push-deploy.sh`
Complete workflow: build, push, and optionally restart.

**Usage:**
```bash
./build-push-deploy.sh
```

**What it does:**
1. Builds Docker image from source code
2. Tags with `latest` and timestamp
3. Pushes both tags to DockerHub
4. Optionally restarts local container

**Use this after:**
- Merging changes to main branch
- Want to publish new version

---

### 5. `dev.sh` (Updated)
Development environment management with DockerHub support.

**Usage:**

Build locally:
```bash
./dev.sh start
```

Use DockerHub image:
```bash
USE_DOCKERHUB=true ./dev.sh start
```

Stop everything:
```bash
./dev.sh stop
```

---

## Workflows

### For Development (Local Changes)

```bash
# Start with local build
./dev.sh start

# Make code changes...

# Rebuild and restart
./dev.sh stop
./dev.sh start
```

---

### After Merging to Main

```bash
# Build, tag, push, and deploy
./build-push-deploy.sh

# Answer 'y' when asked to restart local container
```

---

### Quick Update from DockerHub

```bash
# If someone else pushed new version
./update-and-restart.sh
```

---

### Fresh Setup on New Machine

```bash
# Clone repository
git clone <repo-url>
cd backend

# Start with DockerHub image (no build needed)
USE_DOCKERHUB=true ./dev.sh start
```

---

## Docker Compose

The `docker-compose.yml` in the root directory now uses DockerHub images by default.

**Start all services:**
```bash
cd ..  # Go to project root
docker-compose up -d
```

**Update backend image:**
```bash
docker-compose pull backend
docker-compose up -d backend
```

**View logs:**
```bash
docker-compose logs -f backend
```

---

## DockerHub Repository

Images are hosted at:
- **Repository:** `khoatdse172986/motorbike-backend`
- **URL:** https://hub.docker.com/r/khoatdse172986/motorbike-backend

**Available tags:**
- `latest` - Most recent version
- `YYYYMMDD-HHMMSS` - Timestamped versions for rollback

---

## Environment Variables

Backend container requires:

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_DATASOURCE_URL` | PostgreSQL connection | Required |
| `SPRING_DATASOURCE_USERNAME` | Database user | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | Database password | Required |
| `SPRING_DATA_REDIS_HOST` | Redis host | Required |
| `SPRING_DATA_REDIS_PORT` | Redis port | `6379` |
| `SPRING_DATA_REDIS_PASSWORD` | Redis password | Required |

---

## Troubleshooting

### Image not updating
```bash
# Force pull latest
docker pull khoatdse172986/motorbike-backend:latest --no-cache

# Remove local cache
docker rmi khoatdse172986/motorbike-backend:latest
docker pull khoatdse172986/motorbike-backend:latest
```

### Container won't start
```bash
# Check logs
docker logs motorbike-dev-app

# Check if database is running
docker ps | grep motorbike-dev-db

# Restart dependencies
./dev.sh stop
./dev.sh start
```

### Push failed
```bash
# Login again
docker login

# Check credentials
docker info | grep Username
```

---

## Best Practices

1. **Always tag with timestamp** - Enables easy rollback
2. **Test locally before pushing** - Ensure build works
3. **Use environment variables** - Never hardcode credentials
4. **Document breaking changes** - Update this README
5. **Keep images small** - Use multi-stage builds

---

## CI/CD Integration

For GitHub Actions, create `.github/workflows/docker-publish.yml`:

```yaml
name: Docker Build and Push

on:
  push:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      
      - name: Login to DockerHub
        uses: docker/login-action@v1
        with:
          username: ${{ secrets.DOCKERHUB_USERNAME }}
          password: ${{ secrets.DOCKERHUB_TOKEN }}
      
      - name: Build and push
        run: |
          cd backend
          ./build-push-deploy.sh
```

Add secrets in GitHub: Settings > Secrets > Actions
- `DOCKERHUB_USERNAME`
- `DOCKERHUB_TOKEN`

---

## Quick Reference

```bash
# Build and push
./build-push-deploy.sh

# Update from DockerHub
./update-and-restart.sh

# Start with local build
./dev.sh start

# Start with DockerHub image
USE_DOCKERHUB=true ./dev.sh start

# Stop everything
./dev.sh stop

# View logs
docker logs motorbike-dev-app -f
```
