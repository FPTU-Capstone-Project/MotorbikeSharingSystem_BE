# GitHub Actions CI/CD Setup

This backend repository now has automated CI/CD pipelines using GitHub Actions.

## What's Automated

When you push to main branch:
1. Run Maven tests automatically
2. Build Docker image automatically
3. Push to DockerHub automatically
4. Tag with timestamp and commit SHA

---

## Quick Setup (One-Time, 2 minutes)

### Step 1: Create DockerHub Access Token

1. Go to https://hub.docker.com
2. Click Profile → Account Settings → Security
3. Click "New Access Token"
   - Name: `GitHub Actions`
   - Permissions: `Read, Write, Delete`
4. Click "Generate" and copy the token

### Step 2: Add Secret to GitHub

1. Go to https://github.com/FPTU-Capstone-Project/MotorbikeSharingSystem_BE
2. Click Settings → Secrets and variables → Actions
3. Click "New repository secret"
   - Name: `DOCKERHUB_TOKEN`
   - Value: Paste the token from Step 1
4. Click "Add secret"

**Done!** CI/CD is now active.

---

## How It Works

### Workflow 1: Backend CI (Recommended)
**File:** `.github/workflows/backend-ci.yml`

**Triggers:**
- Push to main or develop branch
- Pull requests to main or develop

**Steps:**
1. Checkout code
2. Setup Java 17
3. Run Maven tests
4. Build Docker image (only on main push)
5. Push to DockerHub (only on main push)

**Build time:** 5-7 minutes

---

### Workflow 2: Docker Build and Push
**File:** `.github/workflows/docker-build-push.yml`

**Triggers:**
- Push to main branch
- Manual trigger (workflow_dispatch)

**Steps:**
1. Checkout code
2. Build Docker image
3. Push to DockerHub with multiple tags

**Build time:** 3-5 minutes (faster, no tests)

---

## Usage

### Normal Development

```bash
# Make your changes
git add .
git commit -m "Add new feature"
git push origin main

# GitHub Actions automatically:
# - Runs tests
# - Builds Docker image
# - Pushes to DockerHub
```

### Manual Trigger

1. Go to GitHub repository
2. Click "Actions" tab
3. Select "Docker Build and Push"
4. Click "Run workflow"
5. Select branch and run

---

## Image Tags

Every successful build creates 3 tags:

| Tag | Example | Purpose |
|-----|---------|---------|
| `latest` | `khoatdse172986/motorbike-backend:latest` | Always newest |
| `timestamp` | `khoatdse172986/motorbike-backend:20241116-143000` | Version control |
| `commit` | `khoatdse172986/motorbike-backend:abc1234` | Git reference |

---

## Pull Images

```bash
# Latest version
docker pull khoatdse172986/motorbike-backend:latest

# Specific version
docker pull khoatdse172986/motorbike-backend:20241116-143000

# Update local environment
cd backend
./update-and-restart.sh
```

---

## View Build Status

### GitHub UI
1. Repository → Actions tab
2. See all workflow runs
3. Click on a run to see details

### Add Status Badge to README
```markdown
![CI](https://github.com/FPTU-Capstone-Project/MotorbikeSharingSystem_BE/workflows/Backend%20CI/badge.svg)
```

---

## Troubleshooting

### Build Fails: Authentication Error
```
Error: unauthorized: authentication required
```

**Solution:**
1. Verify `DOCKERHUB_TOKEN` secret exists in GitHub Settings
2. Check token hasn't expired
3. Regenerate token if needed

---

### Build Fails: Tests Error
```
Error: There are test failures
```

**Solution:**
```bash
# Run tests locally first
./mvnw clean test

# Fix failing tests
# Commit and push again
```

---

### Image Not Updating
```bash
# Force pull without cache
docker pull khoatdse172986/motorbike-backend:latest --no-cache
```

---

## Costs

**Free for public repositories:**
- GitHub Actions: Unlimited minutes
- DockerHub: Unlimited public repositories
- Total: $0

---

## Workflow Files Location

```
backend/
  .github/
    workflows/
      backend-ci.yml           # Main workflow with tests
      docker-build-push.yml    # Quick build without tests
```

---

## Summary

**Before:**
- Manual build: 10 minutes
- Manual push: 2 minutes
- Manual testing: 5 minutes
- Total: 17 minutes per deployment

**After:**
- Just push code: 1 minute
- Everything else automatic
- Total: 1 minute (7 minutes build time in background)

**Time saved:** 16 minutes per deployment

---

For more details, see:
- Docker deployment: `DOCKER_DEPLOYMENT.md`
- Changes summary: `DOCKER_DEPLOYMENT_SUMMARY.md`

Last Updated: November 16, 2025
