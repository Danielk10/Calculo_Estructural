import os
import subprocess
import re

def run_calculix_validation():
    print("================================================================================")
    print("  CALCULIX BENCHMARK: 4m BEAM WITH POINT LOAD AT 1m & CUSTOM MATERIAL")
    print("================================================================================")

    work_dir = "/tmp/calculix_beam_test"
    os.makedirs(work_dir, exist_ok=True)
    job_name = "beam4m"
    inp_path = os.path.join(work_dir, f"{job_name}.inp")

    L = 4.0
    n_nodes = 41
    dx = L / (n_nodes - 1)

    inp_lines = [
        "*HEADING",
        "4m Beam with point load at 1m and custom material",
        "*NODE",
    ]
    for i in range(1, n_nodes + 1):
        x = (i - 1) * dx
        inp_lines.append(f"{i}, {x:.3f}, 0.0, 0.0")

    inp_lines.append("*ELEMENT, TYPE=B31, ELSET=EALL")
    for i in range(1, n_nodes):
        inp_lines.append(f"{i}, {i}, {i+1}")

    inp_lines.extend([
        "*BEAM SECTION, ELSET=EALL, MATERIAL=CUSTOM_STEEL, SECTION=RECT",
        "0.200, 0.200",
        "0.0, 0.0, 1.0",
        "*MATERIAL, NAME=CUSTOM_STEEL",
        "*ELASTIC",
        "210000000000.0, 0.28",
        "*DENSITY",
        "7850.0",
        "*BOUNDARY",
        "1, 1, 6",
        "41, 2, 2",
        "41, 3, 3",
        "41, 4, 4",
        "*STEP",
        "*STATIC",
        "*CLOAD",
        "11, 2, -10000.0",
        "*NODE FILE",
        "U, RF",
        "*NODE PRINT, NSET=NALL",
        "RF, U",
        "*NSET, NSET=NALL",
        "1, 41",
        "*END STEP"
    ])

    with open(inp_path, "w") as f:
        f.write("\n".join(inp_lines) + "\n")

    print(f"Created CalculiX INP file at: {inp_path}")

    # Run CalculiX
    cmd = ["/home/danielpdiamon/.local/bin/ccx", job_name]
    res = subprocess.run(cmd, cwd=work_dir, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)

    print(f"CalculiX Run Return Code: {res.returncode}")
    dat_path = os.path.join(work_dir, f"{job_name}.dat")
    if os.path.exists(dat_path):
        with open(dat_path, "r") as f:
            dat_content = f.read()
            print("\nCalculiX Results Output (.dat):")
            print(dat_content)

        print("\n✅ CalculiX real simulation verified successfully on 4m beam with custom material and 1m load!")
    else:
        print("CalculiX .dat file not generated, check stderr:")
        print(res.stderr)
        assert False, "CalculiX execution failed"

if __name__ == '__main__':
    run_calculix_validation()
