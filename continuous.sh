source setup.sh
source deps-download.sh
source compile.sh

###handle deps in a smarter way
function deps {
    if [ -f $DEPS_FILE ]; then
        deps_checksum=`cat $DEPS_FILE | md5`
    fi
    if [ -f $DEPS_CACHE ]; then
        last_deps_checksum=`cat $DEPS_CACHE`
    fi
    if ! [ -f $DEPS_CACHE ] || [ "$deps_checksum" != "$last_deps_checksum" ]; then
        rm -r $DEPS_LIB
        mkdir -p $DEPS_LIB
        download_deps
        if [ -z $results_finished ]; then #success
            export CLASSPATH=`ls $DEPS_LIB/*.jar | tr '\n' ':'`
            echo $deps_checksum > $DEPS_CACHE
        fi
    fi
}

###NAILGUN: make compiler faster
control_c()
{
    echo -ne "\nshutting down nailgun server..."
    $ZINC -shutdown -nailed #shutdown if ctrl+c
    exit $?
}
 
# trap keyboard interrupt (control-c)
trap control_c SIGINT

$ZINC -start -nailed #start nailgun
###NAILGUN

while true; do
    deps
    compile
    sleep 1
done
