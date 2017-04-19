#!/bin/bash -v
#
#  Download all airport PDF files and convert to GIF files
#
#  Output is datums/aptplates_$expdate/gif_150/...
#                   aptplates_$expdate/state/$state.csv
#
#  They are on 28 day cycle
#
#  Takes 3 hours to run
#
#  $1 = "" : do all plates of all airports
#  $1 = icaoid : do all plates of just the one airport
#
cd `dirname $0`

. downloadone.si

#
#  Split stdin into files airportids.tmp.{1..$1}
#
function splitairports
{
    n=0
    while read aptid
    do
        n=$((n+1))
        echo $aptid >> airportids.tmp.$n
        if [ $n == $1 ]
        then
            n=0
        fi
    done
}

#
#  Download all PDF files given in stdin and process them
#  Lines are pairs of:
#      <flag> <state> <faaid> <aptdiagpdfurl> <type>
#      <title>
#
function downloadall
{
    dir=datums/aptplates_$expdate
    mkdir -p $dir

    while read airport
    do
        read title
        downloadone $1 $airport "$title"
    done
}

#
#  Download all PDF files given by $1 file
#  Lines in airportids.tmp.$1 are <icaoid>,<faaid>,...
#
function getaptplates
{
    set +e
    mono --debug GetAptPlates.exe $airac < airportids.tmp.$1 | downloadall $1
    rm airportids.tmp.$1
}

#
#  Process just one airport whos icaoid is given in $1
#
function justdooneairport
{
    set -e
    grep "^$1," datums/airports_$aixd.csv | mono --debug GetAptPlates.exe $airac | downloadall 0
    dir=datums/aptplates_$expdate
    stdir=$dir/state
    mkdir -p $stdir
    tmpfile=`echo aptplates.tmp/*`
    state=${tmpfile#*.tmp/}
    state=${state%.*}
    sort -u $stdir/$state.csv $tmpfile > justdooneairport.tmp
    mv -f justdooneairport.tmp $stdir/$state.csv
    rm -rf aptplates.tmp
}

#
#  Take the contents of aptplates.tmp/<stateid>.*
#  and concat to dir/state/<stateid>.csv
#
function postprocessonestate
{
    dir=datums/aptplates_$expdate
    stdir=$dir/state
    mkdir -p $stdir
    tmpfile=$1                              # get a sample aptplates.tmp/<stateid>.<n> filename
    state=${tmpfile#*.tmp/}                 # get the <stateid> from that name
    state=${state%.*}
    tmpfile=${tmpfile%.*}                   # get the aptplates.tmp/<stateid> part
    sort -u $tmpfile.* > $stdir/$state.csv  # create final output file
    rm $tmpfile.*
}

#
#  Start of script...
#
set -e

if [ cureffdate -ot cureffdate.c ]
then
    cc -o cureffdate cureffdate.c
fi

if [ GetAptPlates.exe -ot GetAptPlates.cs ]
then
    mcs -debug -out:GetAptPlates.exe GetAptPlates.cs
fi

rm -rf airportids.tmp.* aptplates.tmp
mkdir aptplates.tmp
airac=`./cureffdate -28 airac`
aixd=`./cureffdate -28 -x yyyymmdd`
expdate=`./cureffdate -28 -x yyyymmdd`
if [ "$expdate" == "" ]
then
    exit
fi

if [ "$1" != "" ]
then
    justdooneairport $1
    exit
fi

splitairports 9 < datums/airports_$aixd.csv
getaptplates 1 &
getaptplates 2 &
getaptplates 3 &
getaptplates 4 &
getaptplates 5 &
getaptplates 6 &
getaptplates 7 &
getaptplates 8 &
getaptplates 9 &

# wait for the threads to finish

while [ "`echo airportids.tmp.*`" != 'airportids.tmp.*' ]
do
    sleep 10
done

# concat all the aptplates.tmp/*.* files

while [ "`echo aptplates.tmp/*`" != 'aptplates.tmp/*' ]
do
    postprocessonestate aptplates.tmp/*
done

rmdir aptplates.tmp

