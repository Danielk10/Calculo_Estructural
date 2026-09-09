// Gmsh - Copyright (C) 1997-2026 C. Geuzaine, J.-F. Remacle
//
// See the LICENSE.txt file in the Gmsh root directory for license information.
// Please report all issues on https://gitlab.onelab.info/gmsh/gmsh/issues.

#ifndef GMSH_CONFIG_H
#define GMSH_CONFIG_H

/* #undef HAVE_3M */
#define HAVE_64BIT_SIZE_T
/* #undef HAVE_ACIS */
#define HAVE_ALGLIB
#define HAVE_ANN
#define HAVE_BAMG
#define HAVE_BOUNDARY_LAYERS
/* #undef HAVE_BLAS */
#define HAVE_BLOSSOM
/* #undef HAVE_CAIRO */
#define HAVE_DLOPEN
#define HAVE_DINTEGRATION
#define HAVE_DOMHEX
#define HAVE_EIGEN
/* #undef HAVE_FLTK */
/* #undef HAVE_GEOMETRYCENTRAL */
#define HAVE_GMM
#define HAVE_HXT
/* #undef HAVE_LAPACK */
/* #undef HAVE_LIBCGNS */
/* #undef HAVE_LIBCGNS_CPEX0045 */
/* #undef HAVE_LIBJPEG */
/* #undef HAVE_LIBPNG */
#define HAVE_LIBZ
/* #undef HAVE_LINUX_JOYSTICK */
#define HAVE_MATHEX
#define HAVE_MED
#define HAVE_MESH
/* #undef HAVE_MESQUITE */
#define HAVE_METIS
/* #undef HAVE_MMG */
/* #undef HAVE_MPEG_ENCODE */
/* #undef HAVE_MPI */
/* #undef HAVE_MUMPS */
#define HAVE_NETGEN
#define HAVE_NII2MESH
/* #undef HAVE_NUMPY */
/* #undef HAVE_NO_INTPTR_T */
/* #undef HAVE_NO_SOCKLEN_T */
/* #undef HAVE_NO_STDINT_H */
/* #undef HAVE_NO_VSNPRINTF */
#define HAVE_OCC
#define HAVE_OCC_CAF
#define HAVE_ONELAB
/* #undef HAVE_ONELAB2 */
#define HAVE_ONELAB_METAMODEL
/* #undef HAVE_UDT */
/* #undef HAVE_OPENGL */
#define HAVE_OPTHOM
/* #undef HAVE_OSMESA */
/* #undef HAVE_P4EST */
/* #undef HAVE_PARASOLID */
/* #undef HAVE_PARASOLID_STEP */
#define HAVE_PARSER
/* #undef HAVE_PETSC */
/* #undef HAVE_PETSC4PY */
#define HAVE_PLUGINS
#define HAVE_POST
/* #undef HAVE_POPPLER */
#define HAVE_QUADTRI
#define HAVE_QUADMESHINGTOOLS
/* #undef HAVE_REVOROPT */
/* #undef HAVE_SALOME */
/* #undef HAVE_SGEOM */
/* #undef HAVE_SLEPC */
#define HAVE_SOLVER
#define HAVE_UNTANGLE
/* #undef HAVE_TAUCS */
#define HAVE_TETGENBR
#define HAVE_TINYXML2
/* #undef HAVE_TOUCHBAR */
/* #undef HAVE_VISUDEV */
#define HAVE_VOROPP
#define HAVE_WINSLOWUNTANGLER
/* #undef HAVE_ZIPPER */

#define GMSH_CONFIG_OPTIONS " 64Bit ALGLIB[contrib] ANN[contrib] Bamg Blossom BoundaryLayers DIntegration Dlopen DomHex Eigen[contrib] Gmm[contrib] Hxt MathEx[contrib] Med Mesh Metis[contrib] Netgen Nii2mesh ONELAB ONELABMetamodel OpenCASCADE OpenCASCADE-CAF OpenMP OptHom Parser Plugins Post QuadMeshingTools QuadTri Solver TetGen/BR TinyXML2[contrib] Untangle Voro++[contrib] WinslowUntangler Zlib tinyobjloader"



#endif
