#!/bin/bash

# Release script for duck4s
# Usage: ./release.sh [version]
# Example: ./release.sh 0.1.0

set -euo pipefail

# Check if version is provided
if [ $# -eq 0 ]; then
    echo "Error: Please provide a version number"
    echo "Usage: $0 <version>"
    echo "Example: $0 0.1.0"
    exit 1
fi

VERSION=$1
TAG="v${VERSION}"

# Validate version format
if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9]+)?$ ]]; then
    echo "Error: Invalid version format. Use semantic versioning (e.g., 1.0.0 or 1.0.0-RC1)"
    exit 1
fi

# Check if we're on main branch
CURRENT_BRANCH=$(git branch --show-current)
if [ "$CURRENT_BRANCH" != "main" ]; then
    echo "Error: Releases must be created from the main branch"
    echo "Current branch: $CURRENT_BRANCH"
    exit 1
fi

# Check for uncommitted changes
if ! git diff-index --quiet HEAD --; then
    echo "Error: You have uncommitted changes. Please commit or stash them first."
    exit 1
fi

# Pull latest changes
echo "Pulling latest changes from origin..."
git pull origin main

# Check if tag already exists
if git rev-parse "$TAG" >/dev/null 2>&1; then
    echo "Error: Tag $TAG already exists"
    exit 1
fi

# Create annotated tag
echo "Creating tag $TAG..."
git tag -a "$TAG" -m "Release $VERSION"

# Push tag to trigger release workflow
echo "Pushing tag to GitHub..."
git push origin "$TAG"

echo "✅ Release $VERSION initiated!"
echo ""
echo "The release workflow will:"
echo "1. Run CI tests"
echo "2. Publish artifacts to Maven Central"
echo "3. Create a GitHub release"
echo ""
echo "Monitor progress at: https://github.com/softinio/duck4s/actions"