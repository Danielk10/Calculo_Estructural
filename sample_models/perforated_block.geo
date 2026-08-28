
SetFactory("OpenCASCADE");
Box(1) = {0, 0, 0, 60, 30, 20};
Cylinder(2) = {30, 15, -5, 0, 0, 30, 7};
BooleanDifference(3) = { Volume{1}; Delete; }{ Volume{2}; Delete; };
