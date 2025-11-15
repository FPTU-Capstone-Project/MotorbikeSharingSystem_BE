#!/bin/bash

# Script to update backend container with latest image from DockerHub
set -e

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

DOCKERHUB_IMAGE="khoatdse172986/motorbike-backend:latest"
CONTAINER_NAME="motorbike-dev-app"

echo -e "${BLUE}=== Update and Restart Backend Container ===${NC}"
echo ""

# Pull latest image from DockerHub
echo -e "${YELLOW}Pulling latest image from DockerHub...${NC}"
docker pull $DOCKERHUB_IMAGE

if [ $? -ne 0 ]; then
    echo -e "${RED}Failed to pull image from DockerHub${NC}"
    exit 1
fi

echo -e "${GREEN}Successfully pulled latest image${NC}"

# Stop and remove existing container if running
echo -e "${YELLOW}Stopping existing container...${NC}"
if docker ps -q -f name=$CONTAINER_NAME | grep -q .; then
    docker stop $CONTAINER_NAME
    echo -e "${GREEN}Container stopped${NC}"
else
    echo -e "${YELLOW}Container not running${NC}"
fi

if docker ps -a -q -f name=$CONTAINER_NAME | grep -q .; then
    docker rm $CONTAINER_NAME
    echo -e "${GREEN}Container removed${NC}"
fi

# Get database and redis container IPs
DB_IP=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' motorbike-dev-db 2>/dev/null || echo "")
REDIS_IP=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' motorbike-dev-redis 2>/dev/null || echo "")

if [ -z "$DB_IP" ]; then
    echo -e "${RED}Database container not found. Please start it first with ./dev.sh start${NC}"
    exit 1
fi

if [ -z "$REDIS_IP" ]; then
    echo -e "${RED}Redis container not found. Please start it first with ./dev.sh start${NC}"
    exit 1
fi

# Start new container with latest image
echo -e "${YELLOW}Starting new container with latest image...${NC}"
docker run -d \
    --name $CONTAINER_NAME \
    -p 8081:8080 \
    -e SPRING_DATASOURCE_URL="jdbc:postgresql://$DB_IP:5432/motorbike_sharing_db" \
    -e SPRING_DATASOURCE_USERNAME="postgres" \
    -e SPRING_DATASOURCE_PASSWORD="Password@123" \
    -e SPRING_JPA_HIBERNATE_DDL_AUTO=update \
    -e SPRING_JPA_SHOW_SQL=true \
    -e SPRING_DATA_REDIS_HOST="$REDIS_IP" \
    -e SPRING_DATA_REDIS_PORT=6379 \
    -e SPRING_DATA_REDIS_PASSWORD="123456" \
    -e REDIS_PASSWORD="123456" \
    $DOCKERHUB_IMAGE

echo -e "${YELLOW}Waiting for application to start...${NC}"
sleep 15

# Check if application is ready
echo -e "${YELLOW}Checking application health...${NC}"
for i in {1..30}; do
    if curl -s http://localhost:8081/actuator/health &> /dev/null || curl -s http://localhost:8081 &> /dev/null; then
        echo -e "${GREEN}Application is ready!${NC}"
        echo ""
        echo -e "${GREEN}Backend updated and restarted successfully!${NC}"
        echo "URL: http://localhost:8081"
        echo "Health: http://localhost:8081/actuator/health"
        echo ""
        echo "View logs: docker logs $CONTAINER_NAME -f"
        exit 0
    fi
    sleep 2
done

echo -e "${YELLOW}Application might still be starting up...${NC}"
echo "Check logs with: docker logs $CONTAINER_NAME -f"
