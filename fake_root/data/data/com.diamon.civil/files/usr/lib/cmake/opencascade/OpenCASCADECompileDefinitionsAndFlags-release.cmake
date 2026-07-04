#-----------------------------------------------------------------------------
#
# OpenCASCADECompileDefinitionsAndFlags-release.cmake - OpenCASCADE CMake file 
# with compile definitions and C/C++ flags for Release configuration.
#
# This file is configured by OpenCASCADE.
#

# The C and C++ flags added by OpenCASCADE to the cmake-configured flags.
set (OpenCASCADE_C_FLAGS_RELEASE      "-O3 -DNDEBUG -DNo_Exception")
set (OpenCASCADE_CXX_FLAGS_RELEASE    "-O3 -DNDEBUG -DNo_Exception")

# The compile definitions used by OpenCASCADE.
set_property(DIRECTORY APPEND PROPERTY COMPILE_DEFINITIONS $<$<CONFIG:Release>:HAVE_FREETYPE>)
set_property(DIRECTORY APPEND PROPERTY COMPILE_DEFINITIONS $<$<CONFIG:Release>:HAVE_FREEIMAGE>)
set_property(DIRECTORY APPEND PROPERTY COMPILE_DEFINITIONS $<$<CONFIG:Release>:HAVE_GLES2_EXT>)
set_property(DIRECTORY APPEND PROPERTY COMPILE_DEFINITIONS $<$<CONFIG:Release>:OCC_CONVERT_SIGNALS>)
set_property(DIRECTORY APPEND PROPERTY COMPILE_DEFINITIONS $<$<CONFIG:Release>:HAVE_TK>)

