#include "CalculixRunner.hpp"
#include <fstream>
#include <iostream>
#include <cstdio>
#include <memory>
#include <stdexcept>
#include <array>
#include <unistd.h>
#include <sys/wait.h>
#include <cstring>
#include <limits.h>

namespace FEA {

CalculixRunner::CalculixRunner(const std::string& workDir, const std::string& libDir)
    : workDir(workDir), libDir(libDir) {}

JobStatus CalculixRunner::runJob(const std::string& jobName, const AnalysisModel& model) {
    std::string inpContent = model.toInpString();
    std::string inpPath = workDir + "/" + jobName + ".inp";
    
    std::ofstream out(inpPath);
    if (!out.is_open()) {
        return {jobName, false, -1, "Error: Could not write .inp file at " + inpPath};
    }
    out << inpContent;
    out.close();

    return runInp(jobName, inpPath);
}

JobStatus CalculixRunner::runInp(const std::string& jobName, const std::string& inpPath) {
    std::string ccxPath = getBinaryPath("ccx");
    if (ccxPath.empty()) {
        return {jobName, false, -1, "Error: ccx binary not found"};
    }

    // Get absolute path of ccx if it's not already
    char absCcxPath[PATH_MAX];
    if (realpath(ccxPath.c_str(), absCcxPath) == nullptr) {
        // If realpath fails, just use the original
        strncpy(absCcxPath, ccxPath.c_str(), sizeof(absCcxPath));
    }

    // Command to execute
    // On Android, we need to set LD_LIBRARY_PATH
    std::string command = "export LD_LIBRARY_PATH=" + libDir + ":" + workDir + "/usr/lib:$LD_LIBRARY_PATH && ";
    command += "export OMP_NUM_THREADS=$(nproc) && ";
    command += std::string(absCcxPath) + " " + jobName;

    JobStatus status;
    status.jobName = jobName;
    status.running = true;

    std::array<char, 128> buffer;
    std::string result;
    
    // Change directory to workDir before running
    char currentDir[1024];
    getcwd(currentDir, sizeof(currentDir));
    chdir(workDir.c_str());

    FILE* pipe = popen(command.c_str(), "r");
    if (!pipe) {
        chdir(currentDir);
        return {jobName, false, -1, "Error: popen() failed"};
    }

    while (fgets(buffer.data(), buffer.size(), pipe) != nullptr) {
        result += buffer.data();
    }

    int returnCode = pclose(pipe);
    chdir(currentDir);

    status.running = false;
    status.exitCode = WEXITSTATUS(returnCode);
    status.output = result;

    return status;
}

std::string CalculixRunner::getBinaryPath(const std::string& name) {
    // Check in workDir/usr/bin
    std::string path = workDir + "/usr/bin/" + name;
    if (access(path.c_str(), F_OK) == 0) return path;

    // Check in libDir (as libname.so)
    path = libDir + "/lib" + name + ".so";
    if (access(path.c_str(), F_OK) == 0) return path;
    
    // Special case for gmsh
    if (name == "gmsh") {
        path = libDir + "/libgmsh_bin.so";
        if (access(path.c_str(), F_OK) == 0) return path;
    }

    return "";
}

} // namespace FEA
