# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

cmake_minimum_required(VERSION 4.1.2)

project("calculoestructural")

add_library(${CMAKE_PROJECT_NAME} SHARED
        native-lib.cpp
        AnalysisModel.cpp
        frd_converter.cpp
        ProjectStore.cpp
        OcctPrimitivesJNI.cpp
        OcctBooleanJNI.cpp)

# Include directories
# Note: In Android, headers should be provided via a standard directory. 
# Re-mapping to a standard location inside jniLibs or a dedicated include folder.
target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE 
        include
        ${CMAKE_SOURCE_DIR}/include/opencascade)

# Define location of prebuilt OCCT libraries
set(OCCT_LIBS_DIR ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

# Helper function to import OCCT libraries with NORMALIZED NAMES
function(import_occt_lib LIB_NAME PHYSICAL_NAME)
    add_library(${LIB_NAME} SHARED IMPORTED)
    set_target_properties(${LIB_NAME} PROPERTIES IMPORTED_LOCATION ${OCCT_LIBS_DIR}/lib${PHYSICAL_NAME}.so)
endfunction()

# Import required OCCT modules using physical names defined in DOCUMENTACION_RENOMBRADO_BINARIOS.md
import_occt_lib(TKPrim TKPrim)
import_occt_lib(TKTopAlgo TKTopAlgo)
import_occt_lib(TKBO TKBO)
import_occt_lib(TKBool TKBool)
import_occt_lib(TKBRep TKBRep)
import_occt_lib(TKMath TKMath)
import_occt_lib(TKernel TKernel)
import_occt_lib(TKGeomBase TKGeomBase)
import_occt_lib(TKG3d TKG3d)
import_occt_lib(TKG2d TKG2d)

# Specifies libraries CMake should link to your target library.
target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        log
        TKPrim
        TKTopAlgo
        TKBO
        TKBool
        TKBRep
        TKMath
        TKernel
        TKGeomBase
        TKG3d
        TKG2d)
