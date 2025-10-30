#!/usr/bin/env bash

# CI runners should exit immediately on error
if [[ -n $CI ]]; then
  set -e
fi

# Run update script for Aya's Gradle deps
# Inner command always produces a Nix store path so quoting is unnecessary on Unix systems
# shellcheck disable=SC2046
eval $(nix build .#Aya.mitmCache.updateScript --no-link --print-out-paths)

# Check whether nix/deps.json is modified or not
if ! git diff --quiet nix/deps.json; then
  if [[ -n $CI ]]; then
    printf "ERROR: Outdated nix/deps.json detected.\n"
    printf "Please run .github/workflows/check-nix-gradle-lock.sh and commit changes.\n"
    exit 1
  fi
fi


