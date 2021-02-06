#!/bin/bash
#
#  Generate georeference info for airport diagram (APD)
#  and instrument approach procedure (IAP) plates
#
#  Takes about 2hrs to run
#
cd `dirname $0`

. processdiagrams.si

#
# Process all the charts in various states
# one state at a time starting with the big
# states.
#
function processstate
{
    while true
    do
        statefile=`./readoneline`
        if [ "$statefile" == "" ]
        then
            rm $1
            return
        fi
        stateid=${statefile##*/}
        stateid=${stateid%.csv}

        # process all airport diagrams in the state
        grep 'APD-AIRPORT DIAGRAM' $statefile | processdiagrams

        # process all approach plates in the state
        ./filteriapplates.sh $statefile > decodeallplates.$stateid.tmp

        # process FAA-provided IAP georeferencing data
        rm -f $iap2outdir/$stateid.csv.tmp
        rm -f $iap2outdir/$stateid.rej.tmp
        if [ ! -f $iap2outdir/$stateid.csv ]
        then
            java DecodePlate2 \
                -csvout $iap2outdir/$stateid.csv.tmp \
                -cycles28 $cycles28 \
                -rejects $iap2outdir/$stateid.rej.tmp \
                    < decodeallplates.$stateid.tmp \
                    > $iap2outdir/$stateid.log
            if [ -f $iap2outdir/$stateid.csv.tmp ]
            then
                mv -f $iap2outdir/$stateid.csv.tmp $iap2outdir/$stateid.csv
            else
                rm -f $iap2outdir/$stateid.csv
            fi
            if [ -f $iap2outdir/$stateid.rej.tmp ]
            then
                mv -f $iap2outdir/$stateid.rej.tmp $iap2outdir/$stateid.rej
            else
                rm -f $iap2outdir/$stateid.rej
            fi
        fi
    done
}

set -e
date
unset DISPLAY
export CLASSPATH=DecodePlate2.jar:pdfbox-1.8.10.jar:commons-logging-1.2.jar

#
# Create output directories
#
if [ "$1" == "" ]
then
    cycles28=`./cureffdate -28 -x yyyymmdd`
else
    cycles28=$1
fi
apdoutdir=datums/apdgeorefs_$cycles28
iap2outdir=datums/iapgeorefs2_$cycles28
mkdir -p $apdoutdir $iap2outdir

#
# Start threads to process plates.
#
rm -f decodeallplates.apdcsv_* decodeallplates.tmp* decodeallplates.*.tmp
mknod decodeallplates.tmp p
n=0
while [ $n != 7 ]
do
    n=$((n+1))
    touch decodeallplates.tmp.$n
    processstate decodeallplates.tmp.$n < decodeallplates.tmp &
done

#
# Output names of states to the pipe so the threads have something to do.
# Start processing the big files first.
#
ls -S datums/aptplates_$cycles28/state/*.csv > decodeallplates.tmp

#
# Wait for all those threads to complete.
#
while [ "`echo decodeallplates.tmp.*`" != "decodeallplates.tmp.*" ]
do
    sleep 10
done

rm -f decodeallplates.tmp decodeallplates.*.tmp

date

