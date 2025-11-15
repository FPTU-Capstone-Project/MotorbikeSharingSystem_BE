#!/bin/bash

# Motorbike Sharing System - Local Development Script
# All-in-one script that works on any machine with just Docker installed
set -e

# Colors for better output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Configuration - completely separate from production
PROJECT_NAME="motorbike-dev"
DB_NAME="motorbike_sharing_db"
DB_USER="postgres"
DB_PASSWORD="Password@123"
# Use non-standard ports to avoid conflicts with existing services
DB_PORT="5433"  # Changed from 5432 to avoid PostgreSQL conflicts
APP_PORT="8081" # Changed from 8080 to avoid common app conflicts
REDIS_PORT="6380" # Changed from 6379 to avoid Redis conflicts
REDIS_PASSWORD="123456"
DOCKERHUB_IMAGE="khoatdse172986/motorbike-backend:latest"
USE_DOCKERHUB="${USE_DOCKERHUB:-false}"  # Set to 'true' to use DockerHub image instead of building

echo -e "${BLUE}Motorbike Sharing System - Local Development${NC}"
echo "=============================================="

# Check Docker installation and status
check_docker() {
    echo -e "${YELLOW}Checking Docker...${NC}"
    
    if ! command -v docker &> /dev/null; then
        echo -e "${RED}Docker not found! Please install Docker Desktop:${NC}"
        echo "https://www.docker.com/products/docker-desktop"
        echo "After installation, restart terminal and run this script again."
        exit 1
    fi
    
    if ! docker info &> /dev/null; then
        echo -e "${RED}Docker not running! Please start Docker Desktop and try again.${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}Docker is ready${NC}"
}

start_redis() {
    echo -e "${YELLOW}Starting Redis cache on port $REDIS_PORT...${NC}"
    echo -e "${BLUE}Using port $REDIS_PORT to avoid conflicts with system Redis (port 6379)${NC}"

    # Stop and remove existing container if exists
    docker stop motorbike-dev-redis 2>/dev/null || true
    docker rm motorbike-dev-redis 2>/dev/null || true

    # Start Redis container with password authentication
    docker run -d \
        --name motorbike-dev-redis \
        -p "$REDIS_PORT:6379" \
        -v motorbike-dev-redis-data:/data \
        redis:alpine \
        redis-server --requirepass "$REDIS_PASSWORD"

    echo -e "${YELLOW}Waiting for Redis to be ready...${NC}"

    # Wait for Redis to be ready
    for i in {1..30}; do
        if docker exec motorbike-dev-redis redis-cli -a "$REDIS_PASSWORD" --no-auth-warning ping &> /dev/null; then
            echo -e "${GREEN}Redis is ready!${NC}"
            return
        fi
        sleep 2
    done

    echo -e "${RED}Redis failed to start${NC}"
    exit 1
}


# Start PostgreSQL database
start_database() {
    echo -e "${YELLOW}Starting PostgreSQL database on port $DB_PORT...${NC}"
    echo -e "${BLUE}Using port $DB_PORT to avoid conflicts with system PostgreSQL (port 5432)${NC}"
    
    # Stop and remove existing container if exists
    docker stop motorbike-dev-db 2>/dev/null || true
    docker rm motorbike-dev-db 2>/dev/null || true
    
    # Start fresh PostgreSQL container on non-standard port
    docker run -d \
        --name motorbike-dev-db \
        -e POSTGRES_DB="$DB_NAME" \
        -e POSTGRES_USER="$DB_USER" \
        -e POSTGRES_PASSWORD="$DB_PASSWORD" \
        -p "$DB_PORT:5432" \
        -v motorbike-dev-data:/var/lib/postgresql/data \
        postgres:15-alpine
    
    echo -e "${YELLOW}Waiting for database to be ready...${NC}"
    
    # Wait for database to be ready
    for i in {1..30}; do
        if docker exec motorbike-dev-db pg_isready -U "$DB_USER" -d "$DB_NAME" &> /dev/null; then
            echo -e "${GREEN}Database is ready!${NC}"
            return
        fi
        sleep 2
    done
    
    echo -e "${RED}Database failed to start${NC}"
    exit 1
}

# Build and start the application
start_application() {
    echo -e "${YELLOW}Building and starting application...${NC}"
    
    # Stop and remove existing app container
    docker stop motorbike-dev-app 2>/dev/null || true
    docker rm motorbike-dev-app 2>/dev/null || true
    
    # Check if we should use DockerHub image or build locally
    if [ "$USE_DOCKERHUB" = "true" ]; then
        echo -e "${BLUE}Using pre-built image from DockerHub${NC}"
        echo -e "${YELLOW}Pulling latest image from DockerHub...${NC}"
        docker pull $DOCKERHUB_IMAGE
        IMAGE_TO_USE=$DOCKERHUB_IMAGE
    else
        echo -e "${BLUE}Building image locally${NC}"
        
        # Check if we're in correct directory
        if [ ! -f "pom.xml" ]; then
            echo -e "${RED}pom.xml not found! Please run this script from MotorbikeSharingSystem_BE directory${NC}"
            exit 1
        fi
        
        # Create temporary Dockerfile for development (doesn't affect existing files)
        cat > .Dockerfile.dev << 'EOF'
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Install Maven
RUN apt-get update && apt-get install -y maven && apt-get clean

COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
COPY mvnw.cmd .
RUN chmod +x mvnw && mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update && apt-get install -y curl && apt-get clean
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENV JAVA_OPTS="-Xms256m -Xmx512m"
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
EOF
        
        # Build Docker image
        echo -e "${YELLOW}Building Docker image...${NC}"
        docker build -t motorbike-dev-app -f .Dockerfile.dev .
        
        # Remove temporary Dockerfile
        rm .Dockerfile.dev
        IMAGE_TO_USE="motorbike-dev-app"
    fi

    # Get container IP addresses for internal communication
    DB_IP=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' motorbike-dev-db)
    REDIS_IP=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' motorbike-dev-redis)
    
    # Start application container
    echo -e "${YELLOW}Starting application on port $APP_PORT...${NC}"
    echo -e "${BLUE}Using port $APP_PORT to avoid conflicts with other apps (port 8080)${NC}"
    
    docker run -d \
        --name motorbike-dev-app \
        -p "$APP_PORT:8080" \
        -e SPRING_DATASOURCE_URL="jdbc:postgresql://$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' motorbike-dev-db):5432/$DB_NAME" \
        -e SPRING_DATASOURCE_USERNAME="$DB_USER" \
        -e SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD" \
        -e SPRING_JPA_HIBERNATE_DDL_AUTO=update \
        -e SPRING_JPA_SHOW_SQL=true \
        -e SPRING_DATA_REDIS_HOST="$REDIS_IP" \
        -e SPRING_DATA_REDIS_PORT=6379 \
        -e SPRING_DATA_REDIS_PASSWORD="$REDIS_PASSWORD" \
        -e REDIS_PASSWORD="$REDIS_PASSWORD" \
        $IMAGE_TO_USE
    
    echo -e "${YELLOW}Waiting for application to start...${NC}"
    sleep 15
    
    # Check if application is ready
    for i in {1..30}; do
        if curl -s http://localhost:$APP_PORT/actuator/health &> /dev/null || curl -s http://localhost:$APP_PORT &> /dev/null; then
            echo -e "${GREEN}Application is ready!${NC}"
            return
        fi
        sleep 2
    done
    
    echo -e "${YELLOW}Application might still be starting up...${NC}"
}

# Show status and information
show_status() {
    echo ""
    echo -e "${GREEN}Development Environment Started Successfully!${NC}"
    echo "=============================================="
    echo ""
    echo -e "${BLUE}Database:${NC}"
    echo "  Host: localhost"
    echo "  Port: $DB_PORT"
    echo "  Database: $DB_NAME"
    echo "  Username: $DB_USER"
    echo "  Password: $DB_PASSWORD"
    echo ""
    echo -e "${BLUE}Redis Cache:${NC}"
    echo "  Host: localhost"
    echo "  Port: $REDIS_PORT"
    echo "  Password: $REDIS_PASSWORD"
    echo ""
    echo -e "${BLUE}Application:${NC}"
    echo "  URL: http://localhost:$APP_PORT"
    echo "  API Docs: http://localhost:$APP_PORT/swagger-ui.html"
    echo "  Health: http://localhost:$APP_PORT/actuator/health"
    echo ""
    echo -e "${BLUE}Useful Commands:${NC}"
    echo "  View app logs: docker logs motorbike-dev-app -f"
    echo "  View db logs: docker logs motorbike-dev-db -f"
    echo "  Stop everything: ./dev.sh stop"
    echo ""
    echo -e "${GREEN}Happy coding!${NC}"
}

# Stop all development containers
stop_services() {
    echo -e "${YELLOW}Stopping development environment...${NC}"
    
    # Stop containers
    docker stop motorbike-dev-app 2>/dev/null && echo -e "${GREEN}Application stopped${NC}" || echo -e "${YELLOW}Application not running${NC}"
    docker stop motorbike-dev-redis 2>/dev/null && echo -e "${GREEN}Redis stopped${NC}" || echo -e "${YELLOW}Redis not running${NC}"
    docker stop motorbike-dev-db 2>/dev/null && echo -e "${GREEN}Database stopped${NC}" || echo -e "${YELLOW}Database not running${NC}"
    
    # Remove containers
    docker rm motorbike-dev-app 2>/dev/null || true
    docker rm motorbike-dev-redis 2>/dev/null || true
    docker rm motorbike-dev-db 2>/dev/null || true
    
    # Ask about data removal
    echo ""
    echo -e "${YELLOW}Do you want to remove database data? This will delete everything! [y/N]:${NC}"
    read -r remove_data
    
    if [[ $remove_data =~ ^[Yy]$ ]]; then
        docker volume rm motorbike-dev-data 2>/dev/null && echo -e "${GREEN}Database data removed${NC}" || echo -e "${YELLOW}No data volume found${NC}"
        docker volume rm motorbike-dev-redis-data 2>/dev/null && echo -e "${GREEN}Redis data removed${NC}" || echo -e "${YELLOW}No Redis volume found${NC}"
        echo -e "${RED}All data deleted!${NC}"
    else
        echo -e "${GREEN}Database data preserved${NC}"
    fi
    
    # Clean up images
    echo -e "${YELLOW}Do you want to remove Docker images? [y/N]:${NC}"
    read -r remove_images
    
    if [[ $remove_images =~ ^[Yy]$ ]]; then
        docker rmi motorbike-dev-app 2>/dev/null && echo -e "${GREEN}App image removed${NC}" || true
        docker image prune -f > /dev/null && echo -e "${GREEN}Unused images cleaned up${NC}"
    fi
    
    echo -e "${GREEN}Development environment stopped${NC}"
}

# Show help
show_help() {
    echo "Motorbike Sharing System - Development Script"
    echo ""
    echo "Usage:"
    echo "  ./dev.sh                    Start development environment (build locally)"
    echo "  ./dev.sh start              Start development environment (build locally)"
    echo "  ./dev.sh stop               Stop development environment"
    echo "  ./dev.sh help               Show this help"
    echo "  USE_DOCKERHUB=true ./dev.sh Start using pre-built image from DockerHub"
    echo ""
    echo "Requirements:"
    echo "  - Docker Desktop installed and running"
    echo "  - Run from MotorbikeSharingSystem_BE directory"
    echo ""
    echo "Environment Variables:"
    echo "  USE_DOCKERHUB=true    Use pre-built image from DockerHub instead of building"
    echo ""
    echo "This script:"
    echo "  - Starts PostgreSQL database on port $DB_PORT"
    echo "  - Starts Redis cache on port $REDIS_PORT"
    echo "  - Starts Spring Boot application on port $APP_PORT"
    echo "  - Does not modify any existing project files"
    echo "  - Creates separate containers for development"
    echo "  - Preserves data between restarts"
    echo "  - Works on any machine with Docker"
    echo ""
    echo "Examples:"
    echo "  ./dev.sh                              # Build and run locally"
    echo "  USE_DOCKERHUB=true ./dev.sh start     # Use DockerHub image"
}

# Main function
main() {
    case "${1:-start}" in
        "start"|"")
            check_docker
            start_redis
            start_database
            start_application
            show_status
            ;;
        "stop")
            stop_services
            ;;
        "help"|"-h"|"--help")
            show_help
            ;;
        *)
            echo -e "${RED}Unknown command: $1${NC}"
            show_help
            exit 1
            ;;
    esac
}

# Handle Ctrl+C
trap 'echo -e "\n${YELLOW}Interrupted! Run ./dev.sh stop to clean up${NC}"; exit 1' INT

# Run main function
main "$@"