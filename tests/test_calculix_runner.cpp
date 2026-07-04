#include "CalculixRunner.hpp"
#include <iostream>
#include <assert.h>
#include <fstream>
#include <unistd.h>
#include <sys/stat.h>

void testCalculixRunner() {
    std::string workDir = "./test_workdir";
    std::string libDir = "./test_libdir";
    
    // Create directories
    system("mkdir -p ./test_workdir/usr/bin");
    system("mkdir -p ./test_libdir");
    
    // Create a mock ccx binary
    std::string mockCcx = workDir + "/usr/bin/ccx";
    std::ofstream ccxOut(mockCcx);
    ccxOut << "#!/bin/sh\necho \"Mock CalculiX Output\"\necho \"Job: $1\"\n";
    ccxOut.close();
    chmod(mockCcx.c_str(), 0755);
    
    FEA::CalculixRunner runner(workDir, libDir);
    FEA::AnalysisModel model;
    model.nodes[1] = {1, 0, 0, 0};
    
    FEA::JobStatus status = runner.runJob("test_job", model);
    
    std::cout << "Exit Code: " << status.exitCode << "\n";
    std::cout << "Output:\n" << status.output << "\n";
    
    assert(status.exitCode == 0);
    assert(status.output.find("Mock CalculiX Output") != std::string::npos);
    assert(status.output.find("Job: test_job") != std::string::npos);
    
    // Check if .inp was created
    std::ifstream inpFile(workDir + "/test_job.inp");
    assert(inpFile.is_open());
    inpFile.close();
    
    std::cout << "CalculixRunner Test Passed!\n";
    
    // Cleanup
    system("rm -rf ./test_workdir ./test_libdir");
}

int main() {
    testCalculixRunner();
    return 0;
}
