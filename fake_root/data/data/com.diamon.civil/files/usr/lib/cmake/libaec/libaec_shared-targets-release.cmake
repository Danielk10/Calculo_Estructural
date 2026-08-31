#----------------------------------------------------------------
# Generated CMake target import file for configuration "Release".
#----------------------------------------------------------------

# Commands may need to know the format version.
set(CMAKE_IMPORT_FILE_VERSION 1)

# Import target "libaec::aec-shared" for configuration "Release"
set_property(TARGET libaec::aec-shared APPEND PROPERTY IMPORTED_CONFIGURATIONS RELEASE)
set_target_properties(libaec::aec-shared PROPERTIES
  IMPORTED_LOCATION_RELEASE "${_IMPORT_PREFIX}/lib/libaec.so.0.1.7"
  IMPORTED_SONAME_RELEASE "libaec.so.0"
  )

list(APPEND _cmake_import_check_targets libaec::aec-shared )
list(APPEND _cmake_import_check_files_for_libaec::aec-shared "${_IMPORT_PREFIX}/lib/libaec.so.0.1.7" )

# Import target "libaec::sz-shared" for configuration "Release"
set_property(TARGET libaec::sz-shared APPEND PROPERTY IMPORTED_CONFIGURATIONS RELEASE)
set_target_properties(libaec::sz-shared PROPERTIES
  IMPORTED_LOCATION_RELEASE "${_IMPORT_PREFIX}/lib/libsz.so.2.0.1"
  IMPORTED_SONAME_RELEASE "libsz.so.2"
  )

list(APPEND _cmake_import_check_targets libaec::sz-shared )
list(APPEND _cmake_import_check_files_for_libaec::sz-shared "${_IMPORT_PREFIX}/lib/libsz.so.2.0.1" )

# Commands beyond this point should not need to know the version.
set(CMAKE_IMPORT_FILE_VERSION)
