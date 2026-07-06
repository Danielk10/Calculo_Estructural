import subprocess
files = set(open('file_list.txt').read().splitlines())
with open('REPORTE_ANALISIS_DEPENDENCIAS.md', 'w') as r:
    r.write('# Reporte de Dependencias\n\n')
    for f in sorted(files):
        r.write(f'### {f}\n| Dep | Class | InFolder |\n|---|---|---|\n')
        try:
            out = subprocess.check_output(['readelf', '-d', 'app/src/main/jniLibs/arm64-v8a/' + f], text=True)
            for line in out.splitlines():
                if '(NEEDED)' in line:
                    d = line.split('[')[1].split(']')[0]
                    c = 'Sistema' if d in ['libc.so', 'libm.so', 'libdl.so', 'liblog.so'] else 'Externa'
                    r.write(f'| {d} | {c} | {('Sí' if d in files else 'No')} |\n')
        except: pass
        r.write('\n')
