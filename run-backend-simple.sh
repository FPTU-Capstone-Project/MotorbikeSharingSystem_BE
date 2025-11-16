#!/bin/bash

# Quick script to pull and run backend (standalone, no database)
set -e

# Load config if exists
if [ -f "$(dirname "$0")/.dockerhub-config" ]; then
    source "$(dirname "$0")/.dockerhub-config"
fi

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${YELLOW}=== Quick Run Backend from DockerHub ===${NC}"
echo ""

# Default DockerHub username
DEFAULT_DOCKERHUB_USERNAME="${DEFAULT_DOCKERHUB_USERNAME:-khoatdse172986}"

# Configuration
DOCKERHUB_USERNAME="${1:-}"
CONTAINER_NAME="motorbike-backend"
BACKEND_PORT="${2:-8080}"

# Check if username provided, otherwise use default
if [ -z "$DOCKERHUB_USERNAME" ]; then
    read -p "Enter DockerHub username [default: $DEFAULT_DOCKERHUB_USERNAME]: " DOCKERHUB_USERNAME
    DOCKERHUB_USERNAME="${DOCKERHUB_USERNAME:-$DEFAULT_DOCKERHUB_USERNAME}"
fi

echo -e "${GREEN}Using DockerHub username: $DOCKERHUB_USERNAME${NC}"

IMAGE_NAME="$DOCKERHUB_USERNAME/motorbike-backend:latest"

# Database configuration from environment or defaults
DB_URL="${DB_URL:-jdbc:postgresql://localhost:5432/mssus_db}"
DB_USERNAME="${DB_USERNAME:-postgres}"
DB_PASSWORD="${DB_PASSWORD:-postgres}"

echo "Image: $IMAGE_NAME"
echo "Port: $BACKEND_PORT"
echo "Database: $DB_URL"
echo ""

# Pull latest image
echo -e "${YELLOW}Pulling latest image...${NC}"
docker pull $IMAGE_NAME

# Stop existing container
echo -e "${YELLOW}Stopping existing container...${NC}"
docker stop $CONTAINER_NAME 2>/dev/null || true
docker rm $CONTAINER_NAME 2>/dev/null || true

# Run container
echo -e "${YELLOW}Starting backend container...${NC}"
docker run -d \
    --name $CONTAINER_NAME \
    -p $BACKEND_PORT:8080 \
    --network host \
    -e SPRING_PROFILES_ACTIVE=prod \
    -e SERVER_PORT=8080 \
    -e DB_URL="$DB_URL" \
    -e DB_USERNAME="$DB_USERNAME" \
    -e DB_PASSWORD="$DB_PASSWORD" \
    -e JPA_DDL_AUTO=update \
    -e CORS_ALLOWED_ORIGINS="*" \
    --restart unless-stopped \
    $IMAGE_NAME

echo ""
echo -e "${GREEN}Backend started successfully!${NC}"
echo "API URL: http://localhost:$BACKEND_PORT"
echo ""
echo "View logs: docker logs -f $CONTAINER_NAME"
echo ""

# Show initial logs
docker logs -f $CONTAINER_NAME
