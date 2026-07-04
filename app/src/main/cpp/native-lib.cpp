#include <jni.h>
#include <string>
#include <memory>
#include "AnalysisModel.hpp"
#include "CalculixRunner.hpp"
#include "ProjectStore.hpp"

extern "C" JNIEXPORT jlong JNICALL
Java_com_diamon_civil_engine_NativeFeaCore_createModel(JNIEnv* env, jobject) {
    return reinterpret_cast<jlong>(new FEA::AnalysisModel());
}

extern "C" JNIEXPORT void JNICALL
Java_com_diamon_civil_engine_NativeFeaCore_deleteModel(JNIEnv* env, jobject, jlong ptr) {
    delete reinterpret_cast<FEA::AnalysisModel*>(ptr);
}

extern "C" JNIEXPORT void JNICALL
Java_com_diamon_civil_engine_NativeFeaCore_clearModel(JNIEnv* env, jobject, jlong ptr) {
    auto model = reinterpret_cast<FEA::AnalysisModel*>(ptr);
    if (model) model->clear();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_diamon_civil_engine_NativeFeaCore_modelToInp(JNIEnv* env, jobject, jlong ptr) {
    auto model = reinterpret_cast<FEA::AnalysisModel*>(ptr);
    if (!model) return env->NewStringUTF("");
    return env->NewStringUTF(model->toInpString().c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_diamon_civil_engine_NativeFeaCore_modelToJson(JNIEnv* env, jobject, jlong ptr) {
    auto model = reinterpret_cast<FEA::AnalysisModel*>(ptr);
    if (!model) return env->NewStringUTF("");
    return env->NewStringUTF(model->toJson().c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_diamon_civil_engine_NativeFeaCore_modelFromJson(JNIEnv* env, jobject, jlong ptr, jstring jJson) {
    auto model = reinterpret_cast<FEA::AnalysisModel*>(ptr);
    if (!model) return;
    const char* jsonStr = env->GetStringUTFChars(jJson, nullptr);
    model->fromJson(jsonStr);
    env->ReleaseStringUTFChars(jJson, jsonStr);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_diamon_civil_engine_NativeFeaCore_saveProject(JNIEnv* env, jobject, jstring jPath, jlong ptr) {
    auto model = reinterpret_cast<FEA::AnalysisModel*>(ptr);
    if (!model) return JNI_FALSE;
    const char* path = env->GetStringUTFChars(jPath, nullptr);
    bool success = FEA::ProjectStore::saveProject(path, *model);
    env->ReleaseStringUTFChars(jPath, path);
    return success ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_diamon_civil_engine_NativeFeaCore_loadProject(JNIEnv* env, jobject, jstring jPath, jlong ptr) {
    auto model = reinterpret_cast<FEA::AnalysisModel*>(ptr);
    if (!model) return JNI_FALSE;
    const char* path = env->GetStringUTFChars(jPath, nullptr);
    bool success = FEA::ProjectStore::loadProject(path, *model);
    env->ReleaseStringUTFChars(jPath, path);
    return success ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_diamon_civil_engine_NativeFeaCore_runCalculix(JNIEnv* env, jobject, jstring jWorkDir, jstring jLibDir, jstring jJobName, jlong ptr) {
    auto model = reinterpret_cast<FEA::AnalysisModel*>(ptr);
    if (!model) return env->NewStringUTF("Error: Invalid model pointer");
    
    const char* workDir = env->GetStringUTFChars(jWorkDir, nullptr);
    const char* libDir = env->GetStringUTFChars(jLibDir, nullptr);
    const char* jobName = env->GetStringUTFChars(jJobName, nullptr);
    
    FEA::CalculixRunner runner(workDir, libDir);
    FEA::JobStatus status = runner.runJob(jobName, *model);
    
    env->ReleaseStringUTFChars(jWorkDir, workDir);
    env->ReleaseStringUTFChars(jLibDir, libDir);
    env->ReleaseStringUTFChars(jJobName, jobName);
    
    return env->NewStringUTF(status.output.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_diamon_civil_ui_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    FEA::AnalysisModel model;
    std::string hello = "CalculiX Bridge Ready. V2.0";
    return env->NewStringUTF(hello.c_str());
}