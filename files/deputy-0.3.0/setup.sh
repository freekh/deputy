#!/usr/bin/env bash
VERSION=0.3.0

DEPUTY_HTTP_FILES=http://freekh.github.com/deputy/files
DEPUTY_HOME=$HOME/.deputy

DEPUTY_NAME=deputy-$VERSION
DEPUTY_TARBALL=$DEPUTY_NAME.tar.gz
DEPUT_CLASSPATH_JARS_URLS=jar.urls

DEPUTY_DIR=$DEPUTY_HOME/$DEPUTY_NAME
DEPUTY_LIB=$DEPUTY_DIR/lib
DEPUTY_JAR=$DEPUTY_DIR/deputy.jar

echo "installing deputy to: $DEPUTY_HOME..."

mkdir -p $DEPUTY_DIR > /dev/null
curl $DEPUTY_HTTP_FILES/$DEPUTY_NAME/$DEPUTY_TARBALL > $DEPUTY_HOME/$DEPUTY_TARBALL || { echo  "Could not download deputy from: $DEPUTY_HTTP_FILES/$DEPUTY_NAME/$DEPUTY_TARBALL"; exit -1; }

tar -xvzf $DEPUTY_HOME/$DEPUTY_TARBALL -C $DEPUTY_HOME  || { echo  "Got corrupt deputy package from: $DEPUTY_HTTP_FILES/$DEPUTY_NAME/$DEPUTY_TARBALL"; exit -1; }

echo "downloading dependencies..."

mkdir -p $DEPUTY_LIB
pushd $DEPUTY_LIB > /dev/null
for jar_url in `cat $DEPUTY_DIR/$DEPUT_CLASSPATH_JARS_URLS`; do
   curl -LO $jar_url 
   if [ $? -ne 0 ]; then
       echo "Could not download dependency: $jar_url"
       exit -1
   fi

done
popd > /dev/null

echo "
#!/bin/sh
VERSION=$VERSION

if ! [ -z \$DEPUTY_HOME ]; then
    DEPUTY_HOME=$DEPUTY_HOME
fi
DEPUTY_CP=\`find $DEPUTY_LIB | tr '\\\n' ':'\`

DEPUTY_JAR=\$DEPUTY_HOME/deputy-\$VERSION/deputy.jar

java \$DEPUTY_OPTS -cp \$DEPUTY_CP:$DEPUTY_JAR deputy.Deputy \"\$@\"
" > $HOME/bin/deputy

chmod 755 $HOME/bin/deputy

echo "completed! type: deputy --help to learn more..."