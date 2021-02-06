#!/bin/bash
#
# Create the per-state zip files
# Takes about 3 minutes to run
#

#
# Create file named datums/statezips_$cycles28/$state.zip
#
function processastate
{
    state=$1
    zipperm=$zipdir/$state.zip
    ziptemp=$zipdir/$state.tmp.zip

    if [ ! -f $zipperm ]
    then
        echo "- $state.zip"
        rm -f $ziptemp

        # add various csvs to zip file
        # they go in as compressed name.html
        # client is more efficient if these are at beginning of zip file
        rm -rf $zipdir/$state
        mkdir $zipdir/$state
        cd $zipdir/$state
        ln $datums/aptplates_$cycles28/state/$state.csv aptplates.csv
        ln $datums/apdgeorefs_$cycles28/$state.csv      apdgeorefs.csv
        ln $datums/iapgeorefs2_$cycles28/$state.csv     iapgeorefs2.csv
        ln $datums/iapcifps_$cycles28/$state.csv        iapcifps.csv
        zip -q $ziptemp aptplates.csv apdgeorefs.csv iapgeorefs2.csv iapcifps.csv

        # add airport info to zip file
        # they go in as compressed faaid.html
        grep ",$state,P[RU]," $datums/airports_$cycles28.csv | addaptinfo | zip -q -@ $ziptemp

        # add plates to zip file
        # they go in as uncompressed name.gif.pn
        cd $datums/aptplates_$cycles28/gif_150
        addplates < ../state/$state.csv | sort -u | zip -0 -q -@ $ziptemp

        # zip complete, make it permanent
        rm -rf $zipdir/$state
        mv $ziptemp $zipperm
    fi
}

#
# Read through list of html files needed for a state
# giving AFD-like info for an airport
#
function addaptinfo
{
    set -e
    while read csvline
    do
        faaid=${csvline#*,}
        faaid=${faaid%%,*}
        faa0=${faaid:0:1}
        faa1=${faaid:1}
        gunzip -c $datums/aptinfo_$cycles28/$faa0/$faa1.html.gz > $faaid.html
        echo $faaid.html
    done
}

#
# Read through list of gif files needed for a state
# for things like runway diagrams, approach procedures
#
function addplates
{
    set -e
    while read csvline
    do
        gifwild=${csvline##*\",}
        ls $gifwild*
    done
}

# starts here
cd `dirname $0`
mydir=`pwd`
datums=$mydir/datums

set -e
if [ "$1" != "" ]
then
    cycles28=$1
else
    cycles28=`./cureffdate -28 -x yyyymmdd`
fi
zipdir=$datums/statezips_$cycles28

if [ "$2" != "" ]
then
    statecsv=$2
    processastate ${statecsv%.csv}
else
    mkdir -p $zipdir
    cd $datums/aptplates_$cycles28/state
    ls *.csv | xargs -n 1 -P 8 -r $mydir/makestatezips.sh $cycles28
fi
