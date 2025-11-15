#!/bin/bash

# Script to push images to DockerHub
set -e

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${YELLOW}=== Push Motorbike Images to DockerHub ===${NC}"
echo ""

# Request DockerHub username
read -p "Enter your DockerHub username: " DOCKERHUB_USERNAME

if [ -z "$DOCKERHUB_USERNAME" ]; then
    echo -e "${RED}Username cannot be empty!${NC}"
    exit 1
fi

# Login to DockerHub
echo -e "${YELLOW}Logging in to DockerHub...${NC}"
docker login

# Tag and push backend app image
echo -e "${YELLOW}Tagging and pushing backend app image...${NC}"
docker tag motorbike-dev-app:latest $DOCKERHUB_USERNAME/motorbike-backend:latest
docker tag motorbike-dev-app:latest $DOCKERHUB_USERNAME/motorbike-backend:$(date +%Y%m%d-%H%M%S)
docker push $DOCKERHUB_USERNAME/motorbike-backend:latest
docker push $DOCKERHUB_USERNAME/motorbike-backend:$(date +%Y%m%d-%H%M%S)

echo ""
echo -e "${GREEN}Images pushed successfully!${NC}"
echo ""
echo "Your images are now available at:"
echo "  https://hub.docker.com/r/$DOCKERHUB_USERNAME/motorbike-backend"
echo ""
echo "To pull on another machine:"
echo "  docker pull $DOCKERHUB_USERNAME/motorbike-backend:latest"
echo ""
echo -e "${YELLOW}Note: PostgreSQL and Redis images are public, no need to push them${NC}"
