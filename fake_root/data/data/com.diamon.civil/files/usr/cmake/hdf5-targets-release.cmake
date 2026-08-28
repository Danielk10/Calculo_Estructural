#----------------------------------------------------------------
# Generated CMake target import file for configuration "Release".
#----------------------------------------------------------------

# Commands may need to know the format version.
set(CMAKE_IMPORT_FILE_VERSION 1)

# Import target "hdf5-shared" for configuration "Release"
set_property(TARGET hdf5-shared APPEND PROPERTY IMPORTED_CONFIGURATIONS RELEASE)
set_target_properties(hdf5-shared PROPERTIES
  IMPORTED_LOCATION_RELEASE "${_IMPORT_PREFIX}/lib/libhdf5.so.1000.0.0"
  IMPORTED_SONAME_RELEASE "libhdf5.so.1000"
  )

list(APPEND _cmake_import_check_targets hdf5-shared )
list(APPEND _cmake_import_check_files_for_hdf5-shared "${_IMPORT_PREFIX}/lib/libhdf5.so.1000.0.0" )

# Import target "hdf5_hl-shared" for configuration "Release"
set_property(TARGET hdf5_hl-shared APPEND PROPERTY IMPORTED_CONFIGURATIONS RELEASE)
set_target_properties(hdf5_hl-shared PROPERTIES
  IMPORTED_LOCATION_RELEASE "${_IMPORT_PREFIX}/lib/libhdf5_hl.so.1000.0.0"
  IMPORTED_SONAME_RELEASE "libhdf5_hl.so.1000"
  )

list(APPEND _cmake_import_check_targets hdf5_hl-shared )
list(APPEND _cmake_import_check_files_for_hdf5_hl-shared "${_IMPORT_PREFIX}/lib/libhdf5_hl.so.1000.0.0" )

# Commands beyond this point should not need to know the version.
set(CMAKE_IMPORT_FILE_VERSION)
