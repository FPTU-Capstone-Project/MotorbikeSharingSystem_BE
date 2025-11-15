# CI/CD Quick Reference

Automated Docker image builds for backend repository.

## Setup (One-Time)

1. Create DockerHub token: https://hub.docker.com → Security → New Access Token
2. Add to GitHub: Repository Settings → Secrets → `DOCKERHUB_TOKEN`

Done! Push to main triggers automatic build.

---

## Workflows

### Backend CI (Main)
- Runs tests on every push/PR
- Builds and pushes Docker image on main push
- File: `.github/workflows/backend-ci.yml`

### Docker Build (Quick)
- Just builds and pushes (no tests)
- Can trigger manually
- File: `.github/workflows/docker-build-push.yml`

---

## Usage

```bash
# Normal development
git push origin main
# Automatically: tests → build → push to DockerHub

# Manual trigger
# GitHub → Actions → Docker Build and Push → Run workflow
```

---

## Pull Images

```bash
# Latest
docker pull khoatdse172986/motorbike-backend:latest

# Update local
./update-and-restart.sh
```

---

## Tags Created

- `latest` - Always newest
- `20241116-143000` - Timestamp
- `abc1234` - Commit SHA

---

## Files Structure

```
backend/
  .github/
    workflows/
      backend-ci.yml          # Main CI/CD pipeline
      docker-build-push.yml   # Quick build option
    CICD_SETUP.md             # Full documentation
    CICD_QUICKREF.md          # This file
  
  build-push-deploy.sh        # Local build script
  update-and-restart.sh       # Update from DockerHub
  push-to-dockerhub.sh        # Manual push
  
  DOCKER_DEPLOYMENT.md        # Docker guide
  DOCKER_DEPLOYMENT_SUMMARY.md # Changes summary
```

---

## Check Status

- GitHub Actions: https://github.com/FPTU-Capstone-Project/MotorbikeSharingSystem_BE/actions
- DockerHub: https://hub.docker.com/r/khoatdse172986/motorbike-backend

---

## Troubleshooting

**Build fails?**
```bash
# Check secret exists
GitHub Settings → Secrets → DOCKERHUB_TOKEN

# Test locally
./mvnw clean test
docker build -t test .
```

---

Full documentation: `.github/CICD_SETUP.md`
