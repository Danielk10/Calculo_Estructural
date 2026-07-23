#include <jni.h>
#include <string>
#include <vector>
#include <fstream>
#include <sstream>
#include <map>
#include <algorithm>
#include <cmath>
#include <limits>
#include <android/log.h>

#define TINYGLTF_IMPLEMENTATION
#define STB_IMAGE_IMPLEMENTATION
#define STB_IMAGE_WRITE_IMPLEMENTATION
#define TINYGLTF_NO_EXTERNAL_IMAGE
#define TINYGLTF_NO_STB_IMAGE
#define TINYGLTF_NO_STB_IMAGE_WRITE
#include "include/tiny_gltf.h"

#define LOG_TAG "FRD_CONVERTER"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ─── Tipos de datos ──────────────────────────────────────────────────────────

struct Node  { float x, y, z; };
struct Element { std::vector<int> nodes; };

// Mapa de resultados por nodo (valor escalar = estrés von Mises o similar)
struct Result { float value; };

// ─── Mapa arco-iris para colormap de estrés ──────────────────────────────────
static void get_color(float value, float min_v, float max_v, float rgb[3]) {
    float ratio = (max_v > min_v) ? (value - min_v) / (max_v - min_v) : 0.5f;
    ratio = std::max(0.0f, std::min(1.0f, ratio));  // clamp [0,1]
    rgb[0] = std::max(0.0f, 2.0f * ratio - 1.0f);
    rgb[1] = 1.0f - std::abs(2.0f * ratio - 1.0f);
    rgb[2] = std::max(0.0f, 1.0f - 2.0f * ratio);
}

// ─── Utilidades para calcular bounding-box y centrar el modelo ────────────────
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

// ─── FIX C++: Parser FRD con prefijos reales del formato CalculiX ────────────
// El formato FRD de CalculiX usa prefijos de 5 caracteres:
//   "    1" → nodo (bloque -1)
//   "    2" → elemento (bloque -2)
//   " -1" al principio de un resultado de nodo
//
// El parser anterior buscaba "  -1" como indicador de inicio de nodo,
// pero eso es el marcador de INICIO del bloque header, no de cada línea.
// El formato real es:
//   2C        <- cabecera de nodos
//   -1  <id>  <x>  <y>  <z>
//   -3  <- fin del bloque

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

    while (std::getline(frd, line)) {
        if (line.size() < 5) continue;

        // Detectar inicio de bloque de nodos: líneas que empiezan con "    2C"
        if (line.substr(0, 6) == "    2C") { inNodeBlock = true;  inElemBlock = false; inResultBlock = false; continue; }
        if (line.substr(0, 6) == "    3C") { inElemBlock = true;  inNodeBlock = false; inResultBlock = false; continue; }

        // Detectar bloques de resultados (STRESS, DISP, etc.)
        if (line.find("STRESS") != std::string::npos ||
            line.find("CLSTRESS") != std::string::npos ||
            line.find("DISP") != std::string::npos) {
            inResultBlock = true; inNodeBlock = false; inElemBlock = false; continue;
        }

        // Fin de bloque
        if (line.substr(0, 3) == " -3") { inNodeBlock = false; inElemBlock = false; inResultBlock = false; continue; }

        // ── Líneas de nodo: " -1   <id>   <x>   <y>   <z>"
        if (inNodeBlock && line.substr(0, 3) == " -1") {
            std::stringstream ss(line.substr(3));
            int id; float x, y, z;
            if (ss >> id >> x >> y >> z) nodes[id] = {x, y, z};
            continue;
        }

        // ── Líneas de elemento: " -1   <id>   <type>   <n1>  <n2> ..."
        if (inElemBlock && line.substr(0, 3) == " -1") {
            std::stringstream ss(line.substr(3));
            int id, type; ss >> id >> type;
            Element el; int nid;
            while (ss >> nid) el.nodes.push_back(nid);
            if (!el.nodes.empty()) elements.push_back(el);
            continue;
        }

        // ── Líneas de resultado de nodo: " -1   <id>   <val1>   ..."
        if (inResultBlock && line.substr(0, 3) == " -1") {
            std::stringstream ss(line.substr(3));
            int id; float val;
            if (ss >> id >> val) {
                results[id] = {val};
                min_stress = std::min(min_stress, val);
                max_stress = std::max(max_stress, val);
            }
            continue;
        }
    }

    return !nodes.empty();
}

// ─── JNI: convertFrdToGlb ─────────────────────────────────────────────────────
extern "C" JNIEXPORT jboolean JNICALL
Java_com_diamon_civil_engine_CalculixExecutor_convertFrdToGlb(
        JNIEnv* env, jobject,
        jstring jInputPath, jstring jOutputPath) {

    const char* inputPath  = env->GetStringUTFChars(jInputPath,  nullptr);
    const char* outputPath = env->GetStringUTFChars(jOutputPath, nullptr);

    std::ifstream frd(inputPath);
    if (!frd.is_open()) {
        LOGE("Cannot open FRD file: %s", inputPath);
        env->ReleaseStringUTFChars(jInputPath,  inputPath);
        env->ReleaseStringUTFChars(jOutputPath, outputPath);
        return JNI_FALSE;
    }

    std::map<int, Node>  nodes;
    std::vector<Element> elements;
    std::map<int, Result> results;
    float min_stress, max_stress;

    if (!parseFRD(frd, nodes, elements, results, min_stress, max_stress)) {
        LOGE("No nodes parsed from FRD: %s", inputPath);
        env->ReleaseStringUTFChars(jInputPath,  inputPath);
        env->ReleaseStringUTFChars(jOutputPath, outputPath);
        return JNI_FALSE;
    }
    LOGI("FRD parsed: %zu nodes, %zu elements, %zu results",
         nodes.size(), elements.size(), results.size());

    // ── Bounding-box para centrar el modelo ───────────────────────────────────
    BBox bbox;
    for (auto const& [id, n] : nodes) bbox.expand(n.x, n.y, n.z);
    float extent = bbox.maxExtent();
    float scale  = (extent > 0.0f) ? 1.0f / extent : 1.0f; // normalizar a ~1 m

    // ── Construir buffers de vértices + colores ───────────────────────────────
    std::vector<float>    vertex_data;
    std::vector<float>    color_data;
    std::vector<uint32_t> index_data;
    std::map<int, int>    id_to_idx;
    int current_idx = 0;

    // Calcular min/max para componentes del accessor POSITION (requerido por GLTF spec)
    float pos_min[3] = { std::numeric_limits<float>::max(),  std::numeric_limits<float>::max(),  std::numeric_limits<float>::max()};
    float pos_max[3] = {-std::numeric_limits<float>::max(), -std::numeric_limits<float>::max(), -std::numeric_limits<float>::max()};

    for (auto const& [id, node] : nodes) {
        float cx = (node.x - bbox.cx()) * scale;
        float cy = (node.y - bbox.cy()) * scale;
        float cz = (node.z - bbox.cz()) * scale;

        vertex_data.push_back(cx);
        vertex_data.push_back(cy);
        vertex_data.push_back(cz);

        pos_min[0] = std::min(pos_min[0], cx); pos_max[0] = std::max(pos_max[0], cx);
        pos_min[1] = std::min(pos_min[1], cy); pos_max[1] = std::max(pos_max[1], cy);
        pos_min[2] = std::min(pos_min[2], cz); pos_max[2] = std::max(pos_max[2], cz);

        float rgb[3] = {0.6f, 0.7f, 0.8f}; // color neutro por defecto (azul acero)
        if (results.count(id)) get_color(results.at(id).value, min_stress, max_stress, rgb);
        color_data.push_back(rgb[0]);
        color_data.push_back(rgb[1]);
        color_data.push_back(rgb[2]);

        id_to_idx[id] = current_idx++;
    }

    // ── Triangulación de elementos ────────────────────────────────────────────
    for (const auto& el : elements) {
        const size_t n = el.nodes.size();
        if (n == 4) { // TET4 → 4 caras triangulares
            static const int faces[4][3] = {{0,1,2},{0,1,3},{1,2,3},{0,2,3}};
            for (int f = 0; f < 4; ++f) {
                for (int v = 0; v < 3; ++v) {
                    auto it = id_to_idx.find(el.nodes[faces[f][v]]);
                    if (it != id_to_idx.end()) index_data.push_back(it->second);
                }
            }
        } else if (n == 8) { // HEX8 → 6 caras × 2 triángulos
            static const int faces[6][4] = {{0,1,2,3},{4,5,6,7},{0,1,5,4},
                                             {2,3,7,6},{1,2,6,5},{0,3,7,4}};
            for (int f = 0; f < 6; ++f) {
                // Quad → 2 triángulos
                int idx[4];
                bool valid = true;
                for (int v = 0; v < 4; ++v) {
                    auto it = id_to_idx.find(el.nodes[faces[f][v]]);
                    if (it == id_to_idx.end()) { valid = false; break; }
                    idx[v] = it->second;
                }
                if (!valid) continue;
                index_data.push_back(idx[0]); index_data.push_back(idx[1]); index_data.push_back(idx[2]);
                index_data.push_back(idx[0]); index_data.push_back(idx[2]); index_data.push_back(idx[3]);
            }
        } else if (n == 3) { // TRIA3
            for (int v = 0; v < 3; ++v) {
                auto it = id_to_idx.find(el.nodes[v]);
                if (it != id_to_idx.end()) index_data.push_back(it->second);
            }
        } else if (n >= 10) { // TET10 → usar solo nodos de esquina (0-3)
            static const int faces[4][3] = {{0,1,2},{0,1,3},{1,2,3},{0,2,3}};
            for (int f = 0; f < 4; ++f) {
                for (int v = 0; v < 3; ++v) {
                    auto it = id_to_idx.find(el.nodes[faces[f][v]]);
                    if (it != id_to_idx.end()) index_data.push_back(it->second);
                }
            }
        }
    }

    if (index_data.empty()) {
        LOGE("No triangles generated from elements");
        env->ReleaseStringUTFChars(jInputPath,  inputPath);
        env->ReleaseStringUTFChars(jOutputPath, outputPath);
        return JNI_FALSE;
    }

    // ── Construir modelo GLTF 2.0 ────────────────────────────────────────────
    tinygltf::Model gltf;
    gltf.asset.version   = "2.0";
    gltf.asset.generator = "StructuralFEA-frd_converter";

    // Un único buffer por tipo (separados para compatibilidad con validadores GLTF)
    auto add_buffer_view = [&](const void* data, size_t byteSize, int target) -> int {
        tinygltf::Buffer buf;
        const auto* bytes = reinterpret_cast<const unsigned char*>(data);
        buf.data.assign(bytes, bytes + byteSize);
        int bIdx = (int)gltf.buffers.size();
        gltf.buffers.push_back(std::move(buf));

        tinygltf::BufferView bv;
        bv.buffer     = bIdx;
        bv.byteOffset = 0;
        bv.byteLength = byteSize;
        bv.target     = target;
        int vIdx = (int)gltf.bufferViews.size();
        gltf.bufferViews.push_back(bv);
        return vIdx;
    };

    int posView = add_buffer_view(vertex_data.data(), vertex_data.size() * sizeof(float), TINYGLTF_TARGET_ARRAY_BUFFER);
    int colView = add_buffer_view(color_data.data(),  color_data.size()  * sizeof(float), TINYGLTF_TARGET_ARRAY_BUFFER);
    int idxView = add_buffer_view(index_data.data(),  index_data.size()  * sizeof(uint32_t), TINYGLTF_TARGET_ELEMENT_ARRAY_BUFFER);

    // Accessors con min/max obligatorios para POSITION
    auto make_accessor = [&](int viewIdx, int compType, int type, size_t count,
                              const float* minVals = nullptr, const float* maxVals = nullptr,
                              int numComp = 3) -> int {
        tinygltf::Accessor acc;
        acc.bufferView    = viewIdx;
        acc.byteOffset    = 0;
        acc.componentType = compType;
        acc.count         = count;
        acc.type          = type;
        if (minVals && maxVals) {
            for (int i = 0; i < numComp; ++i) {
                acc.minValues.push_back((double)minVals[i]);
                acc.maxValues.push_back((double)maxVals[i]);
            }
        }
        int aIdx = (int)gltf.accessors.size();
        gltf.accessors.push_back(acc);
        return aIdx;
    };

    int posAcc = make_accessor(posView, TINYGLTF_COMPONENT_TYPE_FLOAT, TINYGLTF_TYPE_VEC3,
                               nodes.size(), pos_min, pos_max, 3);
    int colAcc = make_accessor(colView, TINYGLTF_COMPONENT_TYPE_FLOAT, TINYGLTF_TYPE_VEC3,
                               nodes.size());
    float idxMin[1] = {0.0f};
    float idxMax[1] = {(float)(nodes.size() - 1)};
    int idxAcc = make_accessor(idxView, TINYGLTF_COMPONENT_TYPE_UNSIGNED_INT, TINYGLTF_TYPE_SCALAR,
                               index_data.size(), idxMin, idxMax, 1);

    tinygltf::Material mat;
    mat.name = "FEA_Material";
    mat.pbrMetallicRoughness.metallicFactor = 0.1;
    mat.pbrMetallicRoughness.roughnessFactor = 0.8;
    mat.doubleSided = true;
    gltf.materials.push_back(mat);

    tinygltf::Primitive prim;
    prim.attributes["POSITION"] = posAcc;
    prim.attributes["COLOR_0"]  = colAcc;
    prim.indices = idxAcc;
    prim.mode    = TINYGLTF_MODE_TRIANGLES;
    prim.material = 0;

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
    scene.nodes.push_back(0);
    gltf.scenes.push_back(scene);
    gltf.defaultScene = 0;

    tinygltf::TinyGLTF writer;
    bool ok = writer.WriteGltfSceneToFile(
        &gltf, outputPath,
        /*embedImages=*/false,
        /*embedBuffers=*/true,   // GLB embebido
        /*prettyPrint=*/false,
        /*writeBinary=*/true);   // salida .glb

    LOGI("GLB write %s → %s", ok ? "OK" : "FAILED", outputPath);

    env->ReleaseStringUTFChars(jInputPath,  inputPath);
    env->ReleaseStringUTFChars(jOutputPath, outputPath);
    return ok ? JNI_TRUE : JNI_FALSE;
}
