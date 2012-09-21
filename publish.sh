#/bin/sh

TMPDIR=tmp
SBTVERSION=0.11.3
SBTLAUNCH=$TMPDIR/sbt-launch-$SBTVERSION.jar

if ! [ -e $SBTLAUNCH ]; then
    mkdir tmp > /dev/null
    curl http://typesafe.artifactoryonline.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/$SBTVERSION/sbt-launch.jar > $SBTLAUNCH
fi

#we set the commit here, because I could not figure out how to get sbt to pull the latest

if [ -z $DEPUTY_HOME ]; then
    abs_path=$PWD/`dirname $0`/../deputy
    pushd $abs_path > /dev/null
    DEPUTY_HOME=`pwd`
    popd > /dev/null
fi

uri=file://$DEPUTY_HOME
if ! [ -z $DEPUTY_HOME ]; then
    echo "Using deputy from here: '$uri' - set absolute path to DEPUTY_HOME to override!"
else
    echo "Using deputy from DEPUTY_HOME: '$uri'"
fi

read -p "Press [Enter] key to continue..."

java -Ddeputy.location="$uri" -Xms512M -Xmx1536M -Xss1M -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=384M -jar $SBTLAUNCH ";project {$uri}; publish"

git add $(git ls-files -o --exclude-standard)

git commit -m "auto publishing new version..."

read -p "Press [Enter] key to push changes..."
 
git push origin gh-pages
