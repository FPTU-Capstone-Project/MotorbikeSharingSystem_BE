# Motorbike Sharing System Backend

A Spring Boot backend application for motorbike sharing system with PostgreSQL database. This guide will help you run the project locally for development, regardless of your technical background.

## Quick Start

```bash
# Navigate to project directory
cd MotorbikeSharingSystem_BE

# Start development environment
chmod +x dev.sh
# Then
./dev.sh

# Stop when you're done
./dev.sh stop
```

That's it! Your application will be available at http://localhost:8081

## Prerequisites

You only need one thing installed on your computer:

**Docker Desktop** - Download from https://www.docker.com/products/docker-desktop

- Install Docker Desktop
- Start Docker Desktop (wait for it to show "running")
- That's all you need!

**You don't need to install:**
- Java
- Maven
- PostgreSQL
- Any other development tools

Everything runs inside Docker containers.

## Development Setup

### Method 1: One Command Setup (Recommended)

This is the easiest way to get started:

```bash
./dev.sh
```

**What this does:**
- Checks if Docker is running
- Starts PostgreSQL database in a container
- Builds and starts your Spring Boot application
- Shows you all the URLs and connection info

**To stop everything:**
```bash
./dev.sh stop
```

<details>
<summary>Method 2: Step by Step (if you want more control)</summary>

```bash
# Start the development environment
./dev.sh start

# View application logs
docker logs motorbike-dev-app -f

# View database logs  
docker logs motorbike-dev-db -f

# Stop everything
./dev.sh stop
```

</details>

<details>
<summary>Method 3: Manual Docker Commands (for advanced users)</summary>

If you want to understand what's happening behind the scenes:

```bash
# Start database
docker run -d --name motorbike-dev-db \
  -e POSTGRES_DB=motorbike_sharing_db \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=Password@123 \
  -p 5432:5432 \
  postgres:15-alpine

# Build application (run from project directory)
docker build -t motorbike-dev-app .

# Start application
docker run -d --name motorbike-dev-app \
  -p 8081:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://[DB_IP]:5432/motorbike_sharing_db \
  motorbike-dev-app
```

</details>

## Available Services

Once started, you can access:

| Service | URL | Description |
|---------|-----|-------------|
| **Main Application** | http://localhost:8081 | Your Spring Boot API |
| **API Documentation** | http://localhost:8081/swagger-ui.html | Interactive API docs |
| **Health Check** | http://localhost:8081/actuator/health | Check if app is running |

### Database Connection

| Setting | Value |
|---------|-------|
| **Host** | localhost |
| **Port** | 5433 |
| **Database** | motorbike_sharing_db |
| **Username** | postgres |
| **Password** | Password@123 |

## Development Workflow

### Daily Development

```bash
# Start your development day
./dev.sh

# Make your code changes
# The application will automatically rebuild when you restart

# View logs if something goes wrong
docker logs motorbike-dev-app -f

# End your development day
./dev.sh stop
```

### Making Code Changes

When you change your code:

1. Stop the current application: `./dev.sh stop`
2. Start again: `./dev.sh`
3. Your changes will be included in the new build

### Database Management

Your database data is automatically saved between restarts.

<details>
<summary>Database Commands</summary>

```bash
# Connect to database directly
docker exec -it motorbike-dev-db psql -U postgres -d motorbike_sharing_db

# View database logs
docker logs motorbike-dev-db -f

# Reset database (delete all data)
./dev.sh stop
# When asked about removing data, answer 'y'
```

</details>

## Troubleshooting

### Common Issues

<details>
<summary>Docker is not running</summary>

**Error:** `Docker not found!` or `Docker not running!`

**Solution:**
1. Install Docker Desktop if not installed
2. Start Docker Desktop application
3. Wait for Docker to show "running" status
4. Try the script again

</details>

<details>
<summary>Port already in use</summary>

**Error:** `Port 8081 is already in use`

**Solution:**
```bash
# Find what's using port 8081
lsof -i :8081

# Stop the other application, then try again
./dev.sh
```

</details>

<details>
<summary>Application won't start</summary>

**Steps to debug:**
```bash
# Check application logs
docker logs motorbike-dev-app -f

# Check database logs
docker logs motorbike-dev-db -f

# Try restarting everything
./dev.sh stop
./dev.sh
```

</details>

<details>
<summary>Out of disk space</summary>

**Clean up Docker:**
```bash
# Remove unused Docker data
docker system prune -a

# Remove development containers and start fresh
./dev.sh stop
# Answer 'y' to remove data and images
./dev.sh
```

</details>

### Getting Help

```bash
# Show help and available commands
./dev.sh help

# Check what containers are running
docker ps

# Check Docker status
docker info
```

## Project Structure

```
MotorbikeSharingSystem_BE/
├── src/                    # Java source code
│   ├── main/
│   │   ├── java/          # Application code
│   │   └── resources/     # Configuration files
│   └── test/              # Test files
├── pom.xml                # Maven configuration
├── dev.sh                 # Development script (this is what you use)
└── README.md              # This file
```

## Configuration

The development environment uses these default settings:

- **Database:** PostgreSQL 15
- **Java Version:** 17
- **Application Port:** 8081
- **Database Port:** 5433
- **Memory:** 512MB for application

All settings are optimized for local development and completely separate from any production configuration.

## API Documentation

### Swagger UI

Interactive API documentation is available at:
- **URL:** http://localhost:8081/swagger-ui.html
- **Features:** Test APIs directly from the browser

### Main API Endpoints

| Endpoint | Method | Description |
|----------|---------|-------------|
| `/api/v1/auth/login` | POST | User login |
| `/api/v1/auth/register` | POST | User registration |
| `/api/v1/motorbikes` | GET | List motorbikes |
| `/api/v1/bookings` | POST | Create booking |
| `/actuator/health` | GET | Health check |

## What This Script Does

The `dev.sh` script is designed to work on any computer with Docker installed:

- **Safe:** Doesn't modify any existing project files
- **Isolated:** Creates separate containers that don't affect anything else
- **Automatic:** Handles all the Docker complexity for you
- **Smart:** Checks for common issues and provides helpful error messages
- **Clean:** Easy to remove everything when you're done

## Requirements Summary

**What you need:**
- Docker Desktop installed and running
- This project downloaded to your computer

**What you don't need:**
- Java development experience
- Database knowledge
- Docker expertise
- Any other software installed

## Support

If you run into issues:

1. Make sure Docker Desktop is running
2. Try `./dev.sh stop` then `./dev.sh` to restart
3. Check the troubleshooting section above
4. Look at the logs with `docker logs motorbike-dev-app -f`

The development environment is designed to be simple and foolproof. If something doesn't work, it's likely a common issue with a simple solution in the troubleshooting section.