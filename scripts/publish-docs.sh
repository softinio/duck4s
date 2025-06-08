#!/bin/bash

# Documentation publishing script for duck4s
# Usage: ./publish-docs.sh [version]
# Examples:
#   ./publish-docs.sh          # Uses latest git tag version
#   ./publish-docs.sh 0.1.0    # Uses specified version
# This script manually triggers documentation deployment to GitHub Pages

set -euo pipefail

# Get version from argument or latest git tag
if [ $# -eq 0 ]; then
    # Get latest tag
    LATEST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "")
    if [ -z "$LATEST_TAG" ]; then
        echo "Error: No git tags found and no version specified"
        echo "Usage: $0 [version]"
        exit 1
    fi
    VERSION=${LATEST_TAG#v}  # Remove 'v' prefix if present
    echo "Using latest release version: $VERSION"
else
    VERSION=$1
    echo "Using specified version: $VERSION"
fi

# Check if we're on main branch
CURRENT_BRANCH=$(git branch --show-current)
if [ "$CURRENT_BRANCH" != "main" ]; then
    echo "Error: Documentation must be published from the main branch"
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

# Check if nix is available
if ! command -v nix &> /dev/null; then
    echo "Error: Nix is not installed. Please install Nix first."
    exit 1
fi

# Generate documentation
echo "Generating documentation with version $VERSION..."
DUCK4S_DOC_VERSION="$VERSION" nix develop --command mill 'duck4s[3.7.0].docJar'

# Check if documentation was generated
if [ ! -d "out/duck4s/3.7.0/docJar.dest/javadoc" ]; then
    echo "Error: Documentation generation failed"
    exit 1
fi

# Create temporary directory for docs
TEMP_DIR=$(mktemp -d)
echo "Preparing documentation in $TEMP_DIR..."

# Copy documentation to temp directory
cp -r out/duck4s/3.7.0/docJar.dest/javadoc/* "$TEMP_DIR/"

# Switch to gh-pages branch (create if doesn't exist)
echo "Switching to gh-pages branch..."
if git show-ref --verify --quiet refs/heads/gh-pages; then
    git checkout gh-pages
else
    git checkout --orphan gh-pages
    git rm -rf .
fi

# Clean current directory
git rm -rf . 2>/dev/null || true

# Copy documentation from temp directory
cp -r "$TEMP_DIR"/* .

# Add .nojekyll file to prevent Jekyll processing
touch .nojekyll

# Commit and push
echo "Committing documentation..."
git add -A
git commit -m "Update documentation for v$VERSION - $(date '+%Y-%m-%d %H:%M:%S')"

echo "Pushing to GitHub Pages..."
git push origin gh-pages --force

# Switch back to main branch
git checkout main

# Clean up
rm -rf "$TEMP_DIR"

echo "✅ Documentation published successfully!"
echo ""
echo "The documentation will be available at:"
echo "https://softinio.github.io/duck4s/"
echo ""
echo "Note: It may take a few minutes for GitHub Pages to update."
