#!/bin/bash
set -euo pipefail

export APP_PREFIX=/data/data/com.diamon.civil/files/usr
export DESTDIR="$HOME/fake_root"
export FAKE_USR="$DESTDIR$APP_PREFIX"

FLAT_LIBS="$HOME/android_sim_libs"
TEST_DIR="$HOME/android_sim_test"
TOTAL_LOAD="${TOTAL_LOAD:-100.0}"
CCX_THREADS="${CCX_THREADS:-4}"

require_file() {
  [ -e "$1" ] || { echo "ERROR: no existe $1" >&2; exit 1; }
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { echo "ERROR: falta comando $1" >&2; exit 1; }
}

require_cmd python3
require_cmd find
require_cmd cp
require_cmd grep
require_cmd tr
require_cmd ls

require_file "$FAKE_USR/lib"
require_file "$FAKE_USR/bin/ccx"
require_file "$FAKE_USR/bin/gmsh"

echo "=========================================="
echo "PASO 1: Simulando empaquetado tipo jniLibs/"
echo "=========================================="
rm -rf "$FLAT_LIBS"
mkdir -p "$FLAT_LIBS"
find "$FAKE_USR/lib" -maxdepth 1 -name "*.so*" -exec cp -L {} "$FLAT_LIBS/" \;
cp -L "$FAKE_USR/bin/ccx" "$FLAT_LIBS/libccx.so"
cp -L "$FAKE_USR/bin/gmsh" "$FLAT_LIBS/libgmsh_cli.so"
chmod +x "$FLAT_LIBS/libccx.so" "$FLAT_LIBS/libgmsh_cli.so"
echo "Total de archivos: $(find "$FLAT_LIBS" -maxdepth 1 -type f | wc -l)"

echo
echo "=========================================="
echo "PASO 2: Generando geometría + malla (OCC + Gmsh)"
echo "=========================================="
rm -rf "$TEST_DIR"
mkdir -p "$TEST_DIR"
cd "$TEST_DIR"

cat > cantilever.geo << 'EOF2'
SetFactory("OpenCASCADE");
Box(1) = {0, 0, 0, 10, 1, 1};
Mesh.CharacteristicLengthMax = 0.2;
Mesh.ElementOrder = 1;
s() = Surface In BoundingBox{-0.01,-0.01,-0.01, 0.01,1.01,1.01};
Physical Surface("Fixed") = s();
s2() = Surface In BoundingBox{9.99,-0.01,-0.01, 10.01,1.01,1.01};
Physical Surface("Loaded") = s2();
Physical Volume("Steel") = {1};
EOF2

export LD_LIBRARY_PATH="$FLAT_LIBS"
"$FLAT_LIBS/libgmsh_cli.so" cantilever.geo -3 -format inp -o "$TEST_DIR/cantilever_raw.inp"
"$FLAT_LIBS/libgmsh_cli.so" cantilever.geo -3 -format med -o "$TEST_DIR/cantilever.med"
[ -f "$TEST_DIR/cantilever_raw.inp" ] && echo "OK: malla .inp generada"
[ -f "$TEST_DIR/cantilever.med" ] && echo "OK: malla .med generada"

echo
echo "=========================================="
echo "PASO 3: Preparando input de CalculiX (NSET desde ELSET)"
echo "=========================================="

python3 - "$TOTAL_LOAD" <<'PYEOF'
from pathlib import Path
import sys

raw_path = Path('cantilever_raw.inp')
lines = raw_path.read_text().splitlines(True)

def extract_nodes(elset_name: str):
    nodes = set()
    capture = False
    for line in lines:
        u = line.strip().upper()
        if u.startswith('*ELEMENT') and f'ELSET={elset_name}' in u:
            capture = True
            continue
        if capture:
            if u.startswith('*'):
                break
            parts = [p.strip() for p in line.strip().split(',') if p.strip()]
            for n in parts[1:]:
                nodes.add(int(n))
    return sorted(nodes)

fixed_nodes = extract_nodes('SURFACE1')
loaded_nodes = extract_nodes('SURFACE2')

if not fixed_nodes:
    raise SystemExit('ERROR: no se extrajeron nodos para NFix')
if not loaded_nodes:
    raise SystemExit('ERROR: no se extrajeron nodos para NLoad')

total_load = float(sys.argv[1])
per_node_load = -total_load / len(loaded_nodes)

with open('nsets.inp', 'w') as f:
    f.write('*NSET, NSET=NFix\n')
    for i in range(0, len(fixed_nodes), 10):
        chunk = ','.join(str(n) for n in fixed_nodes[i:i+10])
        f.write(chunk + '\n')

    f.write('*NSET, NSET=NLoad\n')
    for i in range(0, len(loaded_nodes), 10):
        chunk = ','.join(str(n) for n in loaded_nodes[i:i+10])
        f.write(chunk + '\n')

out = []
skip = False
for line in lines:
    u = line.strip().upper()
    if u.startswith('*ELEMENT') and 'TYPE=CPS3' in u:
        skip = True
        continue
    if skip and u.startswith('*') and 'TYPE=CPS3' not in u:
        skip = False
    if skip:
        continue
    out.append(line)

Path('cantilever_clean.inp').write_text(''.join(out))
Path('load_value.txt').write_text(f'{per_node_load:.12g}\n')

print(f'NFix: {len(fixed_nodes)} nodos, NLoad: {len(loaded_nodes)} nodos')
print(f'Carga total objetivo: {total_load}')
print(f'Carga nodal equivalente por nodo: {per_node_load:.12g}')
PYEOF

PER_NODE_LOAD="$(tr -d $'\n' < load_value.txt)"

cat > cantilever_test.inp <<EOF3
*INCLUDE, INPUT=cantilever_clean.inp
*INCLUDE, INPUT=nsets.inp
*MATERIAL, NAME=STEEL
*ELASTIC
210000, 0.3
*SOLID SECTION, ELSET=Volume1, MATERIAL=STEEL
*STEP
*STATIC
*BOUNDARY
NFix, 1, 3
*CLOAD
NLoad, 2, ${PER_NODE_LOAD}
*NODE FILE
U
*EL FILE
S
*END STEP
EOF3

echo
echo "=========================================="
echo "PASO 4: Resolviendo con CalculiX (libccx.so, carga total corregida)"
echo "=========================================="
export OMP_NUM_THREADS="$CCX_THREADS"
export LD_LIBRARY_PATH="$FLAT_LIBS"
echo "OMP_NUM_THREADS: $OMP_NUM_THREADS"
echo "Carga total objetivo: $TOTAL_LOAD"
echo "Carga por nodo aplicada en NLoad: $PER_NODE_LOAD"
"$FLAT_LIBS/libccx.so" -i cantilever_test

echo
echo "=========================================="
echo "PASO 5: Verificando round-trip MED (libs planas)"
echo "=========================================="
"$FLAT_LIBS/libgmsh_cli.so" "$TEST_DIR/cantilever.med" -3 -o "$TEST_DIR/cantilever_roundtrip.msh" 2>&1 | grep -E "Reading MED|nodes|elements|Error" || true

echo
echo "=========================================="
echo "PASO 6: Parseando resultados reales del .frd"
echo "=========================================="
python3 <<'PYEOF'
import math
from pathlib import Path

def parse_frd_disp(filename):
    lines = Path(filename).read_text(errors='ignore').splitlines()
    results = {}
    capture = False

    for line in lines:
        s = line.rstrip()

        if not capture and 'DISP' in s and s.lstrip().startswith('-4'):
            capture = True
            continue

        if capture:
            if s.lstrip().startswith('-3'):
                break

            if s.lstrip().startswith('-1'):
                try:
                    node_id = int(s[3:13])
                except ValueError:
                    continue

                rest = s[13:]
                values = []
                width = 12
                for i in range(0, len(rest), width):
                    chunk = rest[i:i+width].strip()
                    if not chunk:
                        continue
                    try:
                        values.append(float(chunk))
                    except ValueError:
                        pass

                if values:
                    results[node_id] = values

    return results

frd = Path('cantilever_test.frd')
if not frd.exists():
    raise SystemExit('ERROR: no se generó cantilever_test.frd')

disp = parse_frd_disp(str(frd))
print(f'Nodos con desplazamiento: {len(disp)}')

if disp:
    max_node = max(disp, key=lambda n: sum(v*v for v in disp[n]))
    max_vec = disp[max_node]
    max_mag = math.sqrt(sum(v*v for v in max_vec))
    print(f'Nodo con mayor desplazamiento: {max_node} -> {max_vec}')
    print(f'Magnitud máxima: {max_mag}')
else:
    print('ADVERTENCIA: no se extrajeron desplazamientos, revisar formato del .frd')
PYEOF

echo
echo "=========================================="
echo "RESUMEN FINAL"
echo "=========================================="
ls -lh "$TEST_DIR"/*.inp "$TEST_DIR"/*.med "$TEST_DIR"/*.frd "$TEST_DIR"/*.msh 2>/dev/null || true
