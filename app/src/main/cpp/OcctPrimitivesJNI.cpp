#include <jni.h>
#include <string>
#include <BRepPrimAPI_MakeBox.hxx>
#include <BRepPrimAPI_MakeCylinder.hxx>
#include <BRepPrimAPI_MakeSphere.hxx>
#include <BRepTools.hxx>
#include <TopoDS_Shape.hxx>

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_diamon_civil_engine_OcctPrimitivesJNI_createBox(JNIEnv *env, jclass clazz, jdouble l, jdouble w, jdouble h, jstring out_path) {
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
Java_com_diamon_civil_engine_OcctPrimitivesJNI_createCylinder(JNIEnv *env, jclass clazz, jdouble r, jdouble h, jstring out_path) {
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
Java_com_diamon_civil_engine_OcctPrimitivesJNI_createSphere(JNIEnv *env, jclass clazz, jdouble r, jstring out_path) {
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
