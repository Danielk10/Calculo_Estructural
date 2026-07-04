#include <jni.h>
#include <string>
#include <BRepAlgoAPI_Fuse.hxx>
#include <BRepAlgoAPI_Cut.hxx>
#include <BRepAlgoAPI_Common.hxx>
#include <BRepTools.hxx>
#include <TopoDS_Shape.hxx>
#include <BRep_Builder.hxx>

TopoDS_Shape readShape(const char* path) {
    TopoDS_Shape shape;
    BRep_Builder builder;
    BRepTools::Read(shape, path, builder);
    return shape;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_diamon_civil_engine_OcctBooleanJNI_fuse(JNIEnv *env, jclass clazz, jstring path_a, jstring path_b, jstring out_path) {
    const char *pa = env->GetStringUTFChars(path_a, nullptr);
    const char *pb = env->GetStringUTFChars(path_b, nullptr);
    const char *po = env->GetStringUTFChars(out_path, nullptr);

    try {
        TopoDS_Shape shapeA = readShape(pa);
        TopoDS_Shape shapeB = readShape(pb);
        
        BRepAlgoAPI_Fuse fuse(shapeA, shapeB);
        fuse.Build();
        
        jboolean success = JNI_FALSE;
        if (fuse.IsDone()) {
            success = BRepTools::Write(fuse.Shape(), po);
        }

        env->ReleaseStringUTFChars(path_a, pa);
        env->ReleaseStringUTFChars(path_b, pb);
        env->ReleaseStringUTFChars(out_path, po);
        return success;
    } catch (...) {
        env->ReleaseStringUTFChars(path_a, pa);
        env->ReleaseStringUTFChars(path_b, pb);
        env->ReleaseStringUTFChars(out_path, po);
        return JNI_FALSE;
    }
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_diamon_civil_engine_OcctBooleanJNI_cut(JNIEnv *env, jclass clazz, jstring path_a, jstring path_b, jstring out_path) {
    const char *pa = env->GetStringUTFChars(path_a, nullptr);
    const char *pb = env->GetStringUTFChars(path_b, nullptr);
    const char *po = env->GetStringUTFChars(out_path, nullptr);

    try {
        TopoDS_Shape shapeA = readShape(pa);
        TopoDS_Shape shapeB = readShape(pb);
        
        BRepAlgoAPI_Cut cut(shapeA, shapeB);
        cut.Build();
        
        jboolean success = JNI_FALSE;
        if (cut.IsDone()) {
            success = BRepTools::Write(cut.Shape(), po);
        }

        env->ReleaseStringUTFChars(path_a, pa);
        env->ReleaseStringUTFChars(path_b, pb);
        env->ReleaseStringUTFChars(out_path, po);
        return success;
    } catch (...) {
        env->ReleaseStringUTFChars(path_a, pa);
        env->ReleaseStringUTFChars(path_b, pb);
        env->ReleaseStringUTFChars(out_path, po);
        return JNI_FALSE;
    }
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_diamon_civil_engine_OcctBooleanJNI_intersect(JNIEnv *env, jclass clazz, jstring path_a, jstring path_b, jstring out_path) {
    const char *pa = env->GetStringUTFChars(path_a, nullptr);
    const char *pb = env->GetStringUTFChars(path_b, nullptr);
    const char *po = env->GetStringUTFChars(out_path, nullptr);

    try {
        TopoDS_Shape shapeA = readShape(pa);
        TopoDS_Shape shapeB = readShape(pb);
        
        BRepAlgoAPI_Common common(shapeA, shapeB);
        common.Build();
        
        jboolean success = JNI_FALSE;
        if (common.IsDone()) {
            success = BRepTools::Write(common.Shape(), po);
        }

        env->ReleaseStringUTFChars(path_a, pa);
        env->ReleaseStringUTFChars(path_b, pb);
        env->ReleaseStringUTFChars(out_path, po);
        return success;
    } catch (...) {
        env->ReleaseStringUTFChars(path_a, pa);
        env->ReleaseStringUTFChars(path_b, pb);
        env->ReleaseStringUTFChars(out_path, po);
        return JNI_FALSE;
    }
}
