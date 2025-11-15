#!/bin/bash

# Script to build, push to DockerHub, and restart backend container
# Use this after merging changes to main branch
set -e

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

DOCKERHUB_USERNAME="khoatdse172986"
IMAGE_NAME="motorbike-backend"
DOCKERHUB_IMAGE="$DOCKERHUB_USERNAME/$IMAGE_NAME"
CONTAINER_NAME="motorbike-dev-app"

echo -e "${BLUE}=== Build, Push, and Deploy Backend ===${NC}"
echo ""

# Check if we're in the right directory
if [ ! -f "pom.xml" ]; then
    echo -e "${RED}Error: pom.xml not found!${NC}"
    echo "Please run this script from the backend directory"
    exit 1
fi

# Step 1: Build Docker image locally
echo -e "${YELLOW}Step 1: Building Docker image locally...${NC}"
docker build -t $DOCKERHUB_IMAGE:latest -f Dockerfile .

if [ $? -ne 0 ]; then
    echo -e "${RED}Failed to build Docker image${NC}"
    exit 1
fi

echo -e "${GREEN}Docker image built successfully${NC}"
echo ""

# Step 2: Tag with timestamp
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
echo -e "${YELLOW}Step 2: Tagging image with timestamp: $TIMESTAMP${NC}"
docker tag $DOCKERHUB_IMAGE:latest $DOCKERHUB_IMAGE:$TIMESTAMP

# Step 3: Push to DockerHub
echo -e "${YELLOW}Step 3: Pushing images to DockerHub...${NC}"
echo "This will push both 'latest' and '$TIMESTAMP' tags"
echo ""

# Check if logged in
if ! docker info 2>&1 | grep -q "Username"; then
    echo -e "${YELLOW}Please login to DockerHub:${NC}"
    docker login
fi

docker push $DOCKERHUB_IMAGE:latest
docker push $DOCKERHUB_IMAGE:$TIMESTAMP

if [ $? -ne 0 ]; then
    echo -e "${RED}Failed to push images to DockerHub${NC}"
    exit 1
fi

echo -e "${GREEN}Images pushed successfully to DockerHub${NC}"
echo ""

# Step 4: Ask if user wants to restart local container
echo -e "${YELLOW}Step 4: Update local container${NC}"
read -p "Do you want to restart the local backend container with the new image? (y/n): " restart_local

if [[ $restart_local =~ ^[Yy]$ ]]; then
    echo -e "${YELLOW}Stopping existing container...${NC}"
    docker stop $CONTAINER_NAME 2>/dev/null || true
    docker rm $CONTAINER_NAME 2>/dev/null || true
    
    # Check if database and redis are running
    DB_IP=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' motorbike-dev-db 2>/dev/null || echo "")
    REDIS_IP=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' motorbike-dev-redis 2>/dev/null || echo "")
    
    if [ -z "$DB_IP" ] || [ -z "$REDIS_IP" ]; then
        echo -e "${RED}Database or Redis container not found.${NC}"
        echo "Please start them first with: ./dev.sh start"
        exit 1
    fi
    
    echo -e "${YELLOW}Starting container with new image...${NC}"
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
        $DOCKERHUB_IMAGE:latest
    
    echo -e "${YELLOW}Waiting for application to start...${NC}"
    sleep 15
    
    # Check application health
    for i in {1..30}; do
        if curl -s http://localhost:8081/actuator/health &> /dev/null || curl -s http://localhost:8081 &> /dev/null; then
            echo -e "${GREEN}Application is ready!${NC}"
            break
        fi
        sleep 2
    done
    
    echo ""
    echo -e "${GREEN}Local container restarted successfully!${NC}"
    echo "URL: http://localhost:8081"
    echo "Health: http://localhost:8081/actuator/health"
fi

echo ""
echo -e "${GREEN}=== Deployment Summary ===${NC}"
echo "DockerHub Repository: https://hub.docker.com/r/$DOCKERHUB_USERNAME/$IMAGE_NAME"
echo "Latest Tag: $DOCKERHUB_IMAGE:latest"
echo "Timestamped Tag: $DOCKERHUB_IMAGE:$TIMESTAMP"
echo ""
echo "To deploy on other machines:"
echo "  docker pull $DOCKERHUB_IMAGE:latest"
echo "  docker run -d -p 8080:8080 $DOCKERHUB_IMAGE:latest"
echo ""
echo "To use with dev.sh:"
echo "  USE_DOCKERHUB=true ./dev.sh start"
