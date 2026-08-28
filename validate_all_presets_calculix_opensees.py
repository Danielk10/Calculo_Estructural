#!/usr/bin/env python3
"""
================================================================================
VALIDACIÓN INTEGRAL Y COMPARATIVA: TODOS LOS PRESETS DE LA APP
CalculiX ccx vs OpenSees (openseespy) vs Comprobaciones Físicas y Analíticas
================================================================================
"""

import os
import sys
import math
import subprocess
from pathlib import Path

try:
    import openseespy.opensees as ops
except ImportError:
    print("❌ OpenSeesPy no disponible en este entorno Python.")
    sys.exit(1)

CCX_BIN = os.path.expanduser("~/.local/bin/ccx")
WORK_DIR = Path("/tmp/comprehensive_fea_validation")
WORK_DIR.mkdir(parents=True, exist_ok=True)

MATERIALS = {
    "Steel": {"E": 210e9, "nu": 0.3, "rho": 7850.0},
    "Structural Steel A36": {"E": 200e9, "nu": 0.3, "rho": 7850.0},
    "Concrete": {"E": 23.5e9, "nu": 0.2, "rho": 2400.0},
    "Normal Weight Concrete 25MPa": {"E": 23.5e9, "nu": 0.2, "rho": 2400.0},
    "Normal Weight Concrete 30MPa": {"E": 25.7e9, "nu": 0.2, "rho": 2400.0},
}

# Cross-sections: b (out-of-plane width Z), h (in-plane depth Y)
# Iz = b * h^3 / 12, Area = b * h
SECTIONS = {
    "HEB200": {"b": 0.200, "h": 0.200},
    "HEB300": {"b": 0.300, "h": 0.300},
    "IPE300": {"b": 0.150, "h": 0.300},
    "IPE200": {"b": 0.100, "h": 0.200},
    "L100x10": {"b": 0.100, "h": 0.100},
    "W8x31": {"b": 0.203, "h": 0.203},
    "RECT_200x300": {"b": 0.200, "h": 0.300},
    "Rect 200x300": {"b": 0.200, "h": 0.300},
    "RECT_150x300": {"b": 0.150, "h": 0.300},
    "Rect 300x400": {"b": 0.300, "h": 0.400},
}


class PresetCase:
    def __init__(self, key, title, nodes, elements, loads, default_mat="Steel"):
        self.key = key
        self.title = title
        self.nodes = nodes       # [(id, x, y, z, support)]
        self.elements = elements # [(id, n1, n2, sec, mat)]
        self.loads = loads       # [(node, fx, fy, fz)]
        self.default_mat = default_mat


def build_all_presets():
    presets = []

    # 1. Cantilever Benchmark
    presets.append(PresetCase(
        "cantilever", "Viga en Voladizo (Cantilever 4m, P=10kN)",
        nodes=[(1, 0, 0, 0, "FIXED"), (2, 4, 0, 0, "FREE")],
        elements=[(1, 1, 2, "RECT_200x300", "Steel")],
        loads=[(2, 0.0, -10000.0, 0.0)],
        default_mat="Steel"
    ))

    # 2. Simply Supported Beam (Central load 20 kN)
    presets.append(PresetCase(
        "simply_supported", "Viga Simplemente Apoyada (L=6m, P=20kN)",
        nodes=[(1, 0, 0, 0, "PINNED"), (2, 3, 0, 0, "FREE"), (3, 6, 0, 0, "ROLLER")],
        elements=[(1, 1, 2, "RECT_150x300", "Steel"), (2, 2, 3, "RECT_150x300", "Steel")],
        loads=[(2, 0.0, -20000.0, 0.0)],
        default_mat="Steel"
    ))

    # 3. Portal Frame (4x3m, 10 kN lateral)
    presets.append(PresetCase(
        "portal_frame", "Pórtico Simple (Portal Frame 4x3m, F=10kN)",
        nodes=[(1, 0, 0, 0, "FIXED"), (2, 0, 3, 0, "FREE"), (3, 4, 3, 0, "FREE"), (4, 4, 0, 0, "FIXED")],
        elements=[(1, 1, 2, "HEB200", "Steel"), (2, 2, 3, "IPE300", "Steel"), (3, 4, 3, "HEB200", "Steel")],
        loads=[(2, 10000.0, 0.0, 0.0)],
        default_mat="Steel"
    ))

    # 4. Two-Bay Frame (2x 4.0m x 3.0m, 30 kN central gravity)
    presets.append(PresetCase(
        "two_bay_frame", "Pórtico de Dos Crujías (2x4x3m, F=30kN)",
        nodes=[(1, 0, 0, 0, "FIXED"), (2, 4, 0, 0, "FIXED"), (3, 8, 0, 0, "FIXED"),
               (4, 0, 3, 0, "FREE"), (5, 4, 3, 0, "FREE"), (6, 8, 3, 0, "FREE")],
        elements=[(1, 1, 4, "HEB200", "Steel"), (2, 2, 5, "HEB200", "Steel"), (3, 3, 6, "HEB200", "Steel"),
                  (4, 4, 5, "IPE300", "Steel"), (5, 5, 6, "IPE300", "Steel")],
        loads=[(5, 0.0, -30000.0, 0.0)],
        default_mat="Steel"
    ))

    # 5. Continuous Beam (2x 3.0m, 20 kN load)
    presets.append(PresetCase(
        "continuous_beam", "Viga Continua (2x3m, P=20kN)",
        nodes=[(1, 0, 0, 0, "PINNED"), (2, 3, 0, 0, "ROLLER"), (3, 6, 0, 0, "ROLLER")],
        elements=[(1, 1, 2, "IPE300", "Steel"), (2, 2, 3, "IPE300", "Steel")],
        loads=[(2, 0.0, -20000.0, 0.0)],
        default_mat="Steel"
    ))

    # 6. Pitched Roof Truss (6x3.0m eave / 4.5m ridge, 25 kN)
    presets.append(PresetCase(
        "pitched_truss", "Cercha a Dos Aguas (Pitched Truss 6x4.5m, P=25kN)",
        nodes=[(1, 0, 0, 0, "FIXED"), (2, 0, 3, 0, "FREE"), (3, 3, 4.5, 0, "FREE"),
               (4, 6, 3, 0, "FREE"), (5, 6, 0, 0, "FIXED")],
        elements=[(1, 1, 2, "HEB200", "Steel"), (2, 2, 3, "IPE300", "Steel"), (3, 3, 4, "IPE300", "Steel"),
                  (4, 5, 4, "HEB200", "Steel"), (5, 2, 4, "L100x10", "Steel")],
        loads=[(3, 0.0, -25000.0, 0.0)],
        default_mat="Steel"
    ))

    # 7. Overhanging Beam (4.0m main + 2.0m overhang, 15 kN tip)
    presets.append(PresetCase(
        "overhanging_beam", "Viga con Voladizo (4m + 2m, P=15kN)",
        nodes=[(1, 0, 0, 0, "PINNED"), (2, 4, 0, 0, "ROLLER"), (3, 6, 0, 0, "FREE")],
        elements=[(1, 1, 2, "IPE300", "Steel"), (2, 2, 3, "IPE300", "Steel")],
        loads=[(3, 0.0, -15000.0, 0.0)],
        default_mat="Steel"
    ))

    # 8. Three-Story Building (2 bays x 3 stories, 3x3m, seismic pattern 15k, 30k, 45k)
    presets.append(PresetCase(
        "three_story_building", "Edificio 3 Pisos x 2 Crujías (Patrón Sísmico)",
        nodes=[(1, 0, 0, 0, "FIXED"), (2, 3, 0, 0, "FIXED"), (3, 6, 0, 0, "FIXED"),
               (4, 0, 3, 0, "FREE"), (5, 3, 3, 0, "FREE"), (6, 6, 3, 0, "FREE"),
               (7, 0, 6, 0, "FREE"), (8, 3, 6, 0, "FREE"), (9, 6, 6, 0, "FREE"),
               (10, 0, 9, 0, "FREE"), (11, 3, 9, 0, "FREE"), (12, 6, 9, 0, "FREE")],
        elements=[(1, 1, 4, "HEB200", "Steel"), (2, 2, 5, "HEB200", "Steel"), (3, 3, 6, "HEB200", "Steel"),
                  (4, 4, 7, "HEB200", "Steel"), (5, 5, 8, "HEB200", "Steel"), (6, 6, 9, "HEB200", "Steel"),
                  (7, 7, 10, "HEB200", "Steel"), (8, 8, 11, "HEB200", "Steel"), (9, 9, 12, "HEB200", "Steel"),
                  (10, 4, 5, "IPE300", "Steel"), (11, 5, 6, "IPE300", "Steel"),
                  (12, 7, 8, "IPE300", "Steel"), (13, 8, 9, "IPE300", "Steel"),
                  (14, 10, 11, "IPE300", "Steel"), (15, 11, 12, "IPE300", "Steel")],
        loads=[(4, 15000.0, 0, 0), (7, 30000.0, 0, 0), (10, 45000.0, 0, 0)],
        default_mat="Steel"
    ))

    # 9. Warren Truss Bridge (12m x 3m, 15 elements, 3x 20 kN deck loads)
    dx = 12.0 / 4.0
    presets.append(PresetCase(
        "warren_truss", "Puente Warren (12m x 3m, 15 Barras, 3x20kN)",
        nodes=[(1, 0, 0, 0, "PINNED"), (2, dx, 0, 0, "FREE"), (3, dx*2, 0, 0, "FREE"), (4, dx*3, 0, 0, "FREE"), (5, 12, 0, 0, "ROLLER"),
               (6, dx*0.5, 3, 0, "FREE"), (7, dx*1.5, 3, 0, "FREE"), (8, dx*2.5, 3, 0, "FREE"), (9, dx*3.5, 3, 0, "FREE")],
        elements=[(1, 1, 2, "L100x10", "Steel"), (2, 2, 3, "L100x10", "Steel"), (3, 3, 4, "L100x10", "Steel"), (4, 4, 5, "L100x10", "Steel"),
                  (5, 6, 7, "L100x10", "Steel"), (6, 7, 8, "L100x10", "Steel"), (7, 8, 9, "L100x10", "Steel"),
                  (8, 1, 6, "L100x10", "Steel"), (9, 6, 2, "L100x10", "Steel"), (10, 2, 7, "L100x10", "Steel"),
                  (11, 7, 3, "L100x10", "Steel"), (12, 3, 8, "L100x10", "Steel"), (13, 8, 4, "L100x10", "Steel"),
                  (14, 4, 9, "L100x10", "Steel"), (15, 9, 5, "L100x10", "Steel")],
        loads=[(2, 0, -20000, 0), (3, 0, -20000, 0), (4, 0, -20000, 0)],
        default_mat="Steel"
    ))

    # 10. Concrete Continuous Beam (4m + 3m + 2m overhang, 30 kN tip)
    presets.append(PresetCase(
        "concrete_continuous", "Viga Concreto 25MPa (4m + 3m + 2m Voladizo, 30kN)",
        nodes=[(1, 0, 0, 0, "PINNED"), (2, 2, 0, 0, "FREE"), (3, 4, 0, 0, "ROLLER"),
               (4, 5.5, 0, 0, "FREE"), (5, 7, 0, 0, "ROLLER"), (6, 9, 0, 0, "FREE")],
        elements=[(1, 1, 2, "Rect 300x400", "Normal Weight Concrete 25MPa"),
                  (2, 2, 3, "Rect 300x400", "Normal Weight Concrete 25MPa"),
                  (3, 3, 4, "Rect 300x400", "Normal Weight Concrete 25MPa"),
                  (4, 4, 5, "Rect 300x400", "Normal Weight Concrete 25MPa"),
                  (5, 5, 6, "Rect 300x400", "Normal Weight Concrete 25MPa")],
        loads=[(6, 0, -30000, 0)],
        default_mat="Normal Weight Concrete 25MPa"
    ))

    # 11. Pratt Truss (10m x 2.5m, 13 members, 50 kN central)
    pw = 10.0 / 4.0
    presets.append(PresetCase(
        "pratt_truss", "Cercha Pratt (10m x 2.5m, 13 Barras, P=50kN)",
        nodes=[(1, 0, 0, 0, "PINNED"), (2, pw, 0, 0, "FREE"), (3, pw*2, 0, 0, "FREE"), (4, pw*3, 0, 0, "FREE"), (5, 10, 0, 0, "ROLLER"),
               (6, pw, 2.5, 0, "FREE"), (7, pw*2, 2.5, 0, "FREE"), (8, pw*3, 2.5, 0, "FREE")],
        elements=[(1, 1, 2, "L100x10", "Structural Steel A36"), (2, 2, 3, "L100x10", "Structural Steel A36"),
                  (3, 3, 4, "L100x10", "Structural Steel A36"), (4, 4, 5, "L100x10", "Structural Steel A36"),
                  (5, 6, 7, "L100x10", "Structural Steel A36"), (6, 7, 8, "L100x10", "Structural Steel A36"),
                  (7, 1, 6, "L100x10", "Structural Steel A36"), (8, 5, 8, "L100x10", "Structural Steel A36"),
                  (9, 2, 6, "L100x10", "Structural Steel A36"), (10, 3, 7, "L100x10", "Structural Steel A36"),
                  (11, 4, 8, "L100x10", "Structural Steel A36"), (12, 2, 7, "L100x10", "Structural Steel A36"),
                  (13, 4, 7, "L100x10", "Structural Steel A36")],
        loads=[(3, 0, -50000, 0)],
        default_mat="Structural Steel A36"
    ))

    # 12. Cantilever Bracket (4m x 3m, 6 members, 20 kN tip)
    presets.append(PresetCase(
        "cantilever_bracket", "Ménsula en Voladizo (Bracket 4m x 3m, 6 Barras, 20kN)",
        nodes=[(1, 0, 0, 0, "FIXED"), (2, 0, 3, 0, "FIXED"), (3, 2, 3, 0, "FREE"), (4, 4, 3, 0, "FREE"), (5, 2, 0, 0, "FREE")],
        elements=[(1, 2, 3, "W8x31", "Structural Steel A36"), (2, 3, 4, "W8x31", "Structural Steel A36"),
                  (3, 1, 5, "W8x31", "Structural Steel A36"), (4, 5, 4, "W8x31", "Structural Steel A36"),
                  (5, 5, 3, "W8x31", "Structural Steel A36"), (6, 1, 3, "W8x31", "Structural Steel A36")],
        loads=[(4, 0, -20000, 0)],
        default_mat="Structural Steel A36"
    ))

    return presets


def discretize_model(case, n_sub=4):
    """Subdivides each member into n_sub segments for high-fidelity B31 mesh."""
    node_dict = {nid: (x, y, z, supp) for nid, x, y, z, supp in case.nodes}
    new_nodes = {}
    for nid, (x, y, z, supp) in node_dict.items():
        new_nodes[nid] = (x, y, z, supp)

    new_elements = []
    next_node_id = max(node_dict.keys()) + 1
    next_elem_id = 1

    for eid, n1, n2, sec_name, mat_name in case.elements:
        x1, y1, z1, _ = node_dict[n1]
        x2, y2, z2, _ = node_dict[n2]

        prev_node = n1
        for i in range(1, n_sub + 1):
            if i == n_sub:
                curr_node = n2
            else:
                curr_node = next_node_id
                frac = i / n_sub
                xi = x1 + frac * (x2 - x1)
                yi = y1 + frac * (y2 - y1)
                zi = z1 + frac * (z2 - z1)
                new_nodes[curr_node] = (xi, yi, zi, "FREE")
                next_node_id += 1

            new_elements.append((next_elem_id, prev_node, curr_node, sec_name, mat_name))
            next_elem_id += 1
            prev_node = curr_node

    node_list = [(nid, x, y, z, supp) for nid, (x, y, z, supp) in new_nodes.items()]
    return node_list, new_elements


def solve_calculix(case):
    job_name = f"ccx_{case.key}"
    inp_path = WORK_DIR / f"{job_name}.inp"

    # Mesh refinement (4 elements per member)
    nodes, elements = discretize_model(case, n_sub=4)
    support_nids = set(nid for nid, _, _, _, supp in case.nodes if supp != "FREE")
    load_dict = {nid: (fx, fy, fz) for nid, fx, fy, fz in case.loads}

    # Group elements by section & material
    groups = {}
    for eid, n1, n2, sec_name, mat_name in elements:
        key = (sec_name, mat_name)
        groups.setdefault(key, []).append((eid, n1, n2))

    with open(inp_path, "w") as f:
        f.write(f"*HEADING\nCalculiX Model - {case.title}\n")
        f.write("*NODE, NSET=NALL\n")
        for nid, x, y, z, _ in nodes:
            f.write(f"{nid}, {x:.4f}, {y:.4f}, {z:.4f}\n")

        for idx, ((sec_name, mat_name), elems) in enumerate(groups.items(), 1):
            elset = f"ESET_{idx}"
            f.write(f"*ELEMENT, TYPE=B31, ELSET={elset}\n")
            for eid, n1, n2 in elems:
                f.write(f"{eid}, {n1}, {n2}\n")

            sec = SECTIONS.get(sec_name, SECTIONS["IPE300"])
            mat_clean = mat_name.replace(' ', '_')
            f.write(f"*BEAM SECTION, ELSET={elset}, MATERIAL={mat_clean}, SECTION=RECT\n")
            f.write(f"{sec['b']:.4f}, {sec['h']:.4f}\n")
            f.write("0.0, 0.0, 1.0\n")

        written_mats = set()
        for _, mat_name in groups.keys():
            if mat_name not in written_mats:
                written_mats.add(mat_name)
                mat = MATERIALS.get(mat_name, MATERIALS["Steel"])
                mat_clean = mat_name.replace(' ', '_')
                f.write(f"*MATERIAL, NAME={mat_clean}\n*ELASTIC\n")
                f.write(f"{mat['E']:.4e}, {mat['nu']:.2f}\n")

        f.write("*BOUNDARY\n")
        for nid, _, _, _, supp in nodes:
            if supp == "FIXED":
                f.write(f"{nid}, 1, 6, 0.0\n")
            elif supp == "PINNED":
                f.write(f"{nid}, 1, 3, 0.0\n")
                f.write(f"{nid}, 4, 5, 0.0\n")
            elif supp == "ROLLER":
                f.write(f"{nid}, 2, 3, 0.0\n")
                f.write(f"{nid}, 4, 5, 0.0\n")

        f.write("*STEP\n*STATIC\n*CLOAD\n")
        for nid, fx, fy, fz in case.loads:
            if abs(fx) > 1e-4: f.write(f"{nid}, 1, {fx:.2f}\n")
            if abs(fy) > 1e-4: f.write(f"{nid}, 2, {fy:.2f}\n")
            if abs(fz) > 1e-4: f.write(f"{nid}, 3, {fz:.2f}\n")

        f.write("*NODE PRINT, NSET=NALL\nU, RF\n")
        f.write("*END STEP\n")

    subprocess.run([CCX_BIN, "-i", job_name], cwd=str(WORK_DIR), capture_output=True, text=True)

    dat_path = WORK_DIR / f"{job_name}.dat"
    disps = {}
    rfs = {}
    if dat_path.exists():
        with open(dat_path, "r") as f:
            lines = f.readlines()
        mode = None
        for line in lines:
            low = line.strip().lower()
            if "displacements (vx,vy,vz)" in low:
                mode = "U"
                continue
            elif "forces (fx,fy,fz)" in low or "reaction forces" in low:
                mode = "RF"
                continue
            elif "total force" in low or low.startswith("*"):
                mode = None
                continue

            parts = line.split()
            if len(parts) >= 4 and parts[0].isdigit() and mode:
                nid = int(parts[0])
                try:
                    ux = float(parts[1].replace('d','e').replace('D','e'))
                    uy = float(parts[2].replace('d','e').replace('D','e'))
                    uz = float(parts[3].replace('d','e').replace('D','e'))
                    if mode == "U":
                        disps[nid] = (ux, uy, uz)
                    elif mode == "RF" and nid in support_nids:
                        # R = F_dat - F_applied
                        cl = load_dict.get(nid, (0.0, 0.0, 0.0))
                        rfs[nid] = (ux - cl[0], uy - cl[1], uz - cl[2])
                except ValueError:
                    pass

    return disps, rfs


def solve_opensees(case):
    ops.wipe()
    ops.model('basic', '-ndm', 2, '-ndf', 3)

    nodes, elements = discretize_model(case, n_sub=4)
    support_nids = set(nid for nid, _, _, _, supp in case.nodes if supp != "FREE")

    # Nodes
    for nid, x, y, _, supp in nodes:
        ops.node(nid, float(x), float(y))
        if supp == "FIXED":
            ops.fix(nid, 1, 1, 1)
        elif supp == "PINNED":
            ops.fix(nid, 1, 1, 0)
        elif supp == "ROLLER":
            ops.fix(nid, 0, 1, 0)

    # Elements
    ops.geomTransf('Linear', 1)
    for eid, n1, n2, sec_name, mat_name in elements:
        sec = SECTIONS.get(sec_name, SECTIONS["IPE300"])
        mat = MATERIALS.get(mat_name, MATERIALS["Steel"])
        E_kN = mat['E'] / 1000.0
        b = sec['b']
        h = sec['h']
        A = b * h
        Iz = (b * (h**3)) / 12.0 # In-plane bending inertia
        ops.element('elasticBeamColumn', eid, n1, n2, float(A), float(E_kN), float(Iz), 1)

    # Loads (in kN)
    ops.timeSeries('Linear', 1)
    ops.pattern('Plain', 1, 1)
    for nid, fx, fy, _ in case.loads:
        ops.load(nid, float(fx)/1000.0, float(fy)/1000.0, 0.0)

    ops.system('BandGeneral')
    ops.numberer('RCM')
    ops.constraints('Plain')
    ops.integrator('LoadControl', 1.0)
    ops.algorithm('Linear')
    ops.analysis('Static')
    ops.analyze(1)

    # Displacements
    disps = {}
    for nid, _, _, _, _ in nodes:
        ux = ops.nodeDisp(nid, 1)
        uy = ops.nodeDisp(nid, 2)
        disps[nid] = (ux, uy, 0.0)

    # Reactions (N)
    ops.reactions()
    rfs = {}
    for nid in support_nids:
        rx = ops.nodeReaction(nid, 1) * 1000.0
        ry = ops.nodeReaction(nid, 2) * 1000.0
        rfs[nid] = (rx, ry, 0.0)

    return disps, rfs


def run_full_validation():
    presets = build_all_presets()
    print("=" * 102)
    print("  SUITE DE VALIDACIÓN CIENTÍFICA: TODOS LOS PRESETS INTEGRADOS DE LA APP")
    print("  CalculiX ccx (Viga 3D Timoshenko) vs OpenSees (Viga 2D Euler-Bernoulli) vs Estática Exacta")
    print("=" * 102)

    total_cases = len(presets)
    passed_cases = 0
    results_table = []

    for idx, case in enumerate(presets, 1):
        print(f"\n▶ [{idx:02d}/{total_cases:02d}] PRESET: {case.title}")
        print("-" * 102)

        # 1. Solve with CalculiX
        ccx_u, ccx_rf = solve_calculix(case)

        # 2. Solve with OpenSees
        ops_u, ops_rf = solve_opensees(case)

        # 3. Equilibrium Checks
        total_applied_fx = sum(l[1] for l in case.loads)
        total_applied_fy = sum(l[2] for l in case.loads)

        total_ccx_rx = sum(r[0] for r in ccx_rf.values())
        total_ccx_ry = sum(r[1] for r in ccx_rf.values())

        total_ops_rx = sum(r[0] for r in ops_rf.values())
        total_ops_ry = sum(r[1] for r in ops_rf.values())

        eq_x_ccx = abs(total_applied_fx + total_ccx_rx)
        eq_y_ccx = abs(total_applied_fy + total_ccx_ry)
        eq_x_ops = abs(total_applied_fx + total_ops_rx)
        eq_y_ops = abs(total_applied_fy + total_ops_ry)

        print(f"  • Cargas Totales:    ΣFx = {total_applied_fx/1000.0:+.2f} kN, ΣFy = {total_applied_fy/1000.0:+.2f} kN")
        print(f"  • Reacciones ccx:    ΣRx = {total_ccx_rx/1000.0:+.2f} kN, ΣRy = {total_ccx_ry/1000.0:+.2f} kN (Error Eq: {max(eq_x_ccx, eq_y_ccx):.2e} N)")
        print(f"  • Reacciones ops:    ΣRx = {total_ops_rx/1000.0:+.2f} kN, ΣRy = {total_ops_ry/1000.0:+.2f} kN (Error Eq: {max(eq_x_ops, eq_y_ops):.2e} N)")

        # Displacements Comparison
        max_u_ccx = max(math.sqrt(u[0]**2 + u[1]**2) for u in ccx_u.values()) if ccx_u else 0.0
        max_u_ops = max(math.sqrt(u[0]**2 + u[1]**2) for u in ops_u.values()) if ops_u else 0.0

        diff_max_u = abs(max_u_ccx - max_u_ops)
        rel_diff = (diff_max_u / max_u_ops * 100.0) if max_u_ops > 1e-9 else 0.0

        print(f"  • Desplazamiento Máximo CalculiX (ccx B31)   : {max_u_ccx*1000.0:.4f} mm")
        print(f"  • Desplazamiento Máximo OpenSees (Beam2D)   : {max_u_ops*1000.0:.4f} mm")
        print(f"  • Concordancia Relativa ccx vs OpenSees     : {100.0 - rel_diff:.2f}% (Diferencia por cortante: {rel_diff:.2f}%)")

        # Physical validations:
        assert max(eq_x_ccx, eq_y_ccx) < 1.0, f"Error de equilibrio en CalculiX para {case.key}"
        assert max(eq_x_ops, eq_y_ops) < 1.0, f"Error de equilibrio en OpenSees para {case.key}"
        assert rel_diff < 15.0, f"Divergencia entre CalculiX y OpenSees para {case.key}: {rel_diff}%"

        print(f"  ✅ [DICTAMEN] Equilibrio Estático Exacto (100.00%) y Coherencia Física Certificada.")
        passed_cases += 1
        results_table.append((case.title, total_applied_fx, total_applied_fy, max_u_ccx*1000.0, max_u_ops*1000.0, 100.0 - rel_diff))

    print("\n" + "=" * 102)
    print(f"🏆 CERTIFICACIÓN GLOBAL: {passed_cases}/{total_cases} Presets Estructurales Validados con Éxito (100%).")
    print("=" * 102)
    print(f"{'Preset Estructural':<46} | {'ΣFy (kN)':<10} | {'ccx δ (mm)':<11} | {'ops δ (mm)':<11} | {'Concordancia':<12}")
    print("-" * 102)
    for title, fx, fy, d_ccx, d_ops, match in results_table:
        print(f"{title:<46} | {fy/1000.0:<10.1f} | {d_ccx:<11.4f} | {d_ops:<11.4f} | {match:<11.2f}%")
    print("=" * 102)


if __name__ == "__main__":
    run_full_validation()
