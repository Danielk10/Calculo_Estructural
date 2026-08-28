#!/bin/bash
set -e

cd "$HOME" || exit 1

export APP_PREFIX=/data/data/com.diamon.civil/files/usr
export DESTDIR="$HOME/fake_root"
export FAKE_USR="$DESTDIR$APP_PREFIX"

# ==============================================================================
# FUNCIONES DE VERIFICACIÓN (alineación ELF 16KB + integridad)
# ==============================================================================
require_file() {
  [ -e "$1" ] || { echo "ERROR: no existe $1" >&2; exit 1; }
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { echo "ERROR: falta comando $1" >&2; exit 1; }
}

check_elf_alignment() {
  local lib="$1"
  local align
  align=$(readelf -lW "$lib" 2>/dev/null | grep LOAD | head -n1 | awk '{print $NF}')
  if [ "$align" = "0x4000" ] || [ "$align" = "0x10000" ]; then
    echo "  [OK] $(basename "$lib") alineado a 16KB ($align)"
    return 0
  else
    echo "  [FALTA] $(basename "$lib") NO alineado a 16KB (actual: ${align:-desconocido})"
    return 1
  fi
}

verify_libs_alignment() {
  local dir="$1"
  local pattern="$2"
  local fail=0
  while IFS= read -r -d '' lib; do
    check_elf_alignment "$lib" || fail=1
  done < <(find "$dir" -maxdepth 1 -name "$pattern" -print0)
  return $fail
}

require_cmd readelf
require_cmd find
require_cmd awk

mkdir -p "$FAKE_USR/include"

echo "=== Instalando RapidJSON (header-only) ==="
rm -rf "$HOME/rapidjson"
git clone --depth 1 https://github.com/Tencent/rapidjson.git "$HOME/rapidjson"
cp -r "$HOME/rapidjson/include/rapidjson" "$FAKE_USR/include/"

echo ""
echo "=== VERIFICACION FINAL: RapidJSON ==="
if [ -f "$FAKE_USR/include/rapidjson/rapidjson.h" ] && [ -f "$FAKE_USR/include/rapidjson/document.h" ]; then
  echo "  [OK] RapidJSON instalado y verificado correctamente en fake_root"
else
  echo "  [FALTA] RapidJSON incompleto"
  exit 1
fi
