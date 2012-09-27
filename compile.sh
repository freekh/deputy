#!/usr/bin/env bash

source setup.sh
source deps-download.sh

SOURCE_DIR=$DIR/src/
SCALA_DIR=$DIR/src/main/scala

function compile {
    src_files=`find $SCALA_DIR -name "*.scala"`
    src_checksums=`echo $src_files | xargs md5`
    src_checksum=`echo $src_checksums $(cat $DEPS_CACHE) | md5`
    if [ "$src_checksum" != "$last_src_checksum" ]; then
        $ZINC -analysis-cache $TMP/cache -cp $CLASSPATH -d $TARGET_DIR $src_files
        last_src_checksum="$src_checksum"
    fi
}

export compile

if [ "$BASH_SOURCE" == "$0" ]; then #means we are calling this script directly
    compile
fi