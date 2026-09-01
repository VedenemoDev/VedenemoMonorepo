#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

VDOS_FILE="${REPO_ROOT}/.vedenemo/Metsapalsta.vdos"
VDMP_FILE="${REPO_ROOT}/.vedenemo/Metsapalsta2.vdmp"

exec java -cp "${REPO_ROOT}/vedenemo-cli/target/classes" \
  org.vedenemo.cli.VedenemoCli \
  --mload "${VDOS_FILE}" \
  --dload "${VDMP_FILE}"
