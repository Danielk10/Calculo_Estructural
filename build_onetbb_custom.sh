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

export CC=clang
export CXX=clang++

mkdir -p "$FAKE_USR/lib" "$FAKE_USR/include"

echo "=== Compilando oneTBB para arm64 (nativo en Termux) ==="
rm -rf "$HOME/oneTBB"
git clone --depth 1 --branch v2021.13.0 https://github.com/oneapi-src/oneTBB.git "$HOME/oneTBB"
cd "$HOME/oneTBB" || exit 1

mkdir -p build && cd build || exit 1
rm -rf ./*

cmake .. \
  -DCMAKE_INSTALL_PREFIX="$APP_PREFIX" \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_C_COMPILER="$CC" \
  -DCMAKE_CXX_COMPILER="$CXX" \
  -DCMAKE_C_FLAGS="-fPIC -fPIE -Oz" \
  -DCMAKE_CXX_FLAGS="-fPIC -fPIE -Oz" \
  -DCMAKE_EXE_LINKER_FLAGS="-pie -Wl,--undefined-version -Wl,-z,max-page-size=16384" \
  -DCMAKE_SHARED_LINKER_FLAGS="-Wl,--undefined-version -Wl,-z,max-page-size=16384" \
  -DTBB_TEST=OFF \
  -DTBB_EXAMPLES=OFF \
  -DTBB_STRICT=OFF \
  -DBUILD_SHARED_LIBS=ON

JOBS="$(nproc)"
if [ "$JOBS" -gt 1 ]; then
  JOBS=$((JOBS - 1))
fi
cmake --build . --parallel "$JOBS"

echo "Instalando oneTBB en fake_root..."
DESTDIR="$DESTDIR" cmake --install .

echo ""
echo "=== VERIFICACION FINAL: oneTBB ==="
OK=1
for lib in libtbb.so libtbbmalloc.so; do
  if find "$FAKE_USR/lib" -maxdepth 1 -name "${lib}*" | grep -q .; then
    echo "  [OK] $lib encontrado"
  else
    echo "  [FALTA] $lib NO encontrado"
    OK=0
  fi
done
if [ -f "$FAKE_USR/include/tbb/tbb.h" ]; then
  echo "  [OK] headers tbb/tbb.h encontrados"
else
  echo "  [FALTA] headers tbb/tbb.h NO encontrados"
  OK=0
fi

echo ""
echo "=== Verificando alineacion ELF 16KB (oneTBB) ==="
verify_libs_alignment "$FAKE_USR/lib" "libtbb*.so*" || { echo "ERROR: alineacion incorrecta en oneTBB"; exit 1; }

if [ "$OK" -eq 1 ]; then
  echo "=== oneTBB instalado y verificado correctamente en fake_root ==="
else
  echo "=== ERROR: oneTBB incompleto, revisa los mensajes anteriores ==="
  exit 1
fi
