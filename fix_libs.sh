#!/bin/bash
DIR="app/src/main/jniLibs/arm64-v8a"

echo "Cleaning up suspicious files..."
rm -f $DIR/*._so_.so

echo "Starting aggressive SONAME and NEEDED patching..."

# 1. Fix SONAME of every library to be its filename
for lib_path in $DIR/*.so; do
    [ -e "$lib_path" ] || continue
    lib=$(basename "$lib_path")
    echo "Setting SONAME of $lib to $lib"
    patchelf --set-soname "$lib" "$lib_path"
done

# 2. Fix NEEDED entries mapping to existing files
for lib_path in $DIR/*.so; do
    [ -e "$lib_path" ] || continue
    lib=$(basename "$lib_path")
    echo "Fixing dependencies of $lib..."
    deps=$(readelf -d "$lib_path" 2>/dev/null | grep NEEDED | sed -n 's/.*\[\(.*\)\].*/\1/p')
    for dep in $deps; do
        # System libs to completely ignore if exact match
        if [[ "$dep" == libc.so || "$dep" == libm.so || "$dep" == libdl.so || "$dep" == liblog.so || "$dep" == libandroid.so || "$dep" == libjnigraphics.so || "$dep" == libz.so || "$dep" == libGLESv2.so || "$dep" == libEGL.so ]]; then
            continue
        fi

        # If exactly this file exists in our folder, skip
        if [ -f "$DIR/$dep" ]; then
            continue
        fi

        # Try to find a match in the folder
        match=""
        
        # Try stripped version
        dep_base=$(echo "$dep" | sed -E 's/(\.so)(\.[0-9]+|\.)*$/\1/')
        if [ -f "$DIR/$dep_base" ]; then
            match="$dep_base"
        fi
        
        # Try fuzzy match
        if [ -z "$match" ]; then
            fuzzy=$(echo "$dep" | sed 's/\./_/g' | sed 's/_so$/.so/')
            if [[ "$fuzzy" != *.so ]]; then fuzzy="${fuzzy}.so"; fi
            if [ -f "$DIR/$fuzzy" ]; then
                match="$fuzzy"
            fi
        fi
        
        # Android System library mappings (handle versioned dependencies to unversioned system libs)
        if [ -z "$match" ]; then
            case "$dep" in
                libz.so.*) match="libz.so" ;;
                libEGL.so.*) match="libEGL.so" ;;
                libGLESv1_CM.so.*) match="libGLESv1_CM.so" ;;
                libGLESv2.so.*) match="libGLESv2.so" ;;
                libGLESv3.so.*) match="libGLESv3.so" ;;
            esac
        fi

        if [ -n "$match" ] && [ "$match" != "$dep" ]; then
            echo "  Mapping $dep -> $match in $lib"
            patchelf --replace-needed "$dep" "$match" "$lib_path"
        elif [ -z "$match" ]; then
             echo "  WARNING: Dependency $dep not found for $lib"
        fi
    done
done

echo "Patching complete."
