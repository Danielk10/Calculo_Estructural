// Gmsh Concentric Cube in Cube
SetFactory("OpenCASCADE");
Box(1) = {-20, -20, -20, 40, 40, 40};
Box(2) = {-10, -10, -10, 20, 20, 20};
BooleanDifference(3) = { Volume{1}; Delete; }{ Volume{2}; Delete; };
