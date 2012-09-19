#/bin/sh

TMPDIR=tmp
SBTVERSION=0.11.3
SBTLAUNCH=$TMPDIR/sbt-launch-$SBTVERSION.jar

if ! [ -e $SBTLAUNCH ]; then
    mkdir tmp > /dev/null
    curl http://typesafe.artifactoryonline.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/$SBTVERSION/sbt-launch.jar > $SBTLAUNCH
fi

commit=`curl https://api.github.com/repos/freekh/deputy/commits | grep sha | head -n 2 | tail -n 1 | sed -e 's/.*sha\"\:[ ]*\"\(.*\)\".*/\1/'`
java -Ddeputy.commit=$commit -Xms512M -Xmx1536M -Xss1M -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=384M -jar $SBTLAUNCH ";project {git://github.com/freekh/deputy.git#$commit}; publish"
