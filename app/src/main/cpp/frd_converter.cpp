#ifndef STANDALONE_TEST
#include <jni.h>
#endif
#include <string>
#include <vector>
#include <fstream>
#include <sstream>
#include <map>
#include <algorithm>
#include <cmath>
#include <limits>
#ifndef STANDALONE_TEST
#include <android/log.h>
#define LOG_TAG "FRD_CONVERTER"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
#include <cstdio>
#define LOGI(...) do { printf("INFO: "); printf(__VA_ARGS__); printf("\n"); } while(0)
#define LOGE(...) do { printf("ERROR: "); printf(__VA_ARGS__); printf("\n"); } while(0)
#endif

#define TINYGLTF_IMPLEMENTATION
#define STB_IMAGE_IMPLEMENTATION
#define STB_IMAGE_WRITE_IMPLEMENTATION
#define TINYGLTF_NO_EXTERNAL_IMAGE
#define TINYGLTF_NO_STB_IMAGE
#define TINYGLTF_NO_STB_IMAGE_WRITE
#include "include/tiny_gltf.h"

// ─── Data Types ──────────────────────────────────────────────────────────

struct Node  { float x, y, z; float dx=0.0f, dy=0.0f, dz=0.0f; };
struct Element { std::vector<int> nodes; };

// Nodal result: Von Mises stress computed from the 6 stress tensor components
struct Result { float vonMises; };

// ─── Rainbow colormap for stress visualization ──────────────────────────────
static void get_color(float value, float min_v, float max_v, float rgb[3]) {
    float ratio = (max_v > min_v) ? (value - min_v) / (max_v - min_v) : 0.5f;
    ratio = std::max(0.0f, std::min(1.0f, ratio));  // clamp [0,1]
    rgb[0] = std::max(0.0f, 2.0f * ratio - 1.0f);
    rgb[1] = 1.0f - std::abs(2.0f * ratio - 1.0f);
    rgb[2] = std::max(0.0f, 1.0f - 2.0f * ratio);
}

// ─── Bounding box utilities and model centering ─────────────────────────────
struct BBox {
    float xMin, xMax, yMin, yMax, zMin, zMax;
    BBox()
        : xMin( std::numeric_limits<float>::max()),
          xMax(-std::numeric_limits<float>::max()),
          yMin( std::numeric_limits<float>::max()),
          yMax(-std::numeric_limits<float>::max()),
          zMin( std::numeric_limits<float>::max()),
          zMax(-std::numeric_limits<float>::max()) {}
    void expand(float x, float y, float z) {
        xMin = std::min(xMin, x); xMax = std::max(xMax, x);
        yMin = std::min(yMin, y); yMax = std::max(yMax, y);
        zMin = std::min(zMin, z); zMax = std::max(zMax, z);
    }
    float maxExtent() const {
        return std::max({xMax - xMin, yMax - yMin, zMax - zMin});
    }
    float cx() const { return (xMin + xMax) * 0.5f; }
    float cy() const { return (yMin + yMax) * 0.5f; }
    float cz() const { return (zMin + zMax) * 0.5f; }
};

// ─── FRD Fixed-Width Column Parser ──────────────────────────────────────────
// CalculiX FRD format uses Fortran fixed-width columns (NOT space-separated):
//   Columns 1-3:   " -1" (record marker)
//   Columns 4-13:  Node ID (10 chars, right-justified integer)
//   Columns 14-25: Value 1 (12 chars, scientific notation)
//   Columns 26-37: Value 2 (12 chars)
//   Columns 38-49: Value 3 (12 chars)
//   ... (up to 6 values for STRESS, 3 for DISP/coords)
//
// Negative values concatenate without spaces: "1-2.46132E-14" 
// This BREAKS std::stringstream parsing — must use fixed-width extraction.

// Parse a fixed-width float value from a string at given position
static bool parse_fixed_float(const std::string& line, int pos, int width, float& out) {
    if (pos + width > (int)line.size()) return false;
    std::string s = line.substr(pos, width);
    // Trim whitespace
    size_t start = s.find_first_not_of(" \t");
    if (start == std::string::npos) return false;
    try {
        out = std::stof(s.substr(start));
        return true;
    } catch (...) {
        return false;
    }
}

// Parse a fixed-width integer from a string at given position
static bool parse_fixed_int(const std::string& line, int pos, int width, int& out) {
    if (pos + width > (int)line.size()) return false;
    std::string s = line.substr(pos, width);
    size_t start = s.find_first_not_of(" \t");
    if (start == std::string::npos) return false;
    try {
        out = std::stoi(s.substr(start));
        return true;
    } catch (...) {
        return false;
    }
}

// Calculate Von Mises stress from 6 tensor components
static float calc_von_mises(float sxx, float syy, float szz, float sxy, float syz, float szx) {
    float d1 = sxx - syy;
    float d2 = syy - szz;
    float d3 = szz - sxx;
    return std::sqrt(0.5f * (d1*d1 + d2*d2 + d3*d3 + 6.0f*(sxy*sxy + syz*syz + szx*szx)));
}

static bool parseFRD(std::istream& frd,
                      std::map<int, Node>& nodes,
                      std::vector<Element>& elements,
                      std::map<int, Result>& results,
                      float& min_stress, float& max_stress)
{
    min_stress =  std::numeric_limits<float>::max();
    max_stress = -std::numeric_limits<float>::max();

    std::string line;
    bool inNodeBlock    = false;
    bool inElemBlock    = false;
    bool inResultBlock  = false;
    bool inDispBlock    = false;
    int  stressNumComp  = 0;  // Number of stress components (from -4 header)

    Element currentElement;
    bool hasPendingElement = false;

    while (std::getline(frd, line)) {
        if (line.empty()) continue;

        // Use the raw line for fixed-width parsing. Also compute a trimmed version
        // for block-header detection.
        std::string trimmed = line;
        trimmed.erase(0, trimmed.find_first_not_of(" \t"));
        trimmed.erase(trimmed.find_last_not_of(" \t\r\n") + 1);

        if (trimmed.empty()) continue;

        // ── Detect end marker: "9999" ─────────────────────────────────────────
        if (trimmed.find("9999") == 0) break;

        // ── Detect block header: "2C" → nodes ────────────────────────────────
        if (trimmed.size() >= 2 && trimmed.substr(0, 2) == "2C") {
            inNodeBlock = true;
            inElemBlock = false;
            inResultBlock = false;
            inDispBlock = false;
            continue;
        }
        // ── Detect block header: "3C" → elements ─────────────────────────────
        if (trimmed.size() >= 2 && trimmed.substr(0, 2) == "3C") {
            inElemBlock = true;
            inNodeBlock = false;
            inResultBlock = false;
            inDispBlock = false;
            if (hasPendingElement) {
                elements.push_back(currentElement);
                hasPendingElement = false;
            }
            continue;
        }

        // ── Detect result block headers: "-4  STRESS" or "-4  DISP" ──────────
        // Only match "-4" header lines, not "-5" sub-headers or data lines
        if (trimmed.size() >= 2 && trimmed.substr(0, 2) == "-4") {
            if (hasPendingElement) {
                elements.push_back(currentElement);
                hasPendingElement = false;
            }
            if (trimmed.find("STRESS") != std::string::npos ||
                trimmed.find("CLSTRESS") != std::string::npos) {
                inResultBlock = true;
                inDispBlock = false;
                inNodeBlock = false;
                inElemBlock = false;
                // Parse number of components from header: "-4  STRESS      6    1"
                // The number after the name is the component count
                std::stringstream ss(trimmed.substr(2));
                std::string name;
                int numComp = 6;
                if (ss >> name >> numComp) {
                    stressNumComp = numComp;
                }
                LOGI("Detected STRESS block with %d components", stressNumComp);
                continue;
            }
            if (trimmed.find("DISP") != std::string::npos) {
                inResultBlock = false;
                inDispBlock = true;
                inNodeBlock = false;
                inElemBlock = false;
                LOGI("Detected DISP block");
                continue;
            }
            // Other -4 blocks (ERROR, etc.): skip
            inResultBlock = false;
            inDispBlock = false;
            inNodeBlock = false;
            inElemBlock = false;
            continue;
        }

        // ── Skip -5 sub-header lines (component descriptors) ─────────────────
        if (trimmed.size() >= 2 && trimmed.substr(0, 2) == "-5") {
            continue;
        }

        // ── End of block: "-3" ───────────────────────────────────────────────
        if (trimmed.size() >= 2 && trimmed.substr(0, 2) == "-3") {
            inNodeBlock = false;
            inElemBlock = false;
            inResultBlock = false;
            inDispBlock = false;
            if (hasPendingElement) {
                elements.push_back(currentElement);
                hasPendingElement = false;
            }
            continue;
        }

        // ── Skip 1P/100CL header lines ───────────────────────────────────────
        if (trimmed.size() >= 2 && (trimmed[0] == '1' || trimmed.substr(0,3) == "100")) {
            continue;
        }

        // ── Data lines start with " -1" or " -2" ────────────────────────────
        // Use the RAW line (not trimmed) for fixed-width column parsing

        // ── NODE DATA: " -1  <id10>  <x12>  <y12>  <z12>" ───────────────────
        if (inNodeBlock && line.size() >= 3 && line.substr(0, 3) == " -1") {
            int id; float x, y, z;
            // Col 3-12: node id (10 chars), Col 13-24: x, Col 25-36: y, Col 37-48: z
            if (parse_fixed_int(line, 3, 10, id) &&
                parse_fixed_float(line, 13, 12, x) &&
                parse_fixed_float(line, 25, 12, y) &&
                parse_fixed_float(line, 37, 12, z)) {
                nodes[id] = {x, y, z};
            }
            continue;
        }

        // ── ELEMENT HEADER: " -1  <id>  <type>  ..." ────────────────────────
        if (inElemBlock && line.size() >= 3 && line.substr(0, 3) == " -1") {
            if (hasPendingElement) {
                elements.push_back(currentElement);
            }
            currentElement = Element();
            hasPendingElement = true;
            continue;
        }
        // ── ELEMENT CONNECTIVITY: " -2  <n1>  <n2>  ..." ────────────────────
        if (inElemBlock && line.size() >= 3 && line.substr(0, 3) == " -2" && hasPendingElement) {
            // Connectivity uses 10-char wide columns after the " -2" prefix
            int pos = 3;
            while (pos + 10 <= (int)line.size()) {
                int nid;
                if (parse_fixed_int(line, pos, 10, nid)) {
                    currentElement.nodes.push_back(nid);
                }
                pos += 10;
            }
            continue;
        }

        // ── STRESS DATA: " -1  <id10>  <SXX12> <SYY12> <SZZ12> <SXY12> <SYZ12> <SZX12>"
        if (inResultBlock && line.size() >= 3 && line.substr(0, 3) == " -1") {
            int id;
            if (!parse_fixed_int(line, 3, 10, id)) continue;

            // Read up to 6 stress components using fixed-width columns
            float vals[6] = {0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};
            int numRead = 0;
            int pos = 13;
            while (numRead < 6 && pos + 12 <= (int)line.size()) {
                if (parse_fixed_float(line, pos, 12, vals[numRead])) {
                    numRead++;
                }
                pos += 12;
            }

            // Calculate Von Mises from tensor components
            float vonMises;
            if (numRead >= 6) {
                // Full tensor: SXX, SYY, SZZ, SXY, SYZ, SZX
                vonMises = calc_von_mises(vals[0], vals[1], vals[2],
                                          vals[3], vals[4], vals[5]);
            } else if (numRead >= 1) {
                // Scalar result (ERROR block or single-component)
                vonMises = std::abs(vals[0]);
            } else {
                continue;
            }

            results[id] = {vonMises};
            min_stress = std::min(min_stress, vonMises);
            max_stress = std::max(max_stress, vonMises);
            continue;
        }

        // ── DISPLACEMENT DATA: " -1  <id10>  <dx12>  <dy12>  <dz12>" ────────
        if (inDispBlock && line.size() >= 3 && line.substr(0, 3) == " -1") {
            int id;
            float dx, dy, dz;
            if (parse_fixed_int(line, 3, 10, id) &&
                parse_fixed_float(line, 13, 12, dx) &&
                parse_fixed_float(line, 25, 12, dy) &&
                parse_fixed_float(line, 37, 12, dz)) {
                if (nodes.count(id)) {
                    nodes[id].dx = dx;
                    nodes[id].dy = dy;
                    nodes[id].dz = dz;
                }
            }
            continue;
        }
    }

    if (hasPendingElement) {
        elements.push_back(currentElement);
    }

    LOGI("FRD parsing complete: %zu nodes, %zu elements, %zu stress results",
         nodes.size(), elements.size(), results.size());

    return !nodes.empty();
}

// ─── Normal calculation for a triangle ───────────────────────────────────────
static void calc_normal(float ax, float ay, float az,
                        float bx, float by, float bz,
                        float cx, float cy, float cz,
                        float n[3]) {
    float e1x = bx - ax, e1y = by - ay, e1z = bz - az;
    float e2x = cx - ax, e2y = cy - ay, e2z = cz - az;
    n[0] = e1y * e2z - e1z * e2y;
    n[1] = e1z * e2x - e1x * e2z;
    n[2] = e1x * e2y - e1y * e2x;
    float len = std::sqrt(n[0]*n[0] + n[1]*n[1] + n[2]*n[2]);
    if (len > 1e-12f) {
        n[0] /= len; n[1] /= len; n[2] /= len;
    } else {
        n[0] = 0.0f; n[1] = 1.0f; n[2] = 0.0f; // degenerate face fallback
    }
}

// ─── Geometric check to detect spherical mesh ──────────────────────────────
static bool is_spherical_geometry(const std::map<int, Node>& nodes, const BBox& bbox) {
    if (nodes.size() < 12) return false;
    float dx = bbox.xMax - bbox.xMin;
    float dy = bbox.yMax - bbox.yMin;
    float dz = bbox.zMax - bbox.zMin;
    float maxDim = std::max({dx, dy, dz});
    float minDim = std::min({dx, dy, dz});
    if (maxDim <= 0.0f || (maxDim - minDim) / maxDim > 0.12f) {
        return false;
    }
    float cx = bbox.cx(), cy = bbox.cy(), cz = bbox.cz();
    float rMax = 0.0f;
    for (const auto& pair : nodes) {
        const Node& n = pair.second;
        float r = std::sqrt((n.x - cx)*(n.x - cx) + (n.y - cy)*(n.y - cy) + (n.z - cz)*(n.z - cz));
        rMax = std::max(rMax, r);
    }
    if (rMax <= 1e-6f) return false;

    float sumDiff = 0.0f;
    int count = 0;
    for (const auto& pair : nodes) {
        const Node& n = pair.second;
        float r = std::sqrt((n.x - cx)*(n.x - cx) + (n.y - cy)*(n.y - cy) + (n.z - cz)*(n.z - cz));
        if (r >= rMax * 0.75f) {
            sumDiff += std::abs(r - rMax);
            count++;
        }
    }
    if (count < 8) return false;
    return ((sumDiff / count) / rMax) < 0.10f;
}

#ifndef STANDALONE_TEST
extern "C" JNIEXPORT jboolean JNICALL
Java_com_diamon_civil_structural_engine_CalculixExecutor_convertFrdToGlb(
        JNIEnv* env, jobject,
        jstring jInputPath, jstring jOutputPath, jfloat deformationScale, jboolean isSphere) {

    const char* inputPath  = env->GetStringUTFChars(jInputPath,  nullptr);
    const char* outputPath = env->GetStringUTFChars(jOutputPath, nullptr);
    bool sphereMode = (bool)isSphere;
#else
bool convertFrdToGlbStandalone(const char* inputPath, const char* outputPath, float deformationScale, bool isSphere = false) {
    bool sphereMode = isSphere;
#endif

    std::ifstream frd(inputPath);
    if (!frd.is_open()) {
        LOGE("Cannot open FRD file: %s", inputPath);
#ifndef STANDALONE_TEST
        env->ReleaseStringUTFChars(jInputPath,  inputPath);
        env->ReleaseStringUTFChars(jOutputPath, outputPath);
        return JNI_FALSE;
#else
        return false;
#endif
    }

    std::map<int, Node>  nodes;
    std::vector<Element> elements;
    std::map<int, Result> results;
    float min_stress, max_stress;

    if (!parseFRD(frd, nodes, elements, results, min_stress, max_stress)) {
        LOGE("No nodes parsed from FRD: %s", inputPath);
#ifndef STANDALONE_TEST
        env->ReleaseStringUTFChars(jInputPath,  inputPath);
        env->ReleaseStringUTFChars(jOutputPath, outputPath);
        return JNI_FALSE;
#else
        return false;
#endif
    }
    if (results.empty()) {
        min_stress = 0.0f;
        max_stress = 1.0f;
    }
    LOGI("FRD parsed: %zu nodes, %zu elements, %zu results, stress=[%.4f, %.4f]",
         nodes.size(), elements.size(), results.size(), min_stress, max_stress);

    // ── Bounding box for model centering ───────────────────────────────────
    BBox bbox;
    for (auto const& [id, n] : nodes) bbox.expand(n.x, n.y, n.z);
    float extent = bbox.maxExtent();
    float scale  = (extent > 0.0f) ? 1.0f / extent : 1.0f; // normalize to ~1 m

    // ── Helper: get deformed, centered, scaled position of a node ─────────────
    auto get_pos = [&](const Node& node) -> std::array<float,3> {
        return {
            (node.x - bbox.cx() + node.dx * deformationScale) * scale,
            (node.y - bbox.cy() + node.dy * deformationScale) * scale,
            (node.z - bbox.cz() + node.dz * deformationScale) * scale
        };
    };

    // ── Helper: get stress color for a node ID ────────────────────────────────
    auto get_node_color = [&](int nodeId) -> std::array<float,3> {
        float rgb[3] = {0.6f, 0.7f, 0.8f}; // default steel-blue
        if (results.count(nodeId)) {
            get_color(results.at(nodeId).vonMises, min_stress, max_stress, rgb);
        }
        return {rgb[0], rgb[1], rgb[2]};
    };

    // ── Build triangle list with per-face normals ─────────────────────────────
    // We DUPLICATE vertices per triangle face so each face gets its own normal.
    // This is required for proper lighting in Filament/SceneView.
    std::vector<float>    vertex_data;  // x,y,z per vertex
    std::vector<float>    normal_data;  // nx,ny,nz per vertex
    std::vector<float>    color_data;   // r,g,b per vertex
    std::vector<uint32_t> index_data;
    std::map<int, int>    id_to_idx;
    uint32_t vertex_count = 0;

    // Create an id_to_idx mapping for element triangulation
    for (auto const& [id, node] : nodes) {
        id_to_idx[id] = 0; // just to check existence
    }

    // Triangulate elements and create per-face vertices with normals
    auto add_triangle = [&](int n0, int n1, int n2) {
        auto it0 = nodes.find(n0);
        auto it1 = nodes.find(n1);
        auto it2 = nodes.find(n2);
        if (it0 == nodes.end() || it1 == nodes.end() || it2 == nodes.end()) return;

        auto p0 = get_pos(it0->second);
        auto p1 = get_pos(it1->second);
        auto p2 = get_pos(it2->second);

        // Calculate face normal
        float normal[3];
        calc_normal(p0[0], p0[1], p0[2],
                    p1[0], p1[1], p1[2],
                    p2[0], p2[1], p2[2], normal);

        // Get colors
        auto c0 = get_node_color(n0);
        auto c1 = get_node_color(n1);
        auto c2 = get_node_color(n2);

        // Add 3 vertices with same normal (flat shading)
        for (int i = 0; i < 3; ++i) {
            auto& p = (i == 0) ? p0 : (i == 1) ? p1 : p2;
            auto& c = (i == 0) ? c0 : (i == 1) ? c1 : c2;

            vertex_data.push_back(p[0]);
            vertex_data.push_back(p[1]);
            vertex_data.push_back(p[2]);

            normal_data.push_back(normal[0]);
            normal_data.push_back(normal[1]);
            normal_data.push_back(normal[2]);

            color_data.push_back(c[0]);
            color_data.push_back(c[1]);
            color_data.push_back(c[2]);
            color_data.push_back(1.0f); // Alpha = 1.0 (VEC4)

            index_data.push_back(vertex_count++);
        }
    };

    for (const auto& el : elements) {
        const size_t n = el.nodes.size();
        if (n == 4) { // TET4 → 4 triangular faces
            static const int faces[4][3] = {{0,1,2},{0,1,3},{1,2,3},{0,2,3}};
            for (int f = 0; f < 4; ++f) {
                add_triangle(el.nodes[faces[f][0]], el.nodes[faces[f][1]], el.nodes[faces[f][2]]);
            }
        } else if (n == 8) { // HEX8 → 6 quad faces × 2 triangles each
            static const int faces[6][4] = {{0,1,2,3},{4,5,6,7},{0,1,5,4},
                                             {2,3,7,6},{1,2,6,5},{0,3,7,4}};
            for (int f = 0; f < 6; ++f) {
                add_triangle(el.nodes[faces[f][0]], el.nodes[faces[f][1]], el.nodes[faces[f][2]]);
                add_triangle(el.nodes[faces[f][0]], el.nodes[faces[f][2]], el.nodes[faces[f][3]]);
            }
        } else if (n == 3) { // TRIA3
            add_triangle(el.nodes[0], el.nodes[1], el.nodes[2]);
        } else if (n >= 10) { // TET10 → use corner nodes only (0-3)
            static const int faces[4][3] = {{0,1,2},{0,1,3},{1,2,3},{0,2,3}};
            for (int f = 0; f < 4; ++f) {
                add_triangle(el.nodes[faces[f][0]], el.nodes[faces[f][1]], el.nodes[faces[f][2]]);
            }
        } else if (n == 6) { // TRIA6 (quadratic triangle) → use corner nodes only
            add_triangle(el.nodes[0], el.nodes[1], el.nodes[2]);
        } else if (n == 20) { // HEX20 → use corner nodes only (same as HEX8 corners)
            static const int faces[6][4] = {{0,1,2,3},{4,5,6,7},{0,1,5,4},
                                             {2,3,7,6},{1,2,6,5},{0,3,7,4}};
            for (int f = 0; f < 6; ++f) {
                add_triangle(el.nodes[faces[f][0]], el.nodes[faces[f][1]], el.nodes[faces[f][2]]);
                add_triangle(el.nodes[faces[f][0]], el.nodes[faces[f][2]], el.nodes[faces[f][3]]);
            }
        }
    }

    if (index_data.empty()) {
        LOGE("No triangles generated from elements");
#ifndef STANDALONE_TEST
        env->ReleaseStringUTFChars(jInputPath,  inputPath);
        env->ReleaseStringUTFChars(jOutputPath, outputPath);
        return JNI_FALSE;
#else
        return false;
#endif
    }

    LOGI("Generated %u vertices, %zu triangles", vertex_count, index_data.size() / 3);

    // ── Compute POSITION min/max (required by glTF spec) ─────────────────────
    float pos_min[3] = { std::numeric_limits<float>::max(),  std::numeric_limits<float>::max(),  std::numeric_limits<float>::max()};
    float pos_max[3] = {-std::numeric_limits<float>::max(), -std::numeric_limits<float>::max(), -std::numeric_limits<float>::max()};
    for (uint32_t i = 0; i < vertex_count; ++i) {
        for (int c = 0; c < 3; ++c) {
            pos_min[c] = std::min(pos_min[c], vertex_data[i*3+c]);
            pos_max[c] = std::max(pos_max[c], vertex_data[i*3+c]);
        }
    }

    // ── Build glTF 2.0 model ─────────────────────────────────────────────────
    tinygltf::Model gltf;
    gltf.asset.version   = "2.0";
    gltf.asset.generator = "StructuralFEA-frd_converter";

    size_t posBytes = vertex_data.size() * sizeof(float);
    size_t nrmBytes = normal_data.size() * sizeof(float);
    size_t colBytes = color_data.size()  * sizeof(float);

    // Decide index type: use UNSIGNED_SHORT if vertex count fits in 16 bits
    bool use16bit = (vertex_count <= 65535);
    size_t idxElemSize = use16bit ? sizeof(uint16_t) : sizeof(uint32_t);
    size_t idxBytes = index_data.size() * idxElemSize;

    // Build index buffer
    std::vector<unsigned char> idxBuf(idxBytes);
    if (use16bit) {
        auto* ptr = reinterpret_cast<uint16_t*>(idxBuf.data());
        for (size_t i = 0; i < index_data.size(); ++i) {
            ptr[i] = static_cast<uint16_t>(index_data[i]);
        }
    } else {
        memcpy(idxBuf.data(), index_data.data(), idxBytes);
    }

    // Single buffer with layout: [indices | positions | normals | colors]
    tinygltf::Buffer buf;
    size_t totalBytes = idxBytes + posBytes + nrmBytes + colBytes;
    buf.data.resize(totalBytes);
    size_t offset = 0;
    memcpy(buf.data.data() + offset, idxBuf.data(), idxBytes); offset += idxBytes;
    memcpy(buf.data.data() + offset, vertex_data.data(), posBytes); offset += posBytes;
    memcpy(buf.data.data() + offset, normal_data.data(), nrmBytes); offset += nrmBytes;
    memcpy(buf.data.data() + offset, color_data.data(), colBytes);
    gltf.buffers.push_back(std::move(buf));

    // Buffer views — using separate buffer views per attribute for full glTF 2.0 / Filament compliance
    // View 0: Index buffer
    tinygltf::BufferView idxBV;
    idxBV.buffer = 0;
    idxBV.byteOffset = 0;
    idxBV.byteLength = idxBytes;
    idxBV.target = TINYGLTF_TARGET_ELEMENT_ARRAY_BUFFER;
    idxBV.name = "indices bufferView";
    gltf.bufferViews.push_back(idxBV); // view 0

    // View 1: Positions buffer view
    tinygltf::BufferView posBV;
    posBV.buffer = 0;
    posBV.byteOffset = idxBytes;
    posBV.byteLength = posBytes;
    posBV.target = TINYGLTF_TARGET_ARRAY_BUFFER;
    posBV.name = "positions bufferView";
    gltf.bufferViews.push_back(posBV); // view 1

    // View 2: Normals buffer view
    tinygltf::BufferView nrmBV;
    nrmBV.buffer = 0;
    nrmBV.byteOffset = idxBytes + posBytes;
    nrmBV.byteLength = nrmBytes;
    nrmBV.target = TINYGLTF_TARGET_ARRAY_BUFFER;
    nrmBV.name = "normals bufferView";
    gltf.bufferViews.push_back(nrmBV); // view 2

    // View 3: Colors buffer view
    tinygltf::BufferView colBV;
    colBV.buffer = 0;
    colBV.byteOffset = idxBytes + posBytes + nrmBytes;
    colBV.byteLength = colBytes;
    colBV.target = TINYGLTF_TARGET_ARRAY_BUFFER;
    colBV.name = "colors bufferView";
    gltf.bufferViews.push_back(colBV); // view 3

    // Accessors
    // Index accessor
    tinygltf::Accessor idxAcc;
    idxAcc.bufferView = 0;
    idxAcc.byteOffset = 0;
    idxAcc.componentType = use16bit ? TINYGLTF_COMPONENT_TYPE_UNSIGNED_SHORT : TINYGLTF_COMPONENT_TYPE_UNSIGNED_INT;
    idxAcc.count = index_data.size();
    idxAcc.type = TINYGLTF_TYPE_SCALAR;
    idxAcc.minValues = {0.0};
    idxAcc.maxValues = {(double)(vertex_count - 1)};
    gltf.accessors.push_back(idxAcc); // acc 0

    // Position accessor
    tinygltf::Accessor posAcc;
    posAcc.bufferView = 1;
    posAcc.byteOffset = 0;
    posAcc.componentType = TINYGLTF_COMPONENT_TYPE_FLOAT;
    posAcc.count = vertex_count;
    posAcc.type = TINYGLTF_TYPE_VEC3;
    for (int i = 0; i < 3; ++i) {
        posAcc.minValues.push_back((double)pos_min[i]);
        posAcc.maxValues.push_back((double)pos_max[i]);
    }
    gltf.accessors.push_back(posAcc); // acc 1

    // Normal accessor
    float nrm_min[3] = {-1.0f, -1.0f, -1.0f};
    float nrm_max[3] = { 1.0f,  1.0f,  1.0f};
    tinygltf::Accessor nrmAcc;
    nrmAcc.bufferView = 2;
    nrmAcc.byteOffset = 0;
    nrmAcc.componentType = TINYGLTF_COMPONENT_TYPE_FLOAT;
    nrmAcc.count = vertex_count;
    nrmAcc.type = TINYGLTF_TYPE_VEC3;
    for (int i = 0; i < 3; ++i) {
        nrmAcc.minValues.push_back((double)nrm_min[i]);
        nrmAcc.maxValues.push_back((double)nrm_max[i]);
    }
    gltf.accessors.push_back(nrmAcc); // acc 2

    // Color accessor (VEC4 RGBA float)
    float col_min[4] = {0.0f, 0.0f, 0.0f, 1.0f};
    float col_max[4] = {1.0f, 1.0f, 1.0f, 1.0f};
    tinygltf::Accessor colAcc;
    colAcc.bufferView = 3;
    colAcc.byteOffset = 0;
    colAcc.componentType = TINYGLTF_COMPONENT_TYPE_FLOAT;
    colAcc.count = vertex_count;
    colAcc.type = TINYGLTF_TYPE_VEC4;
    for (int i = 0; i < 4; ++i) {
        colAcc.minValues.push_back((double)col_min[i]);
        colAcc.maxValues.push_back((double)col_max[i]);
    }
    gltf.accessors.push_back(colAcc); // acc 3

    if (!sphereMode) {
        sphereMode = is_spherical_geometry(nodes, bbox);
    }

    // Material configuration:
    // - For spheres: Use Lit PBR material so spherical geometries display realistic depth, volume,
    //   and shading highlights rather than rendering as flat 2D silhouettes.
    // - For all other FEA models: Restore UNLIT material with KHR_materials_unlit at full luminance.
    //   FEA stress heatmaps must not be dimmed or distorted by specular/diffuse lighting reflections.
    tinygltf::Material mat;
    mat.name = "FEA_VertexColorMaterial";
    mat.doubleSided = true;
    mat.pbrMetallicRoughness.baseColorFactor = {1.0, 1.0, 1.0, 1.0};

    if (sphereMode) {
        mat.pbrMetallicRoughness.metallicFactor  = 0.05;
        mat.pbrMetallicRoughness.roughnessFactor = 0.35;
    } else {
        mat.pbrMetallicRoughness.metallicFactor  = 0.0;
        mat.pbrMetallicRoughness.roughnessFactor = 1.0;
        mat.extensions["KHR_materials_unlit"] = tinygltf::Value(tinygltf::Value::Object());
        gltf.extensionsUsed.push_back("KHR_materials_unlit");
    }
    gltf.materials.push_back(mat);

    // Mesh primitive
    tinygltf::Primitive prim;
    prim.attributes["POSITION"] = 1;   // acc 1
    prim.attributes["NORMAL"]   = 2;   // acc 2
    prim.attributes["COLOR_0"]  = 3;   // acc 3
    prim.indices  = 0;                  // acc 0
    prim.material = 0;                  // mat 0
    prim.mode     = TINYGLTF_MODE_TRIANGLES;

    tinygltf::Mesh mesh;
    mesh.name = "FEA_Result";
    mesh.primitives.push_back(prim);
    gltf.meshes.push_back(mesh);

    tinygltf::Node meshNode;
    meshNode.mesh = 0;
    meshNode.name = "FEA_Node";
    gltf.nodes.push_back(meshNode);

    tinygltf::Scene scene;
    scene.name = "FEA_Scene";
    scene.nodes.push_back(0); // mesh node
    gltf.scenes.push_back(scene);
    gltf.defaultScene = 0;

    tinygltf::TinyGLTF writer;
    bool ok = writer.WriteGltfSceneToFile(
        &gltf, outputPath,
        /*embedImages=*/false,
        /*embedBuffers=*/true,   // GLB embedded
        /*prettyPrint=*/false,
        /*writeBinary=*/true);   // .glb output

    LOGI("GLB write %s → %s (%u vertices, %zu triangles)",
         ok ? "OK" : "FAILED", outputPath, vertex_count, index_data.size() / 3);

#ifndef STANDALONE_TEST
    env->ReleaseStringUTFChars(jInputPath,  inputPath);
    env->ReleaseStringUTFChars(jOutputPath, outputPath);
    return ok ? JNI_TRUE : JNI_FALSE;
#else
    return ok;
#endif
}

#ifdef STANDALONE_TEST
int main(int argc, char** argv) {
    if (argc < 2) {
        printf("Usage: %s <input.frd> [output.glb]\n", argv[0]);
        return 1;
    }
    const char* inputPath = argv[1];
    const char* outputPath = (argc >= 3) ? argv[2] : "/tmp/output.glb";

    if (convertFrdToGlbStandalone(inputPath, outputPath, 1.0f)) {
        printf("Successfully generated %s\n", outputPath);
        return 0;
    } else {
        printf("Failed to generate GLB\n");
        return 1;
    }
}
#endif
