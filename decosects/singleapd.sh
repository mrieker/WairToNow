#!/bin/bash
#
#  Generate georeference info for single airport diagram (APD)
#
#  $1 = ICAO ID
#

cd `dirname $0`

. downloadone.si
. processdiagrams.si

function doairportdiagram
{
    while read info
    do
        read title
        noapd=${info% APD}
        if [ "$noapd" != "$info" ]
        then
            downloadone 0 $info $title
        fi
    done
}

set -e

if [ GetAptPlates.exe -ot GetAptPlates.cs ]
then
    mcs -debug -out:GetAptPlates.exe GetAptPlates.cs
fi

if [ ReadArptDgmPng.exe -ot ReadArptDgmPng.cs ]
then
    mcs -debug -out:ReadArptDgmPng.exe -reference:System.Drawing.dll ReadArptDgmPng.cs
fi

if [ cureffdate -ot cureffdate.c ]
then
    cc -o cureffdate cureffdate.c
fi

cycles28=`./cureffdate -28 -x yyyymmdd`
airac=`./cureffdate -28 airac`
dir=datums/aptplates_$cycles28

#
# Download PDF and write single line to aptplates.tmp/<state>.0
# eg, aptplates.tmp/AZ.0: GYR,"APD-AIRPORT",gif_150/066/48ad.gif
#
rm -rf aptplates.tmp
mkdir aptplates.tmp
grep "^$1," datums/airports_$cycles28.csv | \
    mono --debug GetAptPlates.exe $airac | doairportdiagram

#
# Merge that single line into datums/aptplates_$cycles28/state/<state>.csv
# Make sure we don't duplicate it
#
stfile=`ls aptplates.tmp`
stateid=${stfile%.0}
sort -u $dir/state/$stateid.csv aptplates.tmp/$stfile > singleapd.tmp
mv singleapd.tmp $dir/state/$stateid.csv

#
# Get georef from diagram
#
apdoutdir=datums/apdgeorefs_$cycles28
processdiagrams '-stages' < aptplates.tmp/$stfile
rm -rf aptplates.tmp

#
# Create patched state zip file
#
rm -f datums/statezips_$cycles28/$stateid.zip
./makestatezips.sh $stateid
