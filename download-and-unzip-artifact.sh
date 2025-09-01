#!/bin/bash

# Usage: ./download-and-unzip-artifact.sh <GITHUB_TOKEN> <RUN_URL>
set -e

TOKEN="$1"
RUN_URL="$2"

if [[ -z "$TOKEN" || -z "$RUN_URL" ]]; then
  echo "Usage: $0 <GITHUB_TOKEN> <RUN_URL>"
  exit 1
fi

# Extract owner, repo, and run_id from URL
if [[ "$RUN_URL" =~ github.com/([^/]+)/([^/]+)/actions/runs/([0-9]+) ]]; then
  OWNER="${BASH_REMATCH[1]}"
  REPO="${BASH_REMATCH[2]}"
  RUN_ID="${BASH_REMATCH[3]}"
else
  echo "Invalid RUN_URL format."
  exit 2
fi

# Get artifact info
ARTIFACTS_JSON=$(curl -s -H "Authorization: token $TOKEN" \
  -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/$OWNER/$REPO/actions/runs/$RUN_ID/artifacts")

ARTIFACT_ID=$(echo "$ARTIFACTS_JSON" | grep '"id":' | head -n1 | awk '{print $2}' | tr -d ',')
ARTIFACT_NAME=$(echo "$ARTIFACTS_JSON" | grep '"name":' | head -n1 | cut -d '"' -f4)

if [[ -z "$ARTIFACT_ID" ]]; then
  echo "No artifacts found for this run."
  exit 3
fi

ZIP_FILE="${ARTIFACT_NAME}.zip"
DEST_DIR="build/libs"

echo "Downloading artifact '$ARTIFACT_NAME' (ID: $ARTIFACT_ID)..."
curl -L -H "Authorization: token $TOKEN" \
  -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/$OWNER/$REPO/actions/artifacts/$ARTIFACT_ID/zip" \
  -o "$ZIP_FILE"

echo "Unzipping to $DEST_DIR..."
mkdir -p "$DEST_DIR"
unzip -j -o "$ZIP_FILE" "*.jar" -x "*-plain.jar" -d "$DEST_DIR"

echo "Cleaning up..."
rm "$ZIP_FILE"

echo "Done. Artifact extracted to $DEST_DIR"
