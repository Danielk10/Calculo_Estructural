#include "ProjectStore.hpp"
#include <iostream>
#include <assert.h>
#include <cstdio>

void testProjectStore() {
    FEA::AnalysisModel model;
    model.nodes[1] = {1, 10.0, 20.0, 30.0};
    
    std::string testFile = "test_project.json";
    bool saved = FEA::ProjectStore::saveProject(testFile, model);
    assert(saved);
    
    FEA::AnalysisModel model2;
    bool loaded = FEA::ProjectStore::loadProject(testFile, model2);
    assert(loaded);
    
    assert(model2.nodes.size() == 1);
    assert(model2.nodes[1].x == 10.0);
    
    std::remove(testFile.c_str());
    std::cout << "ProjectStore Test Passed!\n";
}

int main() {
    testProjectStore();
    return 0;
}
