#----------------------------------------------------------------
# Generated CMake target import file for configuration "Release".
#----------------------------------------------------------------

# Commands may need to know the format version.
set(CMAKE_IMPORT_FILE_VERSION 1)

# Import target "medC" for configuration "Release"
set_property(TARGET medC APPEND PROPERTY IMPORTED_CONFIGURATIONS RELEASE)
set_target_properties(medC PROPERTIES
  IMPORTED_LOCATION_RELEASE "${_IMPORT_PREFIX}/lib/libmedC.so.14.0.1"
  IMPORTED_SONAME_RELEASE "libmedC.so.14"
  )

list(APPEND _cmake_import_check_targets medC )
list(APPEND _cmake_import_check_files_for_medC "${_IMPORT_PREFIX}/lib/libmedC.so.14.0.1" )

# Import target "medfwrap" for configuration "Release"
set_property(TARGET medfwrap APPEND PROPERTY IMPORTED_CONFIGURATIONS RELEASE)
set_target_properties(medfwrap PROPERTIES
  IMPORTED_LOCATION_RELEASE "${_IMPORT_PREFIX}/lib/libmedfwrap.so.14.0.1"
  IMPORTED_SONAME_RELEASE "libmedfwrap.so.14"
  )

list(APPEND _cmake_import_check_targets medfwrap )
list(APPEND _cmake_import_check_files_for_medfwrap "${_IMPORT_PREFIX}/lib/libmedfwrap.so.14.0.1" )

# Import target "med" for configuration "Release"
set_property(TARGET med APPEND PROPERTY IMPORTED_CONFIGURATIONS RELEASE)
set_target_properties(med PROPERTIES
  IMPORTED_LOCATION_RELEASE "${_IMPORT_PREFIX}/lib/libmed.so.14.0.1"
  IMPORTED_SONAME_RELEASE "libmed.so.14"
  )

list(APPEND _cmake_import_check_targets med )
list(APPEND _cmake_import_check_files_for_med "${_IMPORT_PREFIX}/lib/libmed.so.14.0.1" )

# Commands beyond this point should not need to know the version.
set(CMAKE_IMPORT_FILE_VERSION)
