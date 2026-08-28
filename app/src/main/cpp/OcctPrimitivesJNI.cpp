#include <jni.h>
#include <string>
#include <BRepPrimAPI_MakeBox.hxx>
#include <BRepPrimAPI_MakeCylinder.hxx>
#include <BRepPrimAPI_MakeSphere.hxx>
#include <BRepTools.hxx>
#include <TopoDS_Shape.hxx>
#include <BRepFilletAPI_MakeFillet.hxx>
#include <BRepFilletAPI_MakeChamfer.hxx>
#include <BRepPrimAPI_MakePrism.hxx>
#include <BRepAlgoAPI_Fuse.hxx>
#include <TopExp.hxx>
#include <TopExp_Explorer.hxx>
#include <TopTools_IndexedMapOfShape.hxx>
#include <TopTools_IndexedDataMapOfShapeListOfShape.hxx>
#include <TopoDS.hxx>
#include <TopoDS_Edge.hxx>
#include <TopoDS_Face.hxx>
#include <TopoDS_Solid.hxx>
#include <BRep_Tool.hxx>
#include <gp_Vec.hxx>
#include <BRep_Builder.hxx>
#include <BRepBndLib.hxx>
#include <Bnd_Box.hxx>

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_diamon_civil_solids_engine_OcctPrimitivesJNI_createBox(JNIEnv *env, jclass clazz, jdouble l, jdouble w, jdouble h, jstring out_path) {
    const char *path = env->GetStringUTFChars(out_path, nullptr);
    try {
        TopoDS_Shape shape = BRepPrimAPI_MakeBox(l, w, h).Shape();
        jboolean success = BRepTools::Write(shape, path);
        env->ReleaseStringUTFChars(out_path, path);
        return success;
    } catch (...) {
        env->ReleaseStringUTFChars(out_path, path);
        return JNI_FALSE;
    }
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_diamon_civil_solids_engine_OcctPrimitivesJNI_createCylinder(JNIEnv *env, jclass clazz, jdouble r, jdouble h, jstring out_path) {
    const char *path = env->GetStringUTFChars(out_path, nullptr);
    try {
        TopoDS_Shape shape = BRepPrimAPI_MakeCylinder(r, h).Shape();
        jboolean success = BRepTools::Write(shape, path);
        env->ReleaseStringUTFChars(out_path, path);
        return success;
    } catch (...) {
        env->ReleaseStringUTFChars(out_path, path);
        return JNI_FALSE;
    }
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_diamon_civil_solids_engine_OcctPrimitivesJNI_createSphere(JNIEnv *env, jclass clazz, jdouble r, jstring out_path) {
    const char *path = env->GetStringUTFChars(out_path, nullptr);
    try {
        TopoDS_Shape shape = BRepPrimAPI_MakeSphere(r).Shape();
        jboolean success = BRepTools::Write(shape, path);
        env->ReleaseStringUTFChars(out_path, path);
        return success;
    } catch (...) {
        env->ReleaseStringUTFChars(out_path, path);
        return JNI_FALSE;
    }
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_diamon_civil_solids_engine_OcctPrimitivesJNI_applyFillet(JNIEnv *env, jclass clazz, jstring in_path, jstring out_path, jdouble radius) {
    const char *inPathStr = env->GetStringUTFChars(in_path, nullptr);
    const char *outPathStr = env->GetStringUTFChars(out_path, nullptr);
    try {
        TopoDS_Shape shape;
        BRep_Builder builder;
        if (!BRepTools::Read(shape, inPathStr, builder)) {
            env->ReleaseStringUTFChars(in_path, inPathStr);
            env->ReleaseStringUTFChars(out_path, outPathStr);
            return JNI_FALSE;
        }

        TopTools_IndexedMapOfShape edgeMap;
        TopExp::MapShapes(shape, TopAbs_EDGE, edgeMap);
        if (edgeMap.IsEmpty()) {
            env->ReleaseStringUTFChars(in_path, inPathStr);
            env->ReleaseStringUTFChars(out_path, outPathStr);
            return JNI_FALSE;
        }

        // Try filleting all suitable non-degenerated edges
        BRepFilletAPI_MakeFillet mkFillet(shape);
        int added = 0;
        for (int i = 1; i <= edgeMap.Extent(); ++i) {
            TopoDS_Edge E = TopoDS::Edge(edgeMap(i));
            if (BRep_Tool::Degenerated(E)) continue;
            try {
                mkFillet.Add(radius, E);
                added++;
            } catch (...) {}
        }

        if (added > 0) {
            try {
                mkFillet.Build();
                if (mkFillet.IsDone()) {
                    TopoDS_Shape result = mkFillet.Shape();
                    jboolean success = BRepTools::Write(result, outPathStr);
                    env->ReleaseStringUTFChars(in_path, inPathStr);
                    env->ReleaseStringUTFChars(out_path, outPathStr);
                    return success;
                }
            } catch (...) {}
        }

        // Fallback: Try edge by edge until a valid fillet is built
        for (int i = 1; i <= edgeMap.Extent(); ++i) {
            TopoDS_Edge E = TopoDS::Edge(edgeMap(i));
            if (BRep_Tool::Degenerated(E)) continue;
            try {
                BRepFilletAPI_MakeFillet singleFillet(shape);
                singleFillet.Add(radius, E);
                singleFillet.Build();
                if (singleFillet.IsDone()) {
                    TopoDS_Shape result = singleFillet.Shape();
                    jboolean success = BRepTools::Write(result, outPathStr);
                    env->ReleaseStringUTFChars(in_path, inPathStr);
                    env->ReleaseStringUTFChars(out_path, outPathStr);
                    return success;
                }
            } catch (...) {}
        }

        env->ReleaseStringUTFChars(in_path, inPathStr);
        env->ReleaseStringUTFChars(out_path, outPathStr);
        return JNI_FALSE;
    } catch (...) {
        env->ReleaseStringUTFChars(in_path, inPathStr);
        env->ReleaseStringUTFChars(out_path, outPathStr);
        return JNI_FALSE;
    }
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_diamon_civil_solids_engine_OcctPrimitivesJNI_applyChamfer(JNIEnv *env, jclass clazz, jstring in_path, jstring out_path, jdouble distance) {
    const char *inPathStr = env->GetStringUTFChars(in_path, nullptr);
    const char *outPathStr = env->GetStringUTFChars(out_path, nullptr);
    try {
        TopoDS_Shape shape;
        BRep_Builder builder;
        if (!BRepTools::Read(shape, inPathStr, builder)) {
            env->ReleaseStringUTFChars(in_path, inPathStr);
            env->ReleaseStringUTFChars(out_path, outPathStr);
            return JNI_FALSE;
        }

        TopTools_IndexedDataMapOfShapeListOfShape edgeFaceMap;
        TopExp::MapShapesAndAncestors(shape, TopAbs_EDGE, TopAbs_FACE, edgeFaceMap);
        if (edgeFaceMap.IsEmpty()) {
            env->ReleaseStringUTFChars(in_path, inPathStr);
            env->ReleaseStringUTFChars(out_path, outPathStr);
            return JNI_FALSE;
        }

        // Try chamfering all edges with their actual adjacent face
        BRepFilletAPI_MakeChamfer mkChamfer(shape);
        int added = 0;
        for (int i = 1; i <= edgeFaceMap.Extent(); ++i) {
            TopoDS_Edge E = TopoDS::Edge(edgeFaceMap.FindKey(i));
            if (BRep_Tool::Degenerated(E)) continue;
            const TopTools_ListOfShape& faceList = edgeFaceMap.FindFromIndex(i);
            if (faceList.IsEmpty()) continue;
            TopoDS_Face F = TopoDS::Face(faceList.First());
            try {
                mkChamfer.Add(distance, distance, E, F);
                added++;
            } catch (...) {}
        }

        if (added > 0) {
            try {
                mkChamfer.Build();
                if (mkChamfer.IsDone()) {
                    TopoDS_Shape result = mkChamfer.Shape();
                    jboolean success = BRepTools::Write(result, outPathStr);
                    env->ReleaseStringUTFChars(in_path, inPathStr);
                    env->ReleaseStringUTFChars(out_path, outPathStr);
                    return success;
                }
            } catch (...) {}
        }

        // Fallback: Try single edge chamfer
        for (int i = 1; i <= edgeFaceMap.Extent(); ++i) {
            TopoDS_Edge E = TopoDS::Edge(edgeFaceMap.FindKey(i));
            if (BRep_Tool::Degenerated(E)) continue;
            const TopTools_ListOfShape& faceList = edgeFaceMap.FindFromIndex(i);
            if (faceList.IsEmpty()) continue;
            TopoDS_Face F = TopoDS::Face(faceList.First());
            try {
                BRepFilletAPI_MakeChamfer singleChamfer(shape);
                singleChamfer.Add(distance, distance, E, F);
                singleChamfer.Build();
                if (singleChamfer.IsDone()) {
                    TopoDS_Shape result = singleChamfer.Shape();
                    jboolean success = BRepTools::Write(result, outPathStr);
                    env->ReleaseStringUTFChars(in_path, inPathStr);
                    env->ReleaseStringUTFChars(out_path, outPathStr);
                    return success;
                }
            } catch (...) {}
        }

        env->ReleaseStringUTFChars(in_path, inPathStr);
        env->ReleaseStringUTFChars(out_path, outPathStr);
        return JNI_FALSE;
    } catch (...) {
        env->ReleaseStringUTFChars(in_path, inPathStr);
        env->ReleaseStringUTFChars(out_path, outPathStr);
        return JNI_FALSE;
    }
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_diamon_civil_solids_engine_OcctPrimitivesJNI_applyExtrude(JNIEnv *env, jclass clazz, jstring in_path, jstring out_path, jdouble dx, jdouble dy, jdouble dz) {
    const char *inPathStr = env->GetStringUTFChars(in_path, nullptr);
    const char *outPathStr = env->GetStringUTFChars(out_path, nullptr);
    try {
        TopoDS_Shape shape;
        BRep_Builder builder;
        if (!BRepTools::Read(shape, inPathStr, builder)) {
            env->ReleaseStringUTFChars(in_path, inPathStr);
            env->ReleaseStringUTFChars(out_path, outPathStr);
            return JNI_FALSE;
        }

        gp_Vec vec(dx, dy, dz);

        // If the shape is directly a 2D Face / Wire / Edge, extrude it directly into a solid
        if (shape.ShapeType() == TopAbs_FACE || shape.ShapeType() == TopAbs_WIRE || shape.ShapeType() == TopAbs_EDGE) {
            BRepPrimAPI_MakePrism prism(shape, vec);
            if (prism.IsDone()) {
                TopoDS_Shape result = prism.Shape();
                jboolean success = BRepTools::Write(result, outPathStr);
                env->ReleaseStringUTFChars(in_path, inPathStr);
                env->ReleaseStringUTFChars(out_path, outPathStr);
                return success;
            }
        }

        // If shape is a 3D Solid / Shell / Compound, find a candidate face (e.g. highest Z face)
        TopoDS_Face bestFace;
        double maxZ = -1e9;
        for (TopExp_Explorer exFace(shape, TopAbs_FACE); exFace.More(); exFace.Next()) {
            TopoDS_Face F = TopoDS::Face(exFace.Current());
            Bnd_Box box;
            BRepBndLib::Add(F, box);
            Standard_Real xmin, ymin, zmin, xmax, ymax, zmax;
            box.Get(xmin, ymin, zmin, xmax, ymax, zmax);
            if (zmax > maxZ) {
                maxZ = zmax;
                bestFace = F;
            }
        }

        if (!bestFace.IsNull()) {
            BRepPrimAPI_MakePrism prism(bestFace, vec);
            if (prism.IsDone()) {
                TopoDS_Shape extrudedPrism = prism.Shape();
                // Fuse the extruded feature with the base shape to form an extended 3D solid
                try {
                    BRepAlgoAPI_Fuse fuser(shape, extrudedPrism);
                    fuser.Build();
                    if (fuser.IsDone()) {
                        TopoDS_Shape result = fuser.Shape();
                        jboolean success = BRepTools::Write(result, outPathStr);
                        env->ReleaseStringUTFChars(in_path, inPathStr);
                        env->ReleaseStringUTFChars(out_path, outPathStr);
                        return success;
                    }
                } catch (...) {}

                // If fuse fails, write extruded prism shape
                jboolean success = BRepTools::Write(extrudedPrism, outPathStr);
                env->ReleaseStringUTFChars(in_path, inPathStr);
                env->ReleaseStringUTFChars(out_path, outPathStr);
                return success;
            }
        }

        env->ReleaseStringUTFChars(in_path, inPathStr);
        env->ReleaseStringUTFChars(out_path, outPathStr);
        return JNI_FALSE;
    } catch (...) {
        env->ReleaseStringUTFChars(in_path, inPathStr);
        env->ReleaseStringUTFChars(out_path, outPathStr);
        return JNI_FALSE;
    }
}
