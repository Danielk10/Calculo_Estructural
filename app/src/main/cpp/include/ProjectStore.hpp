#ifndef PROJECT_STORE_HPP
#define PROJECT_STORE_HPP

#include "AnalysisModel.hpp"
#include <string>

namespace FEA {

class ProjectStore {
public:
    static bool saveProject(const std::string& filePath, const AnalysisModel& model);
    static bool loadProject(const std::string& filePath, AnalysisModel& model);
};

} // namespace FEA

#endif // PROJECT_STORE_HPP
