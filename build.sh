#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

#install deputy if not already installed
echo "using deputy from:"
which deputy || { curl http://freekh.github.com/deputy/files/deputy-0.3.0/setup.sh | sh; }

TARGET_DIR=$DIR/target
TMP=$DIR/tmp

function download {
    echo "downloading..."
    mkdir -p $TMP > /dev/null
    TARBALL=$1
    URL=$2
    curl -L $URL > $TMP/$TARBALL
    echo "done"
    echo "uncompressing..."
    pushd $TMP > /dev/null
    tar xfz $TMP/$TARBALL
    popd $TMP > /dev/null
    echo "done"
}


###ZINC
ZINC_VERSION=0.1.0
ZINC_NAME=zinc-$ZINC_VERSION
ZINC=$TMP/$ZINC_NAME/bin/zinc

if ! [ -f $ZINC ]; then
    echo "zinc not found..."
    ZINC_TARBALL=$ZINC_NAME.tgz
    download $ZINC_TARBALL http://repo.typesafe.com/typesafe/zinc/com/typesafe/zinc/dist/$ZINC_VERSION/$ZINC_TARBALL
fi
###ZINC


###HELPERS

###DEPS
DEPS_FILE=$DIR/default.deps
DEPS_SETTINGS="--ivy-settings=$DIR/typesafe-ivy-settings.xml --resolver=typesafe"
DEPS_CACHE_FILES=$TMP/deps.md5
DEPS_CACHE_RESULTS=$TMP/deps.results
DEPS_LIB=$DIR/lib

mkdir -p $DEPS_LIB 
CLASSPATH=`find $DEPS_LIB -name "*.jar" | tr '\n' ':'`

function get_deps {
    if [ -f $DEPS_FILE ]; then
        deps_checksum=`cat $DEPS_FILE | md5`
    fi
    if [ -f $DEPS_CACHE_FILES ]; then
        last_deps_checksum=`cat $DEPS_CACHE_FILES`
    fi
    if [ -f $DEPS_FILE ] && [ "$deps_checksum" != "$last_deps_checksum" ]; then
        echo "resolving..."
        results=`cat $DEPS_FILE | deputy $DEPS_SETTINGS deps-resolved | deputy $DEPS_SETTINGS --grep=compile --quick resolved-transitive | deputy $DEPS_SETTINGS resolved-highest-versions | deputy $DEPS_SETTINGS resolved-results | grep -v "#pom" | grep -v "#ivy" | grep -v "#source" | grep -v "#javadoc"`
        if [ -f $DEPS_CACHE_RESULTS ]; then
            new_results=`diff <(cat $DEPS_CACHE_RESULTS) <(echo "$results") | egrep '^(>)' | tr -d '> '`
            old_results=`diff <(cat $DEPS_CACHE_RESULTS) <(echo "$results") | egrep '^(<)' | tr -d '< '`
            old_files=`echo "$old_results" | sed 's#.*/##' | xargs -I {} echo $DEPS_LIB/{}`
            echo "removing old files..."
            echo "$old_files" | xargs rm
        else
            new_results=$results
        fi
        
        pushd $DEPS_LIB > /dev/null
        echo "downloading..."
        echo "$new_results" | deputy $DEPS_SETTINGS results-download-file
        popd > /dev/null
        echo "done!"
        
        CLASSPATH=`ls $DEPS_LIB/*.jar | tr '\n' ':'`
        echo $deps_checksum > $DEPS_CACHE_FILES
        echo "$results" > $DEPS_CACHE_RESULTS
    fi
}

###COMPILE
SOURCE_DIR=$DIR/src/
SCALA_DIR=$DIR/src/main/scala

function compile {
    src_files=`find $SCALA_DIR -name "*.scala"`
    src_checksums=`echo $src_files | xargs md5`
    src_checksum=`echo $src_checksums | md5`
    if [ "$src_checksum" != "$last_src_checksum" ]; then
        $ZINC -analysis-cache $TMP/cache -cp $CLASSPATH -d $TARGET_DIR $src_files
        last_src_checksum="$src_checksum"
    fi
}

###MAIN LOOP
if ! [ -z $(echo "$*" | grep "\-c") ]; then
    ###NAILGUN: make compiler faster
    control_c()
    {
        echo -ne "\nshutting down nailgun server..."
        $ZINC -shutdown -nailed
        exit $?
    }
 
    # trap keyboard interrupt (control-c)
    trap control_c SIGINT

    $ZINC -start -nailed #start nailgun
    ###NAILGUN

    while true; do
        get_deps
        compile
        sleep 1
    done
    
else
    get_deps
    compile
fi
