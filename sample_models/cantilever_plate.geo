// Gmsh Geometry Sample - Cantilever with Hole
SetFactory("OpenCASCADE");
Box(1) = {0, 0, 0, 100, 20, 20};
Cylinder(2) = {50, 10, 0, 0, 0, 20, 5};
BooleanDifference(3) = { Volume{1}; Delete; }{ Volume{2}; Delete; };

Physical Surface("Fixed") = {1}; // Base face
Physical Surface("Loaded") = {2}; // Tip face
Physical Volume("SolidBody") = {3};
