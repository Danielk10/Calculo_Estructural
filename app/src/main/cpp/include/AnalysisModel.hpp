#ifndef ANALYSIS_MODEL_HPP
#define ANALYSIS_MODEL_HPP

#include <vector>
#include <string>
#include <map>
#include "json.hpp"

namespace FEA {

struct Node {
    int id;
    double x, y, z;
};

struct Element {
    int id;
    std::string type; // e.g., "C3D8", "B31", "S4"
    std::string elset;
    std::vector<int> nodeIds;
};

struct Material {
    std::string name;
    double youngModulus;
    double poissonRatio;
    double density;
};

struct BoundaryCondition {
    int nodeId;
    std::vector<int> dofs; // 1,2,3 for translation, 4,5,6 for rotation
    double value;
};

struct Load {
    int nodeId;
    double fx, fy, fz;
};

struct Section {
    std::string elset;
    std::string type; // "SOLID", "BEAM"
    std::string material;
    std::vector<double> params; // e.g., [width, height] for RECT BEAM
};

class AnalysisModel {
public:
    std::map<int, Node> nodes;
    std::map<int, Element> elements;
    std::vector<Material> materials;
    std::vector<Section> sections;
    std::vector<BoundaryCondition> constraints;
    std::vector<Load> loads;

    void clear() {
        nodes.clear();
        elements.clear();
        materials.clear();
        sections.clear();
        constraints.clear();
        loads.clear();
    }

    // Export to CalculiX .inp format
    std::string toInpString() const;
    
    // Serialization for communication with Kotlin
    std::string toJson() const;
    void fromJson(const std::string& jsonStr);
};

} // namespace FEA

#endif // ANALYSIS_MODEL_HPP
