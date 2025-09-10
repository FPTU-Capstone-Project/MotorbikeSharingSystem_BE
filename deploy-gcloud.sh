#!/bin/bash

# Google Cloud deployment script for MotorbikeSharingSystem_BE
set -e

# Configuration
PROJECT_ID="certain-acre-469011-m3"
SERVICE_NAME="motorbike-sharing-backend"
REGION="asia-southeast1"
IMAGE_NAME="gcr.io/$PROJECT_ID/$SERVICE_NAME"

echo "🚀 Starting deployment to Google Cloud..."

# Check if gcloud is installed
if ! command -v gcloud &> /dev/null; then
    echo "❌ gcloud CLI is not installed. Please install it first:"
    echo "   https://cloud.google.com/sdk/docs/install"
    exit 1
fi

# Check if user is authenticated
if ! gcloud auth list --filter=status:ACTIVE --format="value(account)" | head -n 1 > /dev/null; then
    echo "❌ Not authenticated with Google Cloud. Please run:"
    echo "   gcloud auth login"
    exit 1
fi

# Set the project
echo "📝 Setting project to $PROJECT_ID..."
gcloud config set project $PROJECT_ID

# Enable required APIs
echo "🔧 Enabling required APIs..."
gcloud services enable cloudbuild.googleapis.com
gcloud services enable run.googleapis.com
gcloud services enable containerregistry.googleapis.com

# Build and deploy using Cloud Build
echo "🏗️  Building and deploying with Cloud Build..."
gcloud builds submit --config cloudbuild.yaml

echo "✅ Deployment completed!"
echo "🌐 Your service should be available at:"
gcloud run services describe $SERVICE_NAME --region=$REGION --format="value(status.url)"

echo ""
echo "📋 Next steps:"
echo "1. Update your domain configuration"
echo "2. Set up custom domain if needed"
echo "3. Configure environment variables in Cloud Run console"
echo "4. Set up monitoring and logging"