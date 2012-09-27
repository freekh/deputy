#!/usr/bin/env bash

export DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

#install deputy if not already installed
DEPUTY_VERSION=0.3.0
which deputy > /dev/null || { curl http://freekh.github.com/deputy/files/deputy-$DEPUTY_VERSION/setup.sh | sh; } 

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
export ZINC=$TMP/$ZINC_NAME/bin/zinc

if ! [ -f $ZINC ]; then
    echo "zinc not found..."
    ZINC_TARBALL=$ZINC_NAME.tgz
    download $ZINC_TARBALL http://repo.typesafe.com/typesafe/zinc/com/typesafe/zinc/dist/$ZINC_VERSION/$ZINC_TARBALL
fi
###ZINC