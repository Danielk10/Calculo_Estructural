#----------------------------------------------------------------
# Generated CMake target import file for configuration "Release".
#----------------------------------------------------------------

# Commands may need to know the format version.
set(CMAKE_IMPORT_FILE_VERSION 1)

# Import target "gmsh::shared" for configuration "Release"
set_property(TARGET gmsh::shared APPEND PROPERTY IMPORTED_CONFIGURATIONS RELEASE)
set_target_properties(gmsh::shared PROPERTIES
  IMPORTED_LOCATION_RELEASE "${_IMPORT_PREFIX}/lib/libgmsh.so.5.0.0"
  IMPORTED_SONAME_RELEASE "libgmsh.so.5.0"
  )

list(APPEND _cmake_import_check_targets gmsh::shared )
list(APPEND _cmake_import_check_files_for_gmsh::shared "${_IMPORT_PREFIX}/lib/libgmsh.so.5.0.0" )

# Commands beyond this point should not need to know the version.
set(CMAKE_IMPORT_FILE_VERSION)
