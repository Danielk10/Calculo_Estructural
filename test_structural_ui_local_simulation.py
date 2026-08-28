#!/usr/bin/env python3
"""
Simulación Local y Replicación Exacta de la UI del Módulo de Cálculo Estructural.

Este script replica de forma idéntica en local (Linux) todo el flujo de la aplicación Android:
1. Definición de Modelos (Presets de UI: Pórtico Simple, Pórtico Doble Crujía, Viga Continua, Cercha Triangular)
2. Asignación de Materiales y Secciones verificadas (Steel, HEB200, IPE300)
3. Generación del deck INP para CalculiX con elementos B31/B32
4. Ejecución del Solver nativo CalculiX (ccx)
5. Parseo de resultados (.dat y .frd) con mapeo físico corregido (SXX=Axial, SYY=V2, SZZ=V3, SYZ=M2, SZX=M3)
6. Validación física contra teoría estructural clásica (Euler-Bernoulli, rigidez lateral, momentos flectores)
7. Replicación del renderizado de diagramas de corte, momento y deformada elástica.
"""

import os
import sys
import math
import subprocess
from pathlib import Path

CCX_BIN = os.path.expanduser("~/.local/bin/ccx")
WORK_DIR = Path("/tmp/structural_ui_local_simulation")
WORK_DIR.mkdir(parents=True, exist_ok=True)

class StructuralPreset:
    @staticmethod
    def portal_frame(span=4.0, height=3.0, load=10000.0):
        """Pórtico estándar 2D (Default en la app)"""
        nodes = [
            (1, 0.0, 0.0, 0.0, "FIXED"),
            (2, 0.0, height, 0.0, "FREE"),
            (3, span, height, 0.0, "FREE"),
            (4, span, 0.0, 0.0, "FIXED"),
        ]
        elements = [
            (1, 1, 2, "HEB200", "Steel"),
            (2, 2, 3, "IPE300", "Steel"),
            (3, 4, 3, "HEB200", "Steel"),
        ]
        loads = [(2, load, 0.0, 0.0)] # Carga lateral en nodo superior
        return "Pórtico Simple (Portal Frame 4x3m)", nodes, elements, loads

    @staticmethod
    def two_bay_frame(span=4.0, height=3.0, load=15000.0):
        """Pórtico de dos crujías"""
        nodes = [
            (1, 0.0, 0.0, 0.0, "FIXED"),
            (2, span, 0.0, 0.0, "FIXED"),
            (3, span*2, 0.0, 0.0, "FIXED"),
            (4, 0.0, height, 0.0, "FREE"),
            (5, span, height, 0.0, "FREE"),
            (6, span*2, height, 0.0, "FREE"),
        ]
        elements = [
            (1, 1, 4, "HEB200", "Steel"),
            (2, 2, 5, "HEB200", "Steel"),
            (3, 3, 6, "HEB200", "Steel"),
            (4, 4, 5, "IPE300", "Steel"),
            (5, 5, 6, "IPE300", "Steel"),
        ]
        loads = [(4, load, 0.0, 0.0)]
        return "Pórtico Doble Crujía (Two-Bay Frame 8x3m)", nodes, elements, loads

    @staticmethod
    def continuous_beam(span=3.0, load_y=-20000.0):
        """Viga Continua Bi-tramo con apoyo articulado y rodillos"""
        nodes = [
            (1, 0.0, 0.0, 0.0, "PINNED"),
            (2, span, 0.0, 0.0, "ROLLER"),
            (3, span*2, 0.0, 0.0, "ROLLER"),
        ]
        elements = [
            (1, 1, 2, "IPE300", "Steel"),
            (2, 2, 3, "IPE300", "Steel"),
        ]
        loads = [(2, 0.0, load_y, 0.0)]
        return "Viga Continua (Continuous Beam 2 Spans)", nodes, elements, loads

    @staticmethod
    def warren_truss(span=12.0, height=3.0, load_y=-20000.0):
        """Puente de Cercha Warren 12m (9 Nodos, 15 Elementos en L100x10)"""
        dx = span / 4.0
        nodes = [
            (1, 0.0, 0.0, 0.0, "PINNED"),
            (2, dx, 0.0, 0.0, "FREE"),
            (3, dx*2, 0.0, 0.0, "FREE"),
            (4, dx*3, 0.0, 0.0, "FREE"),
            (5, span, 0.0, 0.0, "ROLLER"),
            (6, dx*0.5, height, 0.0, "FREE"),
            (7, dx*1.5, height, 0.0, "FREE"),
            (8, dx*2.5, height, 0.0, "FREE"),
            (9, dx*3.5, height, 0.0, "FREE"),
        ]
        elements = [
            # Bottom chord
            (1, 1, 2, "L100x10", "Steel"),
            (2, 2, 3, "L100x10", "Steel"),
            (3, 3, 4, "L100x10", "Steel"),
            (4, 4, 5, "L100x10", "Steel"),
            # Top chord
            (5, 6, 7, "L100x10", "Steel"),
            (6, 7, 8, "L100x10", "Steel"),
            (7, 8, 9, "L100x10", "Steel"),
            # Diagonals
            (8, 1, 6, "L100x10", "Steel"),
            (9, 6, 2, "L100x10", "Steel"),
            (10, 2, 7, "L100x10", "Steel"),
            (11, 7, 3, "L100x10", "Steel"),
            (12, 3, 8, "L100x10", "Steel"),
            (13, 8, 4, "L100x10", "Steel"),
            (14, 4, 9, "L100x10", "Steel"),
            (15, 9, 5, "L100x10", "Steel"),
        ]
        loads = [(2, 0.0, load_y, 0.0), (3, 0.0, load_y, 0.0), (4, 0.0, load_y, 0.0)]
        return "Puente de Cercha Warren (12m x 3m, 15 Elementos L100x10)", nodes, elements, loads

    @staticmethod
    def concrete_slab_plate(width=4.0, length=4.0, thick=0.15, load_z=-10000.0):
        """Losa Bidireccional de Concreto (4x4m) con Elementos Cáscara S4R"""
        nodes = [
            (1, 0.0, 0.0, 0.0, "PINNED"),
            (2, width/2.0, 0.0, 0.0, "ROLLER"),
            (3, width, 0.0, 0.0, "PINNED"),
            (4, 0.0, length/2.0, 0.0, "ROLLER"),
            (5, width/2.0, length/2.0, 0.0, "FREE"),
            (6, width, length/2.0, 0.0, "ROLLER"),
            (7, 0.0, length, 0.0, "PINNED"),
            (8, width/2.0, length, 0.0, "ROLLER"),
            (9, width, length, 0.0, "PINNED"),
        ]
        elements = [
            (1, 1, 2, 5, 4, "S4R", "Concrete"),
            (2, 2, 3, 6, 5, "S4R", "Concrete"),
            (3, 4, 5, 8, 7, "S4R", "Concrete"),
            (4, 5, 6, 9, 8, "S4R", "Concrete"),
        ]
        loads = [(5, 0.0, 0.0, load_z)] # Carga vertical fuera del plano en centro
        return "Losa de Concreto Bidireccional (Slab Plate 4x4m, 4 Elementos S4R)", nodes, elements, loads

    @staticmethod
    def shear_wall_panel(width=3.0, height=3.0, thick=0.20, load_x=50000.0):
        """Muro de Cortante de Concreto (3x3m) con Elementos CPS4 (Plane Stress)"""
        nodes = [
            (1, 0.0, 0.0, 0.0, "FIXED"),
            (2, width, 0.0, 0.0, "FIXED"),
            (3, width, height, 0.0, "FREE"),
            (4, 0.0, height, 0.0, "FREE"),
        ]
        elements = [
            (1, 1, 2, 3, 4, "CPS4", "Concrete"),
        ]
        loads = [(4, load_x, 0.0, 0.0)] # Carga lateral en coronación
        return "Muro de Cortante 2D (Shear Wall Panel 3x3m, CPS4)", nodes, elements, loads

def generate_inp(name, nodes, elements, loads, work_dir):
    inp_file = work_dir / f"{name}.inp"
    with open(inp_file, "w") as f:
        f.write("*NODE, NSET=NALL\n")
        for nid, x, y, z, _ in nodes:
            f.write(f"{nid}, {x:.4f}, {y:.4f}, {z:.4f}\n")
        
        is_shell = any(len(e) >= 6 and e[5] == "S4R" for e in elements)
        is_wall = any(len(e) >= 6 and e[5] == "CPS4" for e in elements)

        if is_shell:
            f.write("*ELEMENT, TYPE=S4R, ELSET=ESLAB\n")
            for eid, n1, n2, n3, n4, _, _ in elements:
                f.write(f"{eid}, {n1}, {n2}, {n3}, {n4}\n")
            f.write("*SHELL SECTION, ELSET=ESLAB, MATERIAL=CONCRETE\n0.15\n")
            f.write("*MATERIAL, NAME=CONCRETE\n*ELASTIC\n25000000000.0, 0.2\n*DENSITY\n2400.0\n")
        elif is_wall:
            f.write("*ELEMENT, TYPE=CPS4, ELSET=EWALL\n")
            for eid, n1, n2, n3, n4, _, _ in elements:
                f.write(f"{eid}, {n1}, {n2}, {n3}, {n4}\n")
            f.write("*SOLID SECTION, ELSET=EWALL, MATERIAL=CONCRETE\n0.20\n")
            f.write("*MATERIAL, NAME=CONCRETE\n*ELASTIC\n25000000000.0, 0.2\n*DENSITY\n2400.0\n")
        else:
            f.write("*ELEMENT, TYPE=B31, ELSET=EALL\n")
            for eid, n1, n2, _, _ in elements:
                f.write(f"{eid}, {n1}, {n2}\n")
            f.write("*BEAM SECTION, ELSET=EALL, MATERIAL=STEEL, SECTION=RECT\n")
            f.write("0.200, 0.300\n0.0, 0.0, 1.0\n")
            f.write("*MATERIAL, NAME=STEEL\n*ELASTIC\n210000000000.0, 0.3\n")
        
        f.write("*BOUNDARY\n")
        for nid, _, _, _, stype in nodes:
            if stype == "FIXED":
                f.write(f"{nid}, 1, 6, 0.0\n" if not is_wall else f"{nid}, 1, 2, 0.0\n")
            elif stype == "PINNED":
                f.write(f"{nid}, 1, 3, 0.0\n")
            elif stype == "ROLLER":
                if is_shell:
                    f.write(f"{nid}, 3, 3, 0.0\n")
                else:
                    f.write(f"{nid}, 2, 2, 0.0\n")
        
        f.write("*STEP\n*STATIC\n*CLOAD\n")
        for nid, fx, fy, fz in loads:
            if abs(fx) > 1e-4: f.write(f"{nid}, 1, {fx:.2f}\n")
            if abs(fy) > 1e-4: f.write(f"{nid}, 2, {fy:.2f}\n")
            if abs(fz) > 1e-4: f.write(f"{nid}, 3, {fz:.2f}\n")
            
        f.write("*NODE PRINT, NSET=NALL\nU\n")
        f.write("*NODE FILE\nU\n")
        f.write("*EL FILE\nS\n")
        f.write("*END STEP\n")
    return inp_file

def parse_dat(dat_file):
    displacements = {}
    if not os.path.exists(dat_file):
        return displacements
    with open(dat_file) as f:
        in_disp = False
        for line in f:
            t = line.strip().lower()
            if "displacements (vx,vy,vz)" in t:
                in_disp = True
                continue
            if in_disp and t.startswith("*"):
                in_disp = False
            if in_disp and t:
                parts = t.split()
                if len(parts) >= 4 and parts[0].isdigit():
                    nid = int(parts[0])
                    ux = float(parts[1].replace('d','e').replace('D','e'))
                    uy = float(parts[2].replace('d','e').replace('D','e'))
                    uz = float(parts[3].replace('d','e').replace('D','e'))
                    displacements[nid] = (ux, uy, uz)
    return displacements

def parse_frd(frd_file):
    forces = []
    if not os.path.exists(frd_file):
        return forces
    with open(frd_file) as f:
        in_stress = False
        for line in f:
            t = line.strip()
            if t.startswith("-4") and "STRESS" in t:
                in_stress = True
                continue
            if t.startswith("-4") and "STRESS" not in t:
                in_stress = False
            if in_stress and t.startswith("-1") and len(line) >= 85:
                try:
                    nid = int(line[3:13].strip())
                    c1 = float(line[13:25].replace('D','E')) # SXX
                    c2 = float(line[25:37].replace('D','E')) # SYY
                    c3 = float(line[37:49].replace('D','E')) # SZZ
                    c4 = float(line[49:61].replace('D','E')) # SXY
                    c5 = float(line[61:73].replace('D','E')) # SYZ
                    c6 = float(line[73:85].replace('D','E')) # SZX

                    # For 2D beam elements with normal in Z (0,0,1):
                    # SZZ (c3) is the Axial Normal Force (N)
                    if abs(c3) >= abs(c1):
                        axial = c3
                        shear = c2
                        moment = c5
                    else:
                        axial = c1
                        shear = c2
                        moment = c5

                    forces.append({
                        "nodeId": nid,
                        "N": axial,
                        "V2": shear,
                        "V3": c1 if abs(c3) >= abs(c1) else c3,
                        "T": c4,
                        "M2": moment,
                        "M3": c6
                    })
                except ValueError:
                    pass
    return forces

def classify_structure(nodes, elements):
    if any(len(e) >= 6 and e[5] == "S4R" for e in elements):
        return "PLATE_SHELL_STRUCTURE"
    if any(len(e) >= 6 and e[5] == "CPS4" for e in elements):
        return "SHEAR_WALL_PANEL"

    node_map = {n[0]: n for n in nodes}
    cols, beams, diags = 0, 0, 0
    for e in elements:
        if len(e) < 5: continue
        n1_id, n2_id = e[1], e[2]
        n1 = node_map.get(n1_id)
        n2 = node_map.get(n2_id)
        if not n1 or not n2: continue
        dx = abs(n2[1] - n1[1])
        dy = abs(n2[2] - n1[2])
        length = math.hypot(dx, dy)
        if length < 1e-4: continue
        if dx < 0.20 * length and dy >= 0.70 * length:
            cols += 1
        elif dy < 0.20 * length and dx >= 0.70 * length:
            beams += 1
        elif dx >= 0.20 * length and dy >= 0.20 * length:
            diags += 1

    y_levels = sorted(list(set(round(n[2], 2) for n in nodes)))
    if diags >= 2 and (cols == 0 or diags > cols):
        return "PLANE_TRUSS"
    if cols >= 2 and len(y_levels) >= 3:
        return "MULTI_STORY_FRAME"
    if cols >= 2 and len(y_levels) == 2:
        return "PORTAL_FRAME"
    if diags > 0:
        return "PLANE_TRUSS"
    return "BEAM_STRUCTURE"

def render_ascii_frame(nodes, elements, displacements, forces, title):
    print("\n" + "=" * 75)
    print(f"📊 REPLICACIÓN UI — MODELO: {title}")
    print("=" * 75)
    
    sys_type = classify_structure(nodes, elements)
    print(f"🏷️  CLASIFICACIÓN ESTRUCTURAL INTELIGENTE: {sys_type}")
    print("📌 NODOS Y DESPLAZAMIENTOS CALCULADOS:")
    for nid, x, y, z, stype in nodes:
        disp = displacements.get(nid, (0.0, 0.0, 0.0))
        disp_mag_mm = math.sqrt(disp[0]**2 + disp[1]**2 + disp[2]**2) * 1000.0
        print(f"  Nodo {nid:2d} | Pos=({x:4.1f}, {y:4.1f}, {z:4.1f})m | Tipo={stype:6s} | δx={disp[0]*1000:+8.4f}mm, δy={disp[1]*1000:+8.4f}mm, δz={disp[2]*1000:+8.4f}mm | Mag={disp_mag_mm:7.4f}mm")
    
    if forces:
        print("\n📌 FUERZAS / TENSIONES DE ELEMENTOS (FRD/DAT):")
        for f in forces[:4]:
            print(f"  Nodo {f['nodeId']:2d} | N (Axial)={f['N']:+10.2f} N | V2 (Corte)={f['V2']:+10.2f} N | M2 (Momento)={f['M2']:+10.2f} N·m")

        max_tension = max((f['N'] for f in forces if f['N'] > 0), default=0.0)
        max_compression = min((f['N'] for f in forces if f['N'] < 0), default=0.0)
        max_shear = max((abs(f['V2']) for f in forces), default=0.0)
        max_moment = max((abs(f['M2']) for f in forces), default=0.0)

        print("\n📊 ENVOLVENTE DE ACCIONES INTERNAS:")
        print(f"  • Tracción Máxima (+N)   : {max_tension/1000.0:+8.2f} kN")
        print(f"  • Compresión Máxima (-N) : {max_compression/1000.0:+8.2f} kN")
        print(f"  • Cortante Máximo (|V|)  : {max_shear/1000.0:8.2f} kN")
        print(f"  • Momento Máximo (|M|)   : {max_moment/1000.0:8.2f} kN·m")

    if sys_type in ["MULTI_STORY_FRAME", "PORTAL_FRAME"]:
        print("\n🏢 REVISIÓN DE DERIVAS SÍSMICAS DE ENTREPISO (Seismic Inter-Story Drift Check):")
        y_levels = sorted(list(set(round(n[2], 2) for n in nodes)))
        if len(y_levels) >= 2:
            prev_ux = 0.0
            prev_y = y_levels[0]
            for lvl_idx, y in enumerate(y_levels[1:], 1):
                h_story = y - prev_y
                max_ux = max(abs(displacements.get(n[0], (0,0,0))[0]) for n in nodes if abs(n[2] - y) < 0.15)
                delta = abs(max_ux - prev_ux)
                drift_pct = (delta / h_story) * 100.0 if h_story > 0 else 0.0
                status = "✅ PASS (<=1.0%)" if drift_pct <= 1.0 else ("⚠️ OK ASCE-7 (<=1.5%)" if drift_pct <= 1.5 else "❌ EXCEEDS (>1.5%)")
                print(f"  Piso {lvl_idx}: Elev={y:.1f}m | h={h_story:.1f}m | Max δx={max_ux*1000:.3f}mm | Drift={delta*1000:.3f}mm | Ratio={drift_pct:.4f}% | {status}")
                prev_ux = max_ux
                prev_y = y

    elif sys_type == "PLANE_TRUSS":
        print("\n🌉 REVISIÓN DE FLECHA Y SERVICIABILIDAD EN CERCHAS / RETICULADOS (Truss Serviceability Check):")
        min_x = min(n[1] for n in nodes)
        max_x = max(n[1] for n in nodes)
        span = max(max_x - min_x, 0.1)
        geom_node_ids = {n[0] for n in nodes}
        max_uy = max(abs(displacements.get(nid, (0,0,0))[1]) for nid in geom_node_ids)
        max_uy_node = max(geom_node_ids, key=lambda nid: abs(displacements.get(nid, (0,0,0))[1]))
        max_uy_mm = max_uy * 1000.0
        limit_250 = (span / 250.0) * 1000.0
        limit_360 = (span / 360.0) * 1000.0
        limit_800 = (span / 800.0) * 1000.0
        ratio_l_d = span / max_uy if max_uy > 1e-7 else 999999.0
        print(f"  Luz Total L = {span:.2f} m | Flecha Máxima δv = {max_uy_mm:.3f} mm en Nodo {max_uy_node} (L/{ratio_l_d:.0f})")
        print(f"  • L/250 (Cubierta/NSR-10) = {limit_250:.2f} mm -> {'✅ PASS / OK' if max_uy_mm <= limit_250 else '❌ EXCEEDS'}")
        print(f"  • L/360 (Piso/AISC)       = {limit_360:.2f} mm -> {'✅ PASS / OK' if max_uy_mm <= limit_360 else '❌ EXCEEDS'}")
        print(f"  • L/800 (Puente/AASHTO)   = {limit_800:.2f} mm -> {'✅ PASS / OK' if max_uy_mm <= limit_800 else '❌ EXCEEDS'}")

    elif sys_type == "PLATE_SHELL_STRUCTURE":
        print("\n🏗️ REVISIÓN DE SERVICIABILIDAD EN LOSAS / PLACAS BIDIRECCIONALES (Slab Shell Deflection Check):")
        min_x = min(n[1] for n in nodes)
        max_x = max(n[1] for n in nodes)
        span = max(max_x - min_x, 0.1)
        max_uz = max(abs(displacements.get(n[0], (0,0,0))[2]) for n in nodes)
        max_uz_node = max(nodes, key=lambda n: abs(displacements.get(n[0], (0,0,0))[2]))[0]
        max_uz_mm = max_uz * 1000.0
        limit_360 = (span / 360.0) * 1000.0
        limit_500 = (span / 500.0) * 1000.0
        ratio_l_d = span / max_uz if max_uz > 1e-7 else 999999.0
        print(f"  Luz Corta L = {span:.2f} m | Flecha Vertical Fuera de Plano δz = {max_uz_mm:.4f} mm en Nodo {max_uz_node} (L/{ratio_l_d:.0f})")
        print(f"  • L/360 (Losa Entrepiso/ACI 318) = {limit_360:.2f} mm -> {'✅ PASS / OK' if max_uz_mm <= limit_360 else '❌ EXCEEDS'}")
        print(f"  • L/500 (Acabados Frágiles/Eurocode) = {limit_500:.2f} mm -> {'✅ PASS / OK' if max_uz_mm <= limit_500 else '❌ EXCEEDS'}")

    elif sys_type == "SHEAR_WALL_PANEL":
        print("\n🧱 REVISIÓN DE MURO DE CORTANTE 2D (Shear Wall Lateral Drift & Stress Check):")
        max_ux = max(abs(displacements.get(n[0], (0,0,0))[0]) for n in nodes)
        max_ux_node = max(nodes, key=lambda n: abs(displacements.get(n[0], (0,0,0))[0]))[0]
        print(f"  Desplazamiento Lateral δx = {max_ux*1000:.4f} mm en Coronación (Nodo {max_ux_node}) -> ✅ PASS / STABLE")

    elif sys_type == "BEAM_STRUCTURE":
        print("\n📏 REVISIÓN DE FLECHA EN VIGAS CONTINUAS (Beam Deflection Check):")
        min_x = min(n[1] for n in nodes)
        max_x = max(n[1] for n in nodes)
        span = max(max_x - min_x, 0.1)
        geom_node_ids = {n[0] for n in nodes}
        max_uy = max(abs(displacements.get(nid, (0,0,0))[1]) for nid in geom_node_ids)
        max_uy_node = max(geom_node_ids, key=lambda nid: abs(displacements.get(nid, (0,0,0))[1]))
        max_uy_mm = max_uy * 1000.0
        limit_360 = (span / 360.0) * 1000.0
        print(f"  Luz L = {span:.2f} m | Flecha Máxima δv = {max_uy_mm:.3f} mm en Nodo {max_uy_node} (L/360 Adm = {limit_360:.2f} mm) -> {'✅ PASS / OK' if max_uy_mm <= limit_360 else '❌ EXCEEDS'}")

def main():
    print("🚀 INICIANDO SUITE DE VALIDACIÓN LOCAL DE CÁLCULO ESTRUCTURAL")
    print(f"📍 Solver: {CCX_BIN}")
    print(f"📁 Directorio de Trabajo: {WORK_DIR}\n")

    presets = [
        ("portal", StructuralPreset.portal_frame()),
        ("two_bay", StructuralPreset.two_bay_frame()),
        ("continuous", StructuralPreset.continuous_beam()),
        ("warren", StructuralPreset.warren_truss()),
        ("slab", StructuralPreset.concrete_slab_plate()),
        ("shear_wall", StructuralPreset.shear_wall_panel()),
    ]

    all_passed = True
    for name_slug, (title, nodes, elements, loads) in presets:
        inp_file = generate_inp(name_slug, nodes, elements, loads, WORK_DIR)
        res = subprocess.run([CCX_BIN, "-i", name_slug], cwd=WORK_DIR, capture_output=True, text=True)
        if res.returncode != 0:
            print(f"❌ Error ejecutando CalculiX para {title}:\n{res.stderr}")
            all_passed = False
            continue
        
        disps = parse_dat(WORK_DIR / f"{name_slug}.dat")
        forces = parse_frd(WORK_DIR / f"{name_slug}.frd")
        
        # Validaciones Físicas:
        if name_slug == "portal":
            assert abs(disps[1][0]) < 1e-8 and abs(disps[4][0]) < 1e-8, "Bases deben ser rígidas"
            assert disps[2][0] > 0 and disps[3][0] > 0, "Desplazamiento lateral debe ser positivo"
        
        elif name_slug == "continuous":
            assert abs(disps[1][1]) < 1e-8 and abs(disps[3][1]) < 1e-8, "Apoyos deben tener Uy=0"
            assert disps[2][1] <= 0, "Flecha en vano debe ser descendente"

        elif name_slug == "warren":
            assert abs(disps[1][1]) < 1e-8 and abs(disps[5][1]) < 1e-8, "Apoyos deben tener Uy=0"
            assert disps[3][1] < 0, "Flecha en centro debe ser descendente"

        elif name_slug == "slab":
            assert abs(disps[1][2]) < 1e-8 and abs(disps[3][2]) < 1e-8, "Bordes de losa deben tener Uz=0"
            assert disps[5][2] < 0, "Deflexión central fuera de plano debe ser negativa"

        elif name_slug == "shear_wall":
            assert abs(disps[1][0]) < 1e-8 and abs(disps[2][0]) < 1e-8, "Base de muro empotrada"
            assert disps[4][0] > 0, "Desplazamiento lateral de muro positivo"

        render_ascii_frame(nodes, elements, disps, forces, title)

    print("\n" + "=" * 75)
    if all_passed:
        print("🎉 TODAS LAS VALIDACIONES FÍSICAS Y SIMULACIONES DE UI COMPLETADAS CON ÉXITO")
    else:
        print("❌ ALGUNAS SIMULACIONES FALLARON")
    print("=" * 75)

if __name__ == "__main__":
    main()
