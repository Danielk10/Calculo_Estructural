#include <jni.h>
#include <string>
#include <memory>
#include <exception>
#include <fstream>
#include <sstream>
#include "AnalysisModel.hpp"
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
    if (!model || !jJson) return;
    const char* jsonStr = env->GetStringUTFChars(jJson, nullptr);
    if (!jsonStr) return;
    try {
        model->fromJson(jsonStr);
    } catch (const std::exception& error) {
        env->ReleaseStringUTFChars(jJson, jsonStr);
        jclass exceptionClass = env->FindClass("java/lang/IllegalArgumentException");
        if (exceptionClass) {
            env->ThrowNew(exceptionClass, error.what());
        }
        return;
    } catch (...) {
        env->ReleaseStringUTFChars(jJson, jsonStr);
        jclass exceptionClass = env->FindClass("java/lang/IllegalArgumentException");
        if (exceptionClass) {
            env->ThrowNew(exceptionClass, "Modelo estructural inválido");
        }
        return;
    }
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
Java_com_diamon_civil_engine_NativeFeaCore_parseDatResults(JNIEnv* env, jobject, jstring jPath) {
    const char* path = env->GetStringUTFChars(jPath, nullptr);
    std::ifstream file(path);
    if (!file.is_open()) {
        env->ReleaseStringUTFChars(jPath, path);
        return env->NewStringUTF("Error: Could not open .dat file");
    }

    std::stringstream ss;
    std::string line;
    while (std::getline(file, line)) {
        if (line.find("section forces") != std::string::npos || line.find("moment") != std::string::npos) {
            ss << line << "\n";
        }
    }
    env->ReleaseStringUTFChars(jPath, path);
    return env->NewStringUTF(ss.str().c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_diamon_civil_engine_NativeFeaCore_parseFrdSummary(JNIEnv* env, jobject, jstring jPath) {
    const char* path = env->GetStringUTFChars(jPath, nullptr);
    std::ifstream file(path);
    if (!file.is_open()) {
        env->ReleaseStringUTFChars(jPath, path);
        return env->NewStringUTF("Error: Could not open .frd file");
    }

    std::string line;
    int nodes = 0;
    while (std::getline(file, line)) {
        if (line.substr(0, 3) == " -1") nodes++;
    }
    env->ReleaseStringUTFChars(jPath, path);
    return env->NewStringUTF(("Nodes parsed natively: " + std::to_string(nodes)).c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_diamon_civil_ui_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    FEA::AnalysisModel model;
    std::string hello = "CalculiX Bridge Ready. V2.0";
    return env->NewStringUTF(hello.c_str());
}
