#!/bin/bash
set -e

CHART_DIR="examples/helm"

if [ -z "$GITHUB_REPOSITORY" ]; then
    GIT_REMOTE=$(git config --get remote.origin.url 2>/dev/null || echo "")
    if [[ $GIT_REMOTE =~ github.com[:/]([^/]+)/([^/.]+) ]]; then
        ORG_NAME="${BASH_REMATCH[1]}"
        REPO_NAME="${BASH_REMATCH[2]}"
    else
        ORG_NAME="lgu-se-internal"
        REPO_NAME="TeaStore"
        echo "Warning: Using default ORG_NAME and REPO_NAME"
    fi
else
    ORG_NAME=$(echo "$GITHUB_REPOSITORY" | cut -d'/' -f1 | tr '[:upper:]' '[:lower:]')
    REPO_NAME=$(echo "$GITHUB_REPOSITORY" | cut -d'/' -f2)
fi

ORG_NAME_LOWER=$(echo "$ORG_NAME" | tr '[:upper:]' '[:lower:]')
REPO_URL="https://${ORG_NAME_LOWER}.github.io/${REPO_NAME}"

echo "Target Repo URL: $REPO_URL"

mkdir -p .deploy
helm package "$CHART_DIR" -d .deploy

if [ -f .deploy/index.yaml ]; then
    helm repo index .deploy --url "$REPO_URL" --merge .deploy/index.yaml
else
    helm repo index .deploy --url "$REPO_URL"
fi

echo "Helm chart packaged and index updated in .deploy/"
ls -la .deploy/
