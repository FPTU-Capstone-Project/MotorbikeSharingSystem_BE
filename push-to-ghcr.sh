#!/bin/bash

# Script to push images to GitHub Container Registry
set -e

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${YELLOW}=== Push Motorbike Images to GitHub Container Registry ===${NC}"
echo ""

# GitHub configuration
GITHUB_USERNAME="FPTU-Capstone-Project"
REPO_NAME="MotorbikeSharingSystem_BE"

echo -e "${YELLOW}Prerequisites:${NC}"
echo "1. Create a Personal Access Token (PAT) on GitHub:"
echo "   - Go to: https://github.com/settings/tokens"
echo "   - Click 'Generate new token (classic)'"
echo "   - Select scopes: write:packages, read:packages, delete:packages"
echo "   - Copy the token"
echo ""
read -p "Do you have a GitHub PAT ready? (y/n): " has_token

if [[ ! $has_token =~ ^[Yy]$ ]]; then
    echo -e "${RED}Please create a PAT first, then run this script again.${NC}"
    exit 1
fi

# Login to GitHub Container Registry
echo -e "${YELLOW}Logging in to GitHub Container Registry...${NC}"
echo "Paste your GitHub Personal Access Token:"
docker login ghcr.io -u $GITHUB_USERNAME

# Tag and push backend app image
echo -e "${YELLOW}Tagging and pushing backend app image...${NC}"
IMAGE_NAME="ghcr.io/${GITHUB_USERNAME,,}/motorbike-backend"
VERSION=$(date +%Y%m%d-%H%M%S)

docker tag motorbike-dev-app:latest $IMAGE_NAME:latest
docker tag motorbike-dev-app:latest $IMAGE_NAME:$VERSION
docker push $IMAGE_NAME:latest
docker push $IMAGE_NAME:$VERSION

echo ""
echo -e "${GREEN}Images pushed successfully!${NC}"
echo ""
echo "Your images are now available at:"
echo "  https://github.com/orgs/$GITHUB_USERNAME/packages?repo_name=$REPO_NAME"
echo ""
echo "To pull on another machine:"
echo "  docker login ghcr.io"
echo "  docker pull $IMAGE_NAME:latest"
echo ""
echo -e "${YELLOW}Note: You may need to make the package public in GitHub Settings${NC}"
