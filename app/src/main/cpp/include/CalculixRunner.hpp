#ifndef CALCULIX_RUNNER_HPP
#define CALCULIX_RUNNER_HPP

#include <string>
#include <vector>
#include <functional>
#include "AnalysisModel.hpp"

namespace FEA {

struct JobStatus {
    std::string jobName;
    bool running;
    int exitCode;
    std::string output;
};

class CalculixRunner {
public:
    CalculixRunner(const std::string& workDir, const std::string& libDir);

    // Run a job using an AnalysisModel
    JobStatus runJob(const std::string& jobName, const AnalysisModel& model);

    // Run a job from an existing .inp file
    JobStatus runInp(const std::string& jobName, const std::string& inpPath);

private:
    std::string workDir;
    std::string libDir;
    
    std::string getBinaryPath(const std::string& name);
    void setupEnvironment();
};

} // namespace FEA

#endif // CALCULIX_RUNNER_HPP
