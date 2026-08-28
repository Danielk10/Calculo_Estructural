
SetFactory("OpenCASCADE");
Box(1) = {0, 0, 0, 100, 20, 20};
Box(2) = {0, 0, 0, 100, 7, 6};
Box(3) = {0, 13, 0, 100, 7, 6};
BooleanDifference(4) = { Volume{1}; Delete; }{ Volume{2, 3}; Delete; };
