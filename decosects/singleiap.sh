#!/bin/bash
#
#  Generate georeference info for single airport diagram (APD)
#
#  $1 = ICAO ID
#
#  After running this script, reset iapreview.php 
#  to re-scan and validate these plates
#
#  Input:
#    datums/airports_$cycles28.csv
#
#  Output:
#    recreate datums/getaptplates_$airac/$faaid.dat
#      update datums/iapgeorefs_$cycles28/$stateid.csv
#      update datums/aptplates_$cycles28/state/$stateid.csv
#

cd `dirname $0`

. downloadone.si
. processdiagrams.si

function doinstrapproaches
{
    while read info
    do
        read title
        noapd=${info% IAP}
        if [ "$noapd" != "$info" ]
        then
            downloadone 0 $info "$title"
        fi
    done
}

set -e

export CLASSPATH=DecodePlate.jar:pdfbox-1.8.10.jar:commons-logging-1.2.jar

if [ DecodePlate.jar -ot DecodePlate.java ]
then
    rm -f DecodePlate.jar DecodePlate*.class Lib*.class
    javac -Xlint:deprecation DecodePlate.java Lib.java
    jar cf DecodePlate.jar DecodePlate*.class Lib*.class
    rm -f DecodePlate*.class Lib*.class
fi

cycles28=`./cureffdate -28 -x yyyymmdd`
airac=`./cureffdate -28 airac`
dir=datums/aptplates_$cycles28

stateid=`grep "^$1," datums/airports_$cycles28.csv`
stateid=${stateid%,*}
stateid=${stateid##*,}
echo STATEID $stateid

faaid=`grep "^$1," datums/airports_$cycles28.csv`
faaid=${faaid#$1,}
faaid=${faaid%%,*}
echo FAAID $faaid

#
# This is list of charts for the airport
# Maybe it is corrupt so get rid of it
# GetAptPlates.exe will re-construct it
#
rm -f datums/getaptplates_$airac/$faaid.dat

#
# Download PDFs and write single line for each to aptplates.tmp/<state>.0
# eg, aptplates.tmp/AZ.0: GYR,"APD-AIRPORT",gif_150/066/48ad.gif
#
rm -rf aptplates.tmp
mkdir aptplates.tmp
grep "^$1," datums/airports_$cycles28.csv | \
    mono --debug GetAptPlates.exe $airac | doinstrapproaches

#
# Merge that single line into datums/aptplates_$cycles28/state/<state>.csv
# Make sure we don't duplicate it
#
stfile=`ls aptplates.tmp`
if [ "$stfile" != "" ]
then
    stfile=aptplates.tmp/$stfile
fi
oldfile=$dir/state/$stateid.csv
if [ ! -f $oldfile ]
then
    oldfile=
fi
set -x
sort -u $oldfile $stfile > singleapd.tmp
mv singleapd.tmp $dir/state/$stateid.csv

#
# Get georef from approach plates
#
iapoutdir=datums/iapgeorefs_$cycles28
rm -f $iapoutdir/$stateid.csv.tmp
if [ -f $iapoutdir/$stateid.csv ]
then
    cp $iapoutdir/$stateid.csv $iapoutdir/$stateid.csv.tmp
fi
./filteriapplates.sh $dir/state/$stateid.csv | 
    grep "^$faaid," | \
    java DecodePlate -verbose \
        -csvout $iapoutdir/$stateid.csv.tmp \
        -cycles28 $cycles28 -cycles28 $cycles28 \
        -rejects $iapoutdir/$stateid.rej.tmp
set -x
sort -u $iapoutdir/$stateid.csv.tmp > singleapd.tmp
mv singleapd.tmp $iapoutdir/$stateid.csv

rm -rf aptplates.tmp $iapoutdir/$stateid.csv.tmp
