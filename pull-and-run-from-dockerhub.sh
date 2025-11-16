#!/bin/bash

# Script to pull and run backend image from DockerHub
set -e

# Load config if exists
if [ -f "$(dirname "$0")/.dockerhub-config" ]; then
    source "$(dirname "$0")/.dockerhub-config"
fi

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Pull & Run Backend from DockerHub  ${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Default DockerHub username - thay đổi này nếu bạn đã có username mặc định
DEFAULT_DOCKERHUB_USERNAME="khoatdse172986"

# Request DockerHub username with default value
read -p "Enter DockerHub username [default: $DEFAULT_DOCKERHUB_USERNAME]: " DOCKERHUB_USERNAME
DOCKERHUB_USERNAME="${DOCKERHUB_USERNAME:-$DEFAULT_DOCKERHUB_USERNAME}"

echo -e "${GREEN}Using DockerHub username: $DOCKERHUB_USERNAME${NC}"
echo ""

# Image details
IMAGE_NAME="$DOCKERHUB_USERNAME/motorbike-backend:latest"
CONTAINER_NAME="motorbike-backend"
NETWORK_NAME="motorbike-network"

# Database configuration (can be customized)
DB_HOST="${DB_HOST:-postgres-db}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-mssus_db}"
DB_USERNAME="${DB_USERNAME:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-postgres}"

# Backend configuration
BACKEND_PORT="${BACKEND_PORT:-8080}"
SPRING_PROFILE="${SPRING_PROFILE:-prod}"

echo -e "${YELLOW}Configuration:${NC}"
echo "  Image: $IMAGE_NAME"
echo "  Container: $CONTAINER_NAME"
echo "  Backend Port: $BACKEND_PORT"
echo "  Spring Profile: $SPRING_PROFILE"
echo "  Database Host: $DB_HOST"
echo "  Database Port: $DB_PORT"
echo "  Database Name: $DB_NAME"
echo ""

# Ask if user wants to run with database
read -p "Do you want to run PostgreSQL database container? (y/n): " RUN_DB

# Create network if not exists
echo -e "${YELLOW}Creating Docker network...${NC}"
docker network inspect $NETWORK_NAME >/dev/null 2>&1 || \
    docker network create $NETWORK_NAME && \
    echo -e "${GREEN}Network '$NETWORK_NAME' created or already exists${NC}"

# Run PostgreSQL if requested
if [[ "$RUN_DB" == "y" || "$RUN_DB" == "Y" ]]; then
    echo -e "${YELLOW}Starting PostgreSQL container...${NC}"
    
    # Stop and remove existing PostgreSQL container if exists
    docker stop postgres-db 2>/dev/null || true
    docker rm postgres-db 2>/dev/null || true
    
    docker run -d \
        --name postgres-db \
        --network $NETWORK_NAME \
        -e POSTGRES_DB=$DB_NAME \
        -e POSTGRES_USER=$DB_USERNAME \
        -e POSTGRES_PASSWORD=$DB_PASSWORD \
        -p 5432:5432 \
        -v postgres_data:/var/lib/postgresql/data \
        --restart unless-stopped \
        postgres:13-alpine
    
    echo -e "${GREEN}PostgreSQL container started${NC}"
    echo -e "${YELLOW}Waiting for PostgreSQL to be ready...${NC}"
    sleep 10
fi

# Detect platform
PLATFORM=$(uname -m)
if [ "$PLATFORM" = "arm64" ] || [ "$PLATFORM" = "aarch64" ]; then
    echo -e "${YELLOW}Detected ARM64 architecture (Apple Silicon)${NC}"
    echo -e "${YELLOW}Will use --platform linux/amd64 for compatibility${NC}"
    PLATFORM_FLAG="--platform linux/amd64"
else
    echo -e "${YELLOW}Detected AMD64 architecture${NC}"
    PLATFORM_FLAG=""
fi
echo ""

# Pull the latest image
echo -e "${YELLOW}Pulling image from DockerHub...${NC}"
docker pull $PLATFORM_FLAG $IMAGE_NAME
echo -e "${GREEN}Image pulled successfully!${NC}"
echo ""

# Stop and remove existing backend container if exists
echo -e "${YELLOW}Stopping existing backend container if any...${NC}"
docker stop $CONTAINER_NAME 2>/dev/null || true
docker rm $CONTAINER_NAME 2>/dev/null || true

# Run the backend container
echo -e "${YELLOW}Starting backend container...${NC}"
docker run -d \
    $PLATFORM_FLAG \
    --name $CONTAINER_NAME \
    --network $NETWORK_NAME \
    -p $BACKEND_PORT:8080 \
    -e SPRING_PROFILES_ACTIVE=$SPRING_PROFILE \
    -e SERVER_PORT=8080 \
    -e DB_URL="jdbc:postgresql://$DB_HOST:$DB_PORT/$DB_NAME" \
    -e DB_USERNAME=$DB_USERNAME \
    -e DB_PASSWORD=$DB_PASSWORD \
    -e JPA_DDL_AUTO=update \
    -e FLYWAY_ENABLED=true \
    -e FLYWAY_BASELINE_ON_MIGRATE=true \
    -e CORS_ALLOWED_ORIGINS="http://localhost:3000,http://localhost:3001,https://*.vercel.app" \
    --restart unless-stopped \
    $IMAGE_NAME

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Backend container started successfully!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${BLUE}Container Information:${NC}"
echo "  Name: $CONTAINER_NAME"
echo "  Image: $IMAGE_NAME"
echo "  Port: $BACKEND_PORT"
echo "  API URL: http://localhost:$BACKEND_PORT"
echo ""
echo -e "${BLUE}Useful Commands:${NC}"
echo "  View logs:        docker logs -f $CONTAINER_NAME"
echo "  Stop container:   docker stop $CONTAINER_NAME"
echo "  Start container:  docker start $CONTAINER_NAME"
echo "  Remove container: docker rm -f $CONTAINER_NAME"
echo "  Container stats:  docker stats $CONTAINER_NAME"
echo "  Exec into container: docker exec -it $CONTAINER_NAME /bin/sh"
echo ""

# Show container logs
echo -e "${YELLOW}Showing container logs (Ctrl+C to exit):${NC}"
echo ""
sleep 2
docker logs -f $CONTAINER_NAME
