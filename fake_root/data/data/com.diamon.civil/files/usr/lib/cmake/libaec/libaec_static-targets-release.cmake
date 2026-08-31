#----------------------------------------------------------------
# Generated CMake target import file for configuration "Release".
#----------------------------------------------------------------

# Commands may need to know the format version.
set(CMAKE_IMPORT_FILE_VERSION 1)

# Import target "libaec::aec-static" for configuration "Release"
set_property(TARGET libaec::aec-static APPEND PROPERTY IMPORTED_CONFIGURATIONS RELEASE)
set_target_properties(libaec::aec-static PROPERTIES
  IMPORTED_LINK_INTERFACE_LANGUAGES_RELEASE "C"
  IMPORTED_LOCATION_RELEASE "${_IMPORT_PREFIX}/lib/libaec.a"
  )

list(APPEND _cmake_import_check_targets libaec::aec-static )
list(APPEND _cmake_import_check_files_for_libaec::aec-static "${_IMPORT_PREFIX}/lib/libaec.a" )

# Import target "libaec::sz-static" for configuration "Release"
set_property(TARGET libaec::sz-static APPEND PROPERTY IMPORTED_CONFIGURATIONS RELEASE)
set_target_properties(libaec::sz-static PROPERTIES
  IMPORTED_LINK_INTERFACE_LANGUAGES_RELEASE "C"
  IMPORTED_LOCATION_RELEASE "${_IMPORT_PREFIX}/lib/libsz.a"
  )

list(APPEND _cmake_import_check_targets libaec::sz-static )
list(APPEND _cmake_import_check_files_for_libaec::sz-static "${_IMPORT_PREFIX}/lib/libsz.a" )

# Commands beyond this point should not need to know the version.
set(CMAKE_IMPORT_FILE_VERSION)
