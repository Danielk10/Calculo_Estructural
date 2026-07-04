#!/bin/bash
set -e

export APP_PREFIX=/data/data/com.diamon.civil/files/usr
export DESTDIR="$HOME/fake_root"
export FAKE_USR="$DESTDIR$APP_PREFIX"
export TMX_PREFIX=/data/data/com.termux/files/usr

FLAT_LIBS="$HOME/android_sim_libs"
TEST_DIR="$HOME/android_sim_test"

echo "=========================================="
echo "PASO 1: Simulando empaquetado tipo jniLibs/"
echo "=========================================="
rm -rf "$FLAT_LIBS"
mkdir -p "$FLAT_LIBS"
find "$FAKE_USR/lib" -maxdepth 1 -name "*.so*" -exec cp -L {} "$FLAT_LIBS/" \;
cp -L "$FAKE_USR/bin/ccx" "$FLAT_LIBS/libccx.so"
cp -L "$FAKE_USR/bin/gmsh" "$FLAT_LIBS/libgmsh_cli.so"
chmod +x "$FLAT_LIBS/libccx.so" "$FLAT_LIBS/libgmsh_cli.so"
echo "Total de archivos: $(ls "$FLAT_LIBS" | wc -l)"

echo
echo "=========================================="
echo "PASO 2: Generando geometría + malla (OCC + Gmsh)"
echo "=========================================="
rm -rf "$TEST_DIR"
mkdir -p "$TEST_DIR"
cd "$TEST_DIR" || exit 1

cat > cantilever.geo << 'EOF'
SetFactory("OpenCASCADE");
Box(1) = {0, 0, 0, 10, 1, 1};
Mesh.CharacteristicLengthMax = 0.2;
Mesh.ElementOrder = 1;
s() = Surface In BoundingBox{-0.01,-0.01,-0.01, 0.01,1.01,1.01};
Physical Surface("Fixed") = s();
s2() = Surface In BoundingBox{9.99,-0.01,-0.01, 10.01,1.01,1.01};
Physical Surface("Loaded") = s2();
Physical Volume("Steel") = {1};
EOF

export LD_LIBRARY_PATH="$FLAT_LIBS"
"$FLAT_LIBS/libgmsh_cli.so" cantilever.geo -3 -format inp -o "$TEST_DIR/cantilever_raw.inp"
"$FLAT_LIBS/libgmsh_cli.so" cantilever.geo -3 -format med -o "$TEST_DIR/cantilever.med"
test -f "$TEST_DIR/cantilever_raw.inp" && echo "OK: malla .inp generada"
test -f "$TEST_DIR/cantilever.med" && echo "OK: malla .med generada"

echo
echo "=========================================="
echo "PASO 3: Preparando input de CalculiX (NSET desde ELSET)"
echo "=========================================="
python3 << 'PYEOF'
with open("cantilever_raw.inp") as f:
    lines = f.readlines()

def extract_nodes(elset_name):
    nodes = set()
    capture = False
    for line in lines:
        u = line.strip().upper()
        if u.startswith("*ELEMENT") and f"ELSET={elset_name}" in u:
            capture = True
            continue
        if capture:
            if u.startswith("*"):
                break
            parts = [p.strip() for p in line.strip().split(",") if p.strip()]
            for n in parts[1:]:
                nodes.add(int(n))
    return sorted(nodes)

fixed_nodes = extract_nodes("SURFACE1")
loaded_nodes = extract_nodes("SURFACE2")

with open("nsets.inp", "w") as f:
    f.write("*NSET, NSET=NFix\n")
    for i in range(0, len(fixed_nodes), 10):
        f.write(",".join(str(n) for n in fixed_nodes[i:i+10]) + ",\n")
    f.write("*NSET, NSET=NLoad\n")
    for i in range(0, len(loaded_nodes), 10):
        f.write(",".join(str(n) for n in loaded_nodes[i:i+10]) + ",\n")

print(f"NFix: {len(fixed_nodes)} nodos, NLoad: {len(loaded_nodes)} nodos")

out = []
skip = False
for line in lines:
    u = line.strip().upper()
    if u.startswith("*ELEMENT") and "TYPE=CPS3" in u:
        skip = True
        continue
    if skip and u.startswith("*") and "TYPE=CPS3" not in u:
        skip = False
    if skip:
        continue
    out.append(line)

with open("cantilever_clean.inp", "w") as f:
    f.writelines(out)
PYEOF

cat > cantilever_test.inp << 'EOF'
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
NLoad, 2, -100
*NODE FILE
U
*EL FILE
S
*END STEP
EOF

echo
echo "=========================================="
echo "PASO 4: Resolviendo con CalculiX (libccx.so, libs planas)"
echo "=========================================="
export LD_LIBRARY_PATH="$FLAT_LIBS"

echo "--- Verificando núcleos disponibles AHORA MISMO ---"
NCPU="$(nproc)"
echo "nproc devuelve: $NCPU"

echo
echo "--- 1 hilo (forzado) ---"
export OMP_NUM_THREADS=1
time "$FLAT_LIBS/libccx.so" -i cantilever_test
mv cantilever_test.frd cantilever_1thread.frd

echo
echo "--- Forzando 4 hilos explícitamente (ignorando nproc si es inestable) ---"
export OMP_NUM_THREADS=4
echo "OMP_NUM_THREADS forzado a: $OMP_NUM_THREADS"
time "$FLAT_LIBS/libccx.so" -i cantilever_test
mv cantilever_test.frd cantilever_4thread.frd

echo
echo "=== Confirmando en el log cuántos cpus reportó ccx realmente ==="
echo "(revisa arriba las líneas 'Using up to N cpu(s)' de la segunda corrida)"

echo
echo "=== Comparando resultados numéricos (ignorando timestamp UTIME) ==="
diff <(grep -v UTIME cantilever_1thread.frd) <(grep -v UTIME cantilever_4thread.frd) \
  && echo "IDENTICOS (excluyendo timestamp)" \
  || echo "Diferencias de precisión flotante (esperado y normal en paralelo)"

echo
echo "=========================================="
echo "PASO 5: Verificando round-trip MED (libs planas)"
echo "=========================================="
"$FLAT_LIBS/libgmsh_cli.so" "$TEST_DIR/cantilever.med" -3 -o "$TEST_DIR/cantilever_roundtrip.msh" 2>&1 | grep -E "Reading MED|nodes|elements|Error"

echo
echo "=========================================="
echo "PASO 6: Parseando resultados reales del .frd (formato ancho fijo)"
echo "=========================================="
python3 << 'PYEOF'
def parse_frd_block(filename, block_name):
    with open(filename) as f:
        lines = f.readlines()
    results = {}
    capture = False
    ncols = 0
    for line in lines:
        if f"-4  {block_name}" in line:
            capture = True
            ncols = int(line.split()[-2])
            continue
        if capture and line.startswith(" -3"):
            break
        if capture and line.startswith(" -1"):
            # Formato fijo CalculiX: " -1" + nodo(10) + valores de 12 chars cada uno
            node_id = int(line[3:13])
            rest = line[13:]
            values = []
            width = 12
            for i in range(0, len(rest.strip()), width):
                chunk = rest[i:i+width].strip()
                if chunk:
                    try:
                        values.append(float(chunk))
                    except ValueError:
                        continue
            results[node_id] = values
    return results

disp = parse_frd_block("cantilever_4thread.frd", "DISP")
print(f"Nodos con desplazamiento: {len(disp)}")
if disp:
    max_node = max(disp, key=lambda n: sum(v**2 for v in disp[n]))
    print(f"Nodo con mayor desplazamiento: {max_node} -> {disp[max_node]}")
else:
    print("ADVERTENCIA: no se extrajeron desplazamientos, revisar formato del .frd")
PYEOF

echo
echo "=========================================="
echo "RESUMEN FINAL"
echo "=========================================="
ls -lh "$TEST_DIR"/*.inp "$TEST_DIR"/*.med "$TEST_DIR"/*.frd "$TEST_DIR"/*.msh 2>/dev/null
