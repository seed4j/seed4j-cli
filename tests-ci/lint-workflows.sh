#!/usr/bin/env bash
set -euo pipefail

script_directory="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd -- "$script_directory/.." && pwd)"
actionlint_directory="$(mktemp -d "${TMPDIR:-/tmp}/seed4j-actionlint.XXXXXX")"
trap 'rm -rf -- "$actionlint_directory"' EXIT

archive="$actionlint_directory/actionlint.tar.gz"
curl --fail --location --silent --show-error \
  --output "$archive" \
  https://github.com/rhysd/actionlint/releases/download/v1.7.12/actionlint_1.7.12_linux_amd64.tar.gz
printf '%s  %s\n' \
  '8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8' \
  "$archive" | sha256sum --check

tar -xzf "$archive" -C "$actionlint_directory" actionlint
shopt -s nullglob
workflows=("$repository_root"/.github/workflows/*.yml "$repository_root"/.github/workflows/*.yaml)
if [ "${#workflows[@]}" -eq 0 ]; then
  echo 'No GitHub Actions workflows found.' >&2
  exit 1
fi
"$actionlint_directory/actionlint" -shellcheck= -pyflakes= "${workflows[@]}"
