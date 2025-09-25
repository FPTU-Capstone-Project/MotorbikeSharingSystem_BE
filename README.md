# Motorbike Sharing System Backend

A Spring Boot backend application for motorbike sharing system with PostgreSQL database. This guide will help you run the project locally for development, regardless of your technical background.

---

## **Table of Contents**

- [Architecture](#architecture)
  - [System Context (C4 Level 1)](docs/architecture/C4-Context.puml) - High-level view of system actors and external integrations
  - [Container Architecture (C4 Level 2)](docs/architecture/C4-Containers.puml) - Applications, databases, and infrastructure components
  - [Backend Components](docs/architecture/Backend-Components.puml) - Internal component structure of the backend API
  - [Package Structure](docs/architecture/Backend-Packages.puml) - Layered package organization and module boundaries
- [Setup Guide](#choose-your-setup-path)
- [Development](#development-setup)
- [API Documentation](#api-documentation)
- [Project Structure](#project-structure)
- [Support](#support)

---

## **Architecture**

The MSSUS platform follows a microservices-ready architecture with clean separation of concerns. For detailed architecture documentation:

- **[System Context Diagram](docs/architecture/C4-Context.puml)** - Shows how MSSUS interacts with users and external systems (payment gateways, maps, notifications)
- **[Container Diagram](docs/architecture/C4-Containers.puml)** - Details the mobile apps, web admin, backend API, database, cache, and message queue components
- **[Backend Components Diagram](docs/architecture/Backend-Components.puml)** - Illustrates controllers, services, repositories, and adapters within the Spring Boot backend
- **[Package Structure Diagram](docs/architecture/Backend-Packages.puml)** - Presents the layered package organization following Domain-Driven Design principles

All diagrams are in PlantUML format and can be rendered using PlantUML tools or IDE plugins.

---

## **Choose Your Setup Path**

### **For Non-Technical Users or First-Time Setup**
👇 **Follow the [Complete Step-by-Step Guide](#complete-setup-guide-step-by-step)** below
- Detailed instructions from installing software to running the app
- Works for Mac and Windows
- No prior knowledge required

### **For Developers (Quick Start)**
If you already have Docker and know how to use bash/terminal:

```bash
# Clone/download project and navigate to directory
cd MotorbikeSharingSystem_BE

# Make script executable and run
chmod +x dev.sh
./dev.sh

# Access app at http://localhost:8081
# Stop with: ./dev.sh stop
```

**Requirements:** Docker Desktop running, bash terminal (Git Bash on Windows)

---

## Complete Setup Guide (Step by Step)

Follow these steps exactly - they work for both beginners and experienced developers.

### Step 1: Download and Install Required Software

**You need these two things:**

1. **Docker Desktop** - Download from https://www.docker.com/products/docker-desktop
   - Install Docker Desktop
   - Start Docker Desktop application
   - Wait for it to show "Docker Desktop is running" (green icon)

2. **VS Code** - Download from https://code.visualstudio.com/
   - Install VS Code
   - This will be where you open the project

### Step 2: Get the Project Code

1. **Download or Clone** this project to your computer
2. **Remember the location** where you saved it (e.g., Desktop, Documents, etc.)

### Step 3: Open Project in VS Code

1. **Open VS Code**
2. Click **"File"** → **"Open Folder"** (Mac) or **"Open Folder"** (Windows)
3. **Navigate to** and **select** the `MotorbikeSharingSystem_BE` folder
4. Click **"Open"** or **"Select Folder"**

### Step 4: Open the Correct Terminal in VS Code

This is the **most important step** - you need the right type of terminal:

#### For Mac Users:
1. In VS Code, press **`Ctrl + ` `** (backtick key, usually above Tab key)
   - Or go to menu: **View → Terminal**
2. The terminal will open at the bottom of VS Code
3. Make sure it shows something like:
   ```
   username@MacBook-Pro MotorbikeSharingSystem_BE %
   ```
4. **This is correct** - Mac uses bash/zsh terminal by default

#### For Windows Users:
1. In VS Code, press **`Ctrl + ` `** (backtick key, usually above Tab key)
   - Or go to menu: **View → Terminal**
2. **Check what terminal type opened** - look at the terminal tab name:
   
   **If you see "PowerShell" or "Command Prompt":**
   - Click the **dropdown arrow** next to the "+" button in terminal
   - Select **"Git Bash"** (if available) or **"Bash"**
   - If Git Bash is not available, install **Git for Windows** first from https://git-scm.com/download/win
   
   **You want to see "Git Bash" or "Bash"**
   
3. The correct terminal should show something like:
   ```
   username@DESKTOP-XXX MINGW64 ~/MotorbikeSharingSystem_BE
   $
   ```

#### If Git Bash is Not Available (Windows Only):
1. **Install Git for Windows**:
   - Go to https://git-scm.com/download/win
   - Download and install (use default settings)
   - Restart VS Code
2. **Try opening terminal again** and select "Git Bash"

### Step 5: Verify You're in the Right Directory

In your terminal (Mac or Windows), type this command and press Enter:

```bash
ls
```

**You should see these files:**
- `dev.sh`
- `pom.xml`
- `src/`
- `README.md`
- Other project files

**If you don't see these files:**
- You're in the wrong directory
- Type: `cd MotorbikeSharingSystem_BE` and press Enter
- Try `ls` again

### Step 6: Make the Script Executable (Required)

In your terminal, run this command:

```bash
chmod +x dev.sh
```

This makes the script runnable on your system.

### Step 7: Start the Application

Now run the magic command:

```bash
./dev.sh
```

**What you'll see:**
- Lots of text scrolling (this is normal)
- Messages about Docker containers starting
- Eventually: "Application is ready!"

**When it's ready:**
- Your application will be running at http://localhost:8081
- Keep the terminal open - don't close it

### Step 8: Test It's Working

1. **Open your web browser**
2. **Go to:** http://localhost:8081
3. **You should see** the application running (might be a JSON response or API page)
4. **Also try:** http://localhost:8081/swagger-ui.html for API documentation

### Step 9: Stop the Application (When Done)

When you're finished working:

```bash
./dev.sh stop
```

This safely stops everything and cleans up.

## Quick Start (After Initial Setup)

Once you've done the setup above once, daily usage is simple:

```bash
# Start your development day (in VS Code terminal)
./dev.sh

# Stop when done
./dev.sh stop
```

## Visual Guide Summary

**What success looks like:**

1. **VS Code is open** with your project folder
2. **Terminal is open** at the bottom (Git Bash on Windows, Terminal on Mac)
3. **Terminal shows** the project directory: `~/MotorbikeSharingSystem_BE`
4. **Docker Desktop** shows green "running" status
5. **After running `./dev.sh`**: Browser shows app at http://localhost:8081

**Signs something is wrong:**
- Terminal says "PowerShell" (Windows) - switch to Git Bash
- `ls` command doesn't show `dev.sh` file - wrong directory
- `./dev.sh` says "permission denied" - run `chmod +x dev.sh` first
- Docker errors - make sure Docker Desktop is running

---

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

## Common Issues & Solutions

### Terminal Problems (Most Common)

<details>
<summary>Windows: "bash: command not found" or "./dev.sh: No such file or directory"</summary>

**Problem:** You're using PowerShell or Command Prompt instead of Bash

**Solution:**
1. **In VS Code terminal**, look at the terminal tab name
2. If it says "PowerShell" or "cmd":
   - Click the **dropdown arrow** (⌄) next to the "+" button
   - Select **"Git Bash"**
3. **If Git Bash is not in the list:**
   - Install Git for Windows: https://git-scm.com/download/win
   - Use default installation settings
   - Restart VS Code
   - Try again

**Visual Check - You should see:**
```bash
username@DESKTOP-XXX MINGW64 ~/MotorbikeSharingSystem_BE
$ 
```
**Not this (PowerShell):**
```powershell
PS C:\Users\username\MotorbikeSharingSystem_BE>
```

</details>

<details>
<summary>Windows: "Permission denied" error</summary>

**Problem:** Script doesn't have execute permissions

**Solution:**
1. **Make sure you're in Git Bash** (not PowerShell)
2. Run this command first:
   ```bash
   chmod +x dev.sh
   ```
3. Then try:
   ```bash
   ./dev.sh
   ```

</details>

<details>
<summary>Mac: "zsh: permission denied: ./dev.sh"</summary>

**Problem:** Script doesn't have execute permissions

**Solution:**
```bash
chmod +x dev.sh
./dev.sh
```

</details>

<details>
<summary>Both Platforms: "No such file or directory"</summary>

**Problem:** You're in the wrong folder

**Visual Check - Run this command:**
```bash
ls
```

**You should see:**
- dev.sh
- pom.xml
- src/
- README.md

**If you don't see these files:**
```bash
# Navigate to the correct directory
cd MotorbikeSharingSystem_BE
# Check again
ls
```

</details>

<details>
<summary>VS Code Terminal Not Opening</summary>

**Multiple ways to open terminal:**
1. **Keyboard:** `Ctrl + ` ` (backtick key, above Tab)
2. **Menu:** View → Terminal  
3. **Command Palette:** `Ctrl+Shift+P` → type "Terminal: Create New Terminal"

</details>

### Application Issues

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