#----------------------------------------------------------------
# Generated CMake target import file for configuration "Release".
#----------------------------------------------------------------

# Commands may need to know the format version.
set(CMAKE_IMPORT_FILE_VERSION 1)

# Import target "TKernel" for configuration "Release"
set_property(TARGET TKernel APPEND PROPERTY IMPORTED_CONFIGURATIONS RELEASE)
set_target_properties(TKernel PROPERTIES
  IMPORTED_LOCATION_RELEASE "${_IMPORT_PREFIX}/lib/libTKernel.so.8.0.0"
  IMPORTED_SONAME_RELEASE "libTKernel.so."
  )

list(APPEND _cmake_import_check_targets TKernel )
list(APPEND _cmake_import_check_files_for_TKernel "${_IMPORT_PREFIX}/lib/libTKernel.so.8.0.0" )

# Import target "TKMath" for configuration "Release"
set_property(TARGET TKMath APPEND PROPERTY IMPORTED_CONFIGURATIONS RELEASE)
set_target_properties(TKMath PROPERTIES
  IMPORTED_LOCATION_RELEASE "${_IMPORT_PREFIX}/lib/libTKMath.so.8.0.0"
  IMPORTED_SONAME_RELEASE "libTKMath.so."
  )

list(APPEND _cmake_import_check_targets TKMath )
list(APPEND _cmake_import_check_files_for_TKMath "${_IMPORT_PREFIX}/lib/libTKMath.so.8.0.0" )

# Commands beyond this point should not need to know the version.
set(CMAKE_IMPORT_FILE_VERSION)
