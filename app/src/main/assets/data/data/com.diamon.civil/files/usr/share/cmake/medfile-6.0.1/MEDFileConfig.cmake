# - Config file for the MEDFile package
# It defines the following variables. 
# Specific to the pacakge MEDFile itself:
#  MEDFILE_INCLUDE_DIRS - include directories 
#  MEDFILE_LIBRARIES    - libraries to link against (C and Fortran)
#  MEDFILE_C_LIBRARIES  - C libraries only
#  MEDFILE_EXTRA_LIBRARIES 
#  MEDFILE_ROOT_DIR_EXP - the root path of the installation providing this CMake file
#
# Other stuff specific to this package:
#  1. Dependencies of MEDFILE that might be re-used by dependent modules:
#   HDF5_ROOT_DIR_EXP  - path to the HDF5 installation used by MEDFile
#   MPI_ROOT_DIR_EXP   - path to the MPI installation used by MEDFile
#  2. Some flags
#   MEDFILE_USE_MPI   - boolean indicating if med fichier was compiled with MPI support

### Initialisation performed by CONFIGURE_PACKAGE_CONFIG_FILE:

####### Expanded from @PACKAGE_INIT@ by configure_package_config_file() #######
####### Any changes to this file will be overwritten by the next CMake run ####
####### The input file was MEDFileConfig.cmake.in                            ########

get_filename_component(PACKAGE_PREFIX_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../" ABSOLUTE)

macro(set_and_check _var _file)
  set(${_var} "${_file}")
  if(NOT EXISTS "${_file}")
    message(FATAL_ERROR "File or directory ${_file} referenced by variable ${_var} does not exist !")
  endif()
endmacro()

macro(check_required_components _NAME)
  foreach(comp ${${_NAME}_FIND_COMPONENTS})
    if(NOT ${_NAME}_${comp}_FOUND)
      if(${_NAME}_FIND_REQUIRED_${comp})
        set(${_NAME}_FOUND FALSE)
      endif()
    endif()
  endforeach()
endmacro()

####################################################################################

### First the generic stuff for a standard module:

SET(MEDFILE_INCLUDE_DIRS "${PACKAGE_PREFIX_DIR}/include")

# Load the dependencies for the libraries of MEDFile 
# (contains definitions for IMPORTED targets). This is only 
# imported if we are not built as a subproject (in this case targets are already there)
IF(NOT TARGET medC AND NOT MEDFile_BINARY_DIR)
  # Load the Fortran targets if MED-file was compiled with it
  INCLUDE("${PACKAGE_PREFIX_DIR}/share/cmake/medfile-6.0.1/MEDFileTargets.cmake")
ENDIF()   

# Options exported by the package:
SET(MEDFILE_USE_MPI OFF)
SET(MEDFILE_VERSION 6.0.1)
SET(MEDFILE_BUILD_SHARED_LIBS ON)
SET(MEDFILE_BUILD_STATIC_LIBS OFF)

# These are IMPORTED targets created by MEDFileTargets.cmake
IF(MEDFILE_BUILD_SHARED_LIBS)
  SET(MEDFILE_C_LIBRARIES medC)
  SET(MEDFILE_EXTRA_LIBRARIES medimportengine)
  SET(MEDFILE_LIBRARIES medC medfwrap med)
ENDIF()
IF(MEDFILE_BUILD_STATIC_LIBS)
  SET(MEDFILE_C_LIBRARIES medC_static)
  SET(MEDFILE_EXTRA_LIBRARIES medimportengine_static)
  SET(MEDFILE_LIBRARIES medC_static med_static medfwrap_static)
ENDIF()

# Package root dir:
SET_AND_CHECK(MEDFILE_ROOT_DIR_EXP "${PACKAGE_PREFIX_DIR}")

# If HDF5 was found in CONFIG mode, we need to include its targets so that
# dependent projects can compile
SET(_hdf5_path "HDF5_DIR-NOTFOUND")
IF(_hdf5_path)
  FIND_PACKAGE(HDF5 REQUIRED COMPONENTS C 
         NO_MODULE PATHS "HDF5_DIR-NOTFOUND" NO_DEFAULT_PATH) 
ENDIF()

#### Now the specificities
# Dependencies *directly* used by the package and exported
# for forward reference:
# We set them only if they were used:
SET_AND_CHECK(HDF5_ROOT_DIR_EXP "/data/data/com.termux/files/home/fake_root${PACKAGE_PREFIX_DIR}")
IF(MEDFILE_USE_MPI)
  SET_AND_CHECK(MPI_ROOT_DIR_EXP  "${PACKAGE_PREFIX_DIR}/")
ENDIF()

