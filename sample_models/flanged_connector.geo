
SetFactory("OpenCASCADE");
Cylinder(1) = {0, 0, 0, 80, 0, 0, 15};
Cylinder(2) = {-5, 0, 0, 90, 0, 0, 9};
Cylinder(3) = {20, 0, 0, 15, 0, 0, 25};
BooleanFuse(4) = { Volume{1}; Delete; }{ Volume{3}; Delete; };
BooleanDifference(5) = { Volume{4}; Delete; }{ Volume{2}; Delete; };
