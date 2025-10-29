#!/usr/bin/env bash

# ENV declaring whether this script runs inside CI or not
CI=${CI:-0}

if [[ $CI -eq 1 ]]; then
  set -e
fi

# Run update script for Aya's Gradle deps
# Inner command always produces a Nix store path so quoting is unnecessary on Unix systems
# shellcheck disable=SC2046
eval $(nix build .#Aya.mitmCache.updateScript --no-link --print-out-paths)

# Check whether nix/deps.json is modified or not
git diff --exit-code nix/deps.json || \
  if [[ $CI -eq 1 ]]; then
    printf "ERROR: Outdated nix/deps.json detected.
            Please run .github/workflows/check-nix-gradle.lock.sh and commit changes."
  fi
