#define TINYGLTF_IMPLEMENTATION
#define STB_IMAGE_IMPLEMENTATION
#define STB_IMAGE_WRITE_IMPLEMENTATION
#define TINYGLTF_NO_EXTERNAL_IMAGE
#define TINYGLTF_NO_STB_IMAGE
#define TINYGLTF_NO_STB_IMAGE_WRITE
#include "tiny_gltf.h"
#include <iostream>
#include <fstream>
#include <vector>
#include <string>
#include <sstream>
#include <map>
#include <algorithm>
#include <cmath>

struct Node {
    float x, y, z;
};

struct Element {
    std::vector<int> nodes;
};

struct Result {
    float value;
};

void get_color(float value, float min_v, float max_v, float rgb[3]) {
    float ratio = 0.0f;
    if (max_v > min_v) ratio = (value - min_v) / (max_v - min_v);
    
    rgb[0] = std::max(0.0f, 2.0f * ratio - 1.0f);
    rgb[1] = 1.0f - std::abs(2.0f * ratio - 1.0f);
    rgb[2] = std::max(0.0f, 1.0f - 2.0f * ratio);
}

int main(int argc, char** argv) {
    if (argc < 3) {
        std::cout << "Usage: frd2glb input.frd output.glb" << std::endl;
        return 1;
    }

    std::string input_path = argv[1];
    std::string output_path = argv[2];

    std::ifstream frd(input_path);
    if (!frd.is_open()) {
        std::cerr << "Could not open input file" << std::endl;
        return 1;
    }

    std::map<int, Node> nodes;
    std::vector<Element> elements;
    std::map<int, Result> results;
    float min_stress = 1e30, max_stress = -1e30;

    std::string line;
    while (std::getline(frd, line)) {
        if (line.find("  -1") == 0) { // Nodes
            while (std::getline(frd, line) && line.find("  -") != 0) {
                if (line.size() < 3) break;
                int id;
                float x, y, z;
                std::stringstream ss(line);
                if (ss >> id >> x >> y >> z) {
                    nodes[id] = {x, y, z};
                }
            }
        }
        
        if (line.find("  -2") == 0) { // Elements
            while (std::getline(frd, line) && line.find("  -") != 0) {
                if (line.size() < 3) break;
                std::stringstream ss(line);
                int id, type;
                ss >> id >> type;
                Element el;
                int node_id;
                while (ss >> node_id) {
                    el.nodes.push_back(node_id);
                }
                elements.push_back(el);
            }
        }

        if (line.find("CLSTRESS") != std::string::npos) { // Results
             while (std::getline(frd, line) && line.find(" -1") != 0);
             while (std::getline(frd, line) && line.find(" -1") != 0) {
                 if (line.size() < 3) break;
                 std::stringstream ss(line);
                 int id;
                 float val;
                 if (ss >> id >> val) {
                     results[id] = {val};
                     if (val < min_stress) min_stress = val;
                     if (val > max_stress) max_stress = val;
                 }
             }
        }
    }

    std::vector<float> vertex_data;
    std::vector<float> color_data;
    std::vector<uint32_t> index_data;
    std::map<int, int> id_to_idx;
    int current_idx = 0;

    for (auto const& [id, node] : nodes) {
        vertex_data.push_back(node.x);
        vertex_data.push_back(node.y);
        vertex_data.push_back(node.z);

        float rgb[3] = {0.7f, 0.7f, 0.7f};
        if (results.count(id)) {
            get_color(results[id].value, min_stress, max_stress, rgb);
        }
        color_data.push_back(rgb[0]);
        color_data.push_back(rgb[1]);
        color_data.push_back(rgb[2]);
        id_to_idx[id] = current_idx++;
    }

    for (const auto& el : elements) {
        if (el.nodes.size() == 4) { // TET4
            int faces[4][3] = {{0,1,2}, {0,1,3}, {1,2,3}, {0,2,3}};
            for (int i=0; i<4; ++i) {
                index_data.push_back(id_to_idx[el.nodes[faces[i][0]]]);
                index_data.push_back(id_to_idx[el.nodes[faces[i][1]]]);
                index_data.push_back(id_to_idx[el.nodes[faces[i][2]]]);
            }
        }
    }

    tinygltf::Model model;
    tinygltf::Scene scene;
    scene.nodes.push_back(0);
    model.scenes.push_back(scene);
    model.defaultScene = 0;

    tinygltf::Node node;
    node.mesh = 0;
    model.nodes.push_back(node);

    tinygltf::Mesh mesh;
    tinygltf::Primitive primitive;
    primitive.attributes["POSITION"] = 0;
    primitive.attributes["COLOR_0"] = 1;
    primitive.indices = 2;
    primitive.mode = TINYGLTF_MODE_TRIANGLES;
    mesh.primitives.push_back(primitive);
    model.meshes.push_back(mesh);

    auto add_buffer_view = [&](const void* data, size_t size, int target) {
        tinygltf::Buffer buffer;
        buffer.data.assign((const unsigned char*)data, (const unsigned char*)data + size);
        int buffer_idx = model.buffers.size();
        model.buffers.push_back(buffer);

        tinygltf::BufferView view;
        view.buffer = buffer_idx;
        view.byteOffset = 0;
        view.byteLength = size;
        view.target = target;
        int view_idx = model.bufferViews.size();
        model.bufferViews.push_back(view);
        return view_idx;
    };

    int pos_view = add_buffer_view(vertex_data.data(), vertex_data.size() * sizeof(float), TINYGLTF_TARGET_ARRAY_BUFFER);
    int col_view = add_buffer_view(color_data.data(), color_data.size() * sizeof(float), TINYGLTF_TARGET_ARRAY_BUFFER);
    int idx_view = add_buffer_view(index_data.data(), index_data.size() * sizeof(uint32_t), TINYGLTF_TARGET_ELEMENT_ARRAY_BUFFER);

    auto add_accessor = [&](int view, int type, int component, size_t count) {
        tinygltf::Accessor acc;
        acc.bufferView = view;
        acc.byteOffset = 0;
        acc.componentType = component;
        acc.count = count;
        acc.type = type;
        int acc_idx = model.accessors.size();
        model.accessors.push_back(acc);
        return acc_idx;
    };

    add_accessor(pos_view, TINYGLTF_TYPE_VEC3, TINYGLTF_COMPONENT_TYPE_FLOAT, nodes.size());
    add_accessor(col_view, TINYGLTF_TYPE_VEC3, TINYGLTF_COMPONENT_TYPE_FLOAT, nodes.size());
    add_accessor(idx_view, TINYGLTF_TYPE_SCALAR, TINYGLTF_COMPONENT_TYPE_UNSIGNED_INT, index_data.size());

    tinygltf::TinyGLTF loader;
    if (!loader.WriteGltfSceneToFile(&model, output_path, false, true, true, true)) {
        std::cerr << "Failed to write GLB" << std::endl;
        return 1;
    }

    std::cout << "Successfully converted " << input_path << " to " << output_path << std::endl;
    return 0;
}
