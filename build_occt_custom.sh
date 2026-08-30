#!/bin/bash
set -e

cd "$HOME" || exit 1

export APP_PREFIX=/data/data/com.diamon.civil/files/usr
export DESTDIR="$HOME/fake_root"
export FAKE_USR="$DESTDIR$APP_PREFIX"
export TMX_PREFIX=/data/data/com.termux/files/usr

export CC=clang
export CXX=clang++

FREETYPE_PREFIX=""
FREEIMAGE_PREFIX=""

echo "Verificando Tcl/Tk instalados en fake_root..."
test -f "$FAKE_USR/lib/tclConfig.sh"
test -f "$FAKE_USR/lib/tkConfig.sh"
test -f "$FAKE_USR/lib/libtcl8.6.so"
test -f "$FAKE_USR/lib/libtk8.6.so"

echo "Verificando FreeType..."
if [ -f "$FAKE_USR/lib/libfreetype.so" ] && [ -f "$FAKE_USR/include/freetype2/ft2build.h" ]; then
  FREETYPE_PREFIX="$FAKE_USR"
  echo "FreeType encontrado en fake_root"
elif [ -f "$TMX_PREFIX/lib/libfreetype.so" ] && [ -f "$TMX_PREFIX/include/freetype2/ft2build.h" ]; then
  FREETYPE_PREFIX="$TMX_PREFIX"
  echo "FreeType encontrado en Termux prefix, usando este."
else
  echo "Error: FreeType no encontrado en fake_root ni en Termux prefix."
  exit 1
fi

echo "Verificando FreeImage..."
if [ -f "$FAKE_USR/lib/libfreeimage.so" ] || [ -f "$FAKE_USR/lib/libFreeImage.so" ]; then
  FREEIMAGE_PREFIX="$FAKE_USR"
  echo "FreeImage encontrado en fake_root"
elif [ -f "$TMX_PREFIX/lib/libfreeimage.so" ] || [ -f "$TMX_PREFIX/lib/libFreeImage.so" ]; then
  FREEIMAGE_PREFIX="$TMX_PREFIX"
  echo "FreeImage encontrado en Termux prefix, usando este."
else
  echo "Error: FreeImage no encontrado."
  exit 1
fi

echo "Verificando RapidJSON..."
test -f "$FAKE_USR/include/rapidjson/rapidjson.h" || { echo "Error: RapidJSON no encontrado."; exit 1; }

echo "Verificando oneTBB..."
test -f "$FAKE_USR/include/tbb/tbb.h" && test -f "$FAKE_USR/lib/libtbb.so" || { echo "Error: oneTBB no encontrado."; exit 1; }

echo "Verificando Draco..."
test -f "$FAKE_USR/include/draco/compression/decode.h" && test -f "$FAKE_USR/lib/libdraco.so" || { echo "Error: Draco no encontrado."; exit 1; }


export COMMON_CFLAGS="-fPIC -fPIE -Oz -ffile-prefix-map=$DESTDIR= -I$FAKE_USR/include -I$FREETYPE_PREFIX/include/freetype2 -I$TMX_PREFIX/include"
export COMMON_CXXFLAGS="-fPIC -fPIE -Oz -ffile-prefix-map=$DESTDIR= -I$FAKE_USR/include -I$FREETYPE_PREFIX/include/freetype2 -I$TMX_PREFIX/include"
export CPPFLAGS="-I$FAKE_USR/include -I$FREETYPE_PREFIX/include/freetype2 -I$TMX_PREFIX/include"

# Banderas de enlace separadas (16KB alignment)
export BASE_LDFLAGS="-Wl,-z,max-page-size=16384 -L$FAKE_USR/lib -L$TMX_PREFIX/lib -L$FREETYPE_PREFIX/lib -L$FREEIMAGE_PREFIX/lib"
export EXE_LDFLAGS="-pie $BASE_LDFLAGS"
export SHARED_LDFLAGS="$BASE_LDFLAGS"

export PKG_CONFIG_PATH="$FAKE_USR/lib/pkgconfig:$TMX_PREFIX/lib/pkgconfig:${PKG_CONFIG_PATH:-}"
export LD_LIBRARY_PATH="$FAKE_USR/lib:$TMX_PREFIX/lib:$FREETYPE_PREFIX/lib:$FREEIMAGE_PREFIX/lib:${LD_LIBRARY_PATH:-}"

echo "Verificando headers Tcl/Tk..."
find "$FAKE_USR/include" \( -name 'tcl.h' -o -name 'tk.h' -o -name 'tkDecls.h' \) | sort

echo "Verificando scripts runtime Tcl/Tk..."
test -f "$FAKE_USR/lib/tcl8.6/init.tcl"
test -f "$FAKE_USR/lib/tk8.6/tk.tcl"

echo "Descargando OpenCASCADE 8.0.0.p1..."
rm -rf "$HOME/occt" "$HOME"/OCCT-*
wget -qO- "https://github.com/Open-Cascade-SAS/OCCT/archive/refs/tags/V8_0_0_p1.tar.gz" | tar -xzf -
mv OCCT-* occt
cd "$HOME/occt" || exit 1

echo "Buscando y parcheando bloqueo de Draw en Android..."
grep -Rni "Draw module is turned off due to it is not supported on Android" . || true

if [ -f "CMakeLists.txt" ]; then
  cp CMakeLists.txt CMakeLists.txt.bak

  python3 - <<'PY'
from pathlib import Path
p = Path("CMakeLists.txt")
s = p.read_text()

s = s.replace(
    'message (STATUS "Info. Draw module is turned off due to it is not supported on Android")',
    'message (STATUS "Info. Draw module is forced ON on Android by local patch")'
)

s = s.replace(
    'set (BUILD_MODULE_Draw OFF CACHE BOOL "${BUILD_MODULE_Draw_DESCR}" FORCE)',
    '# patched out: keep BUILD_MODULE_Draw ON on Android'
)

p.write_text(s)
PY
else
  echo "Error: no se encontró CMakeLists.txt en $HOME/occt"
  exit 1
fi

echo "Verificando parche..."
grep -n "Draw module is forced ON on Android by local patch" CMakeLists.txt || {
  echo "ADVERTENCIA: no se encontró el mensaje del parche aplicado; revisa manualmente el patrón de búsqueda."
}
if grep -n 'set (BUILD_MODULE_Draw OFF CACHE BOOL "${BUILD_MODULE_Draw_DESCR}" FORCE)' CMakeLists.txt; then
  echo "Error: sigue presente la línea que fuerza BUILD_MODULE_Draw=OFF"
  exit 1
fi

mkdir -p build
cd build || exit 1
rm -rf ./*

echo "Configurando OpenCASCADE con Draw Harness Tcl/Tk + FreeType + FreeImage..."
cmake .. \
  -DCMAKE_INSTALL_PREFIX="$APP_PREFIX" \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_C_COMPILER="$CC" \
  -DCMAKE_CXX_COMPILER="$CXX" \
  -DCMAKE_C_FLAGS="$COMMON_CFLAGS" \
  -DCMAKE_CXX_FLAGS="$COMMON_CXXFLAGS" \
  -DCMAKE_SHARED_LINKER_FLAGS="$SHARED_LDFLAGS" \
  -DCMAKE_MODULE_LINKER_FLAGS="$SHARED_LDFLAGS" \
  -DCMAKE_EXE_LINKER_FLAGS="$EXE_LDFLAGS" \
  -DCMAKE_PREFIX_PATH="$FAKE_USR;$TMX_PREFIX" \
  -DBUILD_LIBRARY_TYPE=Shared \
  -DBUILD_MODULE_Draw=ON \
  -DBUILD_MODULE_Visualization=ON \
  -DUSE_FREETYPE=ON \
  -DUSE_FREEIMAGE=ON \
  -DUSE_RAPIDJSON=ON \
  -DUSE_TBB=ON \
  -DUSE_DRACO=ON \
  -DUSE_VTK=OFF \
  -DUSE_GLX=OFF \
  -DUSE_OPENGL=OFF \
  -DUSE_GLES2=ON \
  -DBUILD_DOC_Overview=OFF \
  -DBUILD_SAMPLES_QT=OFF \
  -DBUILD_SAMPLES_MFC=OFF \
  -D3RDPARTY_DIR="$FAKE_USR" \
  -D3RDPARTY_TCL_DIR="$FAKE_USR" \
  -D3RDPARTY_TCL_INCLUDE_DIR="$FAKE_USR/include" \
  -D3RDPARTY_TCL_LIBRARY_DIR="$FAKE_USR/lib" \
  -D3RDPARTY_TK_DIR="$FAKE_USR" \
  -D3RDPARTY_TK_INCLUDE_DIR="$FAKE_USR/include" \
  -D3RDPARTY_TK_LIBRARY_DIR="$FAKE_USR/lib" \
  -D3RDPARTY_FREETYPE_DIR="$FREETYPE_PREFIX" \
  -D3RDPARTY_FREETYPE_INCLUDE_DIR="$FREETYPE_PREFIX/include/freetype2" \
  -D3RDPARTY_FREETYPE_LIBRARY_DIR="$FREETYPE_PREFIX/lib" \
  -D3RDPARTY_FREEIMAGE_DIR="$FREEIMAGE_PREFIX" \
  -D3RDPARTY_FREEIMAGE_INCLUDE_DIR="$FREEIMAGE_PREFIX/include" \
  -D3RDPARTY_FREEIMAGE_LIBRARY_DIR="$FREEIMAGE_PREFIX/lib" \
  -D3RDPARTY_RAPIDJSON_DIR="$FAKE_USR" \
  -D3RDPARTY_RAPIDJSON_INCLUDE_DIR="$FAKE_USR/include" \
  -D3RDPARTY_TBB_DIR="$FAKE_USR" \
  -D3RDPARTY_TBB_INCLUDE_DIR="$FAKE_USR/include" \
  -D3RDPARTY_TBB_LIBRARY_DIR="$FAKE_USR/lib" \
  -D3RDPARTY_DRACO_DIR="$FAKE_USR" \
  -D3RDPARTY_DRACO_INCLUDE_DIR="$FAKE_USR/include" \
  -D3RDPARTY_DRACO_LIBRARY_DIR="$FAKE_USR/lib" \
  -DINSTALL_TCL=ON \
  -DINSTALL_TK=ON \
  -DINSTALL_FREETYPE=ON \
  -DINSTALL_FREEIMAGE=ON \
  -DINSTALL_TBB=ON

echo "Compilando OpenCASCADE..."
JOBS="$(nproc)"
if [ "$JOBS" -gt 1 ]; then
  JOBS=$((JOBS - 1))
fi
cmake --build . --parallel "$JOBS"

echo "Instalando en fake_root..."
DESTDIR="$DESTDIR" cmake --install .

echo "Creando wrapper drawenv..."
cat > "$FAKE_USR/bin/drawenv" <<EOF
#!/bin/bash
export APP_PREFIX="$APP_PREFIX"
export DESTDIR="$DESTDIR"
export FAKE_USR="$FAKE_USR"
export TMX_PREFIX="$TMX_PREFIX"
export LD_LIBRARY_PATH="$FAKE_USR/lib:$TMX_PREFIX/lib:\$LD_LIBRARY_PATH"
export TCL_LIBRARY="$FAKE_USR/lib/tcl8.6"
export TK_LIBRARY="$FAKE_USR/lib/tk8.6"
export TCLLIBPATH="$FAKE_USR/lib $FAKE_USR/lib/tcl8.6 $FAKE_USR/lib/tk8.6"
exec "$FAKE_USR/bin/DRAWEXE" "\$@"
EOF
chmod +x "$FAKE_USR/bin/drawenv"

echo "=== Verificando binarios y librerias ==="
find "$FAKE_USR/bin" -maxdepth 1 \( -name 'DRAWEXE*' -o -name 'draw*' -o -name 'TK*' \) | sort || true
find "$FAKE_USR/lib" -maxdepth 1 \( -name 'libTK*' -o -name 'libtcl*' -o -name 'libtk*' -o -name 'libfreetype*' -o -name 'libfreeimage*' -o -name 'libFreeImage*' \) | sort || true

echo ""
echo "=== Verificando alineacion ELF 16KB (OCCT) ==="
for lib in "$FAKE_USR/lib"/libTK*.so; do
  [ -f "$lib" ] || continue
  align=$(readelf -lW "$lib" 2>/dev/null | grep LOAD | head -n1 | awk '{print $NF}')
  if [ "$align" = "0x4000" ] || [ "$align" = "0x10000" ]; then
    echo "  [OK] $(basename "$lib") alineado a 16KB ($align)"
  else
    echo "  [AVISO] $(basename "$lib") NO alineado a 16KB (actual: ${align:-desconocido})"
  fi
done

echo "Verificando runtime Tcl/Tk..."
ls -lh "$FAKE_USR/lib/tcl8.6/init.tcl"
ls -lh "$FAKE_USR/lib/tk8.6/tk.tcl"

echo "=== OpenCASCADE con Draw/Tcl/Tk + FreeType + FreeImage instalado correctamente ==="
