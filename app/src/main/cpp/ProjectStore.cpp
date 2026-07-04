#include "ProjectStore.hpp"
#include <fstream>
#include <sstream>

namespace FEA {

bool ProjectStore::saveProject(const std::string& filePath, const AnalysisModel& model) {
    std::string json = model.toJson();
    std::ofstream out(filePath);
    if (!out.is_open()) return false;
    out << json;
    out.close();
    return true;
}

bool ProjectStore::loadProject(const std::string& filePath, AnalysisModel& model) {
    std::ifstream in(filePath);
    if (!in.is_open()) return false;
    
    std::stringstream ss;
    ss << in.rdbuf();
    in.close();
    
    try {
        model.fromJson(ss.str());
        return true;
    } catch (...) {
        return false;
    }
}

} // namespace FEA
