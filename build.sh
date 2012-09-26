#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

#install deputy if not already installed
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


###MAIN LOOP
#compile files

SOURCE_DIR=$DIR/src/
SCALA_DIR=$DIR/src/main/scala

DEPS_FILE=$DIR/default.deps
DEPS_SETTINGS="--ivy-settings=$DIR/typesafe-ivy-settings.xml --resolver=typesafe"
DEPS_CACHE=$TMP/deps.md5
DEPS_CACHE_RES1=$TMP/deps.results.1
DEPS_CACHE_RES2=$TMP/deps.results.2
DEPS_LIB=$DIR/lib

mkdir -p $DEPS_LIB 
CLASSPATH=`find $DEPS_LIB -name "*.jar" | tr '\n' ':'`

function compile {
    deps_checksum=`cat $DEPS_FILE | md5`
    
    if [ "$deps_checksum" != "`cat $DEPS_CACHE`" ]; then
        #exec_deputy_results="cat $DEPS_FILE | deputy $DEPS_SETTINGS deps-resolved | deputy $DEPS_SETTINGS --grep=compile --quick resolved-transitive | deputy $DEPS_SETTINGS resolved-highest-versions | deputy $DEPS_SETTINGS resolved-results | grep -v '#javadoc' | grep -v '#source' | grep -v '#pom' | grep -v '#ivy'"
        #echo "executing: $exec_deputy_results"
        #deputy_results=`eval $exec_deputy_results`
        #cat $DEPS_FILE | deputy $DEPS_SETTINGS deps-resolved | deputy $DEPS_SETTINGS --grep=compile --quick resolved-transitive | deputy $DEPS_SETTINGS resolved-highest-versions | deputy $DEPS_SETTINGS resolved-results | grep -v '#javadoc' | grep -v '#source' | grep -v '#pom' | grep -v '#ivy' > $DEPS_CACHE_RES2
        #TODO: clean old files?
        #if ! [ -z `grep -v <(cat $DEPS_CACHE_RES2 | sort) <(cat $DEPS_CACHE_RES1 | sort)` ]; then
        #    exec_deputy="grep -v <(cat $DEPS_CACHE_RES2 | sort) <(cat $DEPS_CACHE_RES1 | sort) | deputy results-download-file"
        #    pushd $DEPS_LIB
        #    echo "executing: $exec_deputy"
        #    eval $exec_deputy
        #    popd > /dev/null
        #fi
        rm -rf $DEPS_LIB
        mkdir -p $DEPS_LIB > /dev/null
        pushd $DEPS_LIB > /dev/null
        cat $DEPS_FILE | deputy $DEPS_SETTINGS deps-resolved | deputy $DEPS_SETTINGS --grep=compile --quick resolved-transitive | deputy $DEPS_SETTINGS resolved-highest-versions | deputy $DEPS_SETTINGS resolved-results | grep -v '#javadoc' | grep -v '#source' | grep -v '#pom' | grep -v '#ivy'  | deputy $DEPS_SETTINGS results-download-file
        popd > /dev/null
        CLASSPATH=`ls $DEPS_LIB/*.jar | tr '\n' ':'`
        #echo $deputy_results > $DEPS_CACHE_RES1
        echo $deps_checksum > $DEPS_CACHE

    fi

    #compile sources
    src_files=`find $SCALA_DIR -name "*.scala"`
    src_checksums=`echo $src_files | xargs md5`
    src_checksum=`echo $src_checksums | md5`
    if [ "$src_checksum" != "$last_src_checksum" ]; then
        $ZINC -analysis-cache $TMP/cache -cp $CLASSPATH -d $TARGET_DIR $src_files
        last_src_checksum="$src_checksum"
    fi
}


if ! [ -z $(echo "$*" | grep "\-c") ]; then
    ###NAILGUN
    control_c()
    {
        echo -ne "\nshutting down nailgun server..."
        $ZINC -shutdown
        exit $?
    }
 
    # trap keyboard interrupt (control-c)
    trap control_c SIGINT

    $ZINC -start #start nailgun
    ###NAILGUN

    while true; do
        compile
        sleep 1
    done
    
else 
    compile
fi
