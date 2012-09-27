#!/usr/bin/env bash

source setup.sh

export DEPS_FILE=$DIR/default.deps
export DEPS_LIB=$DIR/lib
export DEPS_CACHE=$DEPS_LIB/.cache.deps.md5
DEPS_SETTINGS="--ivy-settings=$DIR/typesafe-ivy-settings.xml --resolver=typesafe"

mkdir -p $DEPS_LIB 
export CLASSPATH=`find $DEPS_LIB -name "*.jar" | tr '\n' ':'`

function download_deps {
    echo "resolving from $DEPS_FILE..."

    results=$(cat $DEPS_FILE | deputy $DEPS_SETTINGS deps-resolved | deputy $DEPS_SETTINGS --grep=compile --quick resolved-transitive | deputy $DEPS_SETTINGS resolved-highest-versions | deputy $DEPS_SETTINGS resolved-results | grep -v "#pom" | grep -v "#ivy" | grep -v "#source" | grep -v "#javadoc")

    pushd $DEPS_LIB > /dev/null
    echo "downloading $(echo "$results" | wc -l | tr -d ' ') files to $DEPS_LIB..."

    files=$(echo "$results" | deputy $DEPS_SETTINGS results-download-file && { results_finished=""; })

    popd > /dev/null
    echo "completed $(echo "$files" | wc -l | tr -d ' ') files!"
}

export download_deps

if [ "$BASH_SOURCE" == "$0" ]; then #means we are calling this script directly
    download_deps
fi