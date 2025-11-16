#!/bin/bash

# Script to rebuild and push Docker image with Firebase credentials included
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
echo -e "${BLUE}  Rebuild & Push Image with Firebase  ${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Default DockerHub username
DEFAULT_DOCKERHUB_USERNAME="${DEFAULT_DOCKERHUB_USERNAME:-khoatdse172986}"

# Request DockerHub username with default value
read -p "Enter your DockerHub username [default: $DEFAULT_DOCKERHUB_USERNAME]: " DOCKERHUB_USERNAME
DOCKERHUB_USERNAME="${DOCKERHUB_USERNAME:-$DEFAULT_DOCKERHUB_USERNAME}"

echo -e "${GREEN}Using DockerHub username: $DOCKERHUB_USERNAME${NC}"
echo ""

# Check if Firebase credentials exist
FIREBASE_FILE="src/main/resources/mssus-fcm-firebase-adminsdk-fbsvc-938443350c.json"
if [ ! -f "$FIREBASE_FILE" ]; then
    echo -e "${RED}ERROR: Firebase credentials not found at $FIREBASE_FILE${NC}"
    echo "Please ensure the Firebase service account JSON file exists."
    exit 1
fi

echo -e "${GREEN}✓ Firebase credentials found${NC}"
echo ""

# Build with Maven
echo -e "${YELLOW}Step 1: Building Spring Boot application with Maven...${NC}"
mvn clean package -DskipTests \
    -Dmaven.compiler.failOnWarning=false \
    -Dmaven.compiler.showWarnings=false \
    -Dmapstruct.unmappedTargetPolicy=IGNORE

echo ""
echo -e "${GREEN}✓ Maven build completed${NC}"
echo ""

# Verify Firebase credentials in JAR
echo -e "${YELLOW}Step 2: Verifying Firebase credentials in JAR...${NC}"
JAR_FILE="target/MotorbikeSharingSystem_BE-0.0.1-SNAPSHOT.jar"

if unzip -l "$JAR_FILE" | grep -q "mssus-fcm-firebase-adminsdk"; then
    echo -e "${GREEN}✓ Firebase credentials found in JAR${NC}"
else
    echo -e "${RED}ERROR: Firebase credentials NOT found in JAR!${NC}"
    echo "This might be a Maven resources issue."
    exit 1
fi

echo ""

# Login to DockerHub
echo -e "${YELLOW}Step 3: Logging in to DockerHub...${NC}"
docker login

echo ""

# Setup Docker Buildx for multi-platform
echo -e "${YELLOW}Step 4: Setting up Docker Buildx...${NC}"
docker buildx version || {
    echo -e "${RED}Docker Buildx not available. Installing...${NC}"
    docker buildx install
}

# Create or use existing builder
if ! docker buildx inspect multiplatform-builder >/dev/null 2>&1; then
    echo "Creating new buildx builder..."
    docker buildx create --name multiplatform-builder --use
else
    echo "Using existing buildx builder..."
    docker buildx use multiplatform-builder
fi

docker buildx inspect --bootstrap

echo ""

# Build and push multi-platform image
echo -e "${YELLOW}Step 5: Building and pushing multi-platform Docker image...${NC}"
echo "Platforms: linux/amd64, linux/arm64"
echo "Image: $DOCKERHUB_USERNAME/motorbike-backend:latest"
echo ""

TIMESTAMP=$(date +%Y%m%d-%H%M%S)

docker buildx build \
    --platform linux/amd64,linux/arm64 \
    --push \
    -t $DOCKERHUB_USERNAME/motorbike-backend:latest \
    -t $DOCKERHUB_USERNAME/motorbike-backend:$TIMESTAMP \
    -t $DOCKERHUB_USERNAME/motorbike-backend:with-firebase \
    -f Dockerfile .

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Build & Push Completed Successfully!  ${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "Images pushed:"
echo "  • $DOCKERHUB_USERNAME/motorbike-backend:latest"
echo "  • $DOCKERHUB_USERNAME/motorbike-backend:$TIMESTAMP"
echo "  • $DOCKERHUB_USERNAME/motorbike-backend:with-firebase"
echo ""
echo "To run the new image:"
echo "  ./pull-and-run-from-dockerhub.sh"
echo ""
echo -e "${YELLOW}Note: Firebase credentials are now embedded in the image.${NC}"
echo -e "${YELLOW}Anyone can pull and run without needing the credentials file.${NC}"
