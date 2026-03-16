#!/usr/bin/env bash
# set-manifest-locations.sh
#
# PURPOSE
#   Replaces the REPLACE_ME placeholder URLs in packageManifest.json with
#   real GitHub raw content URLs pointing to your repository.
#
# WHEN TO RUN
#   Run this ONCE after you have pushed the repository to GitHub for the
#   first time.  The updated manifest is then committed and pushed so that
#   Hubitat Package Manager (HPM) can resolve the driver, app, licence and
#   documentation files directly from your repo.
#
# USAGE
#   Must be run from the repository root directory (where packageManifest.json
#   lives):
#
#     cd /path/to/your/repo
#     chmod +x scripts/set-manifest-locations.sh
#     ./scripts/set-manifest-locations.sh <github-owner> <github-repo>
#
#   Arguments:
#     github-owner   Your GitHub username or organisation name.
#     github-repo    The name of the repository on GitHub.
#
# EXAMPLE
#   ./scripts/set-manifest-locations.sh gerard hubitat-sonoff-orb
#
#   This replaces every occurrence of:
#     https://raw.githubusercontent.com/REPLACE_ME/REPLACE_ME/main
#   with:
#     https://raw.githubusercontent.com/gerard/hubitat-sonoff-orb/main
#
#   Affected fields in packageManifest.json:
#     - licenseFile
#     - documentationLink
#     - drivers[].location
#     - apps[].location
#
# AFTER RUNNING
#   Commit and push the updated manifest:
#
#     git add packageManifest.json
#     git commit -m "Set HPM manifest URLs"
#     git push
#
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <github-owner> <github-repo>"
  echo "Example: $0 gerard hubitat-sonoff-orb"
  exit 1
fi

owner="$1"
repo="$2"
manifest="packageManifest.json"

if [[ ! -f "$manifest" ]]; then
  echo "Error: $manifest not found in current directory"
  exit 1
fi

base="https://raw.githubusercontent.com/${owner}/${repo}/main"

sed -i "s|https://raw.githubusercontent.com/REPLACE_ME/REPLACE_ME/main|${base}|g" "$manifest"

echo "Updated packageManifest.json with: ${base}"
