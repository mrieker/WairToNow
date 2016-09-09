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
        rm -f $iapoutdir/$stateid.csv.tmp
        rm -f $iapoutdir/$stateid.rej.tmp
        java DecodePlate -verbose \
            -csvout $iapoutdir/$stateid.csv.tmp \
            -cycles28 $cycles28 -cycles56 $cycles56 \
            -rejects $iapoutdir/$stateid.rej.tmp \
                < decodeallplates.$stateid.tmp \
                > $iapoutdir/$stateid.log
        if [ -f $iapoutdir/$stateid.csv.tmp ]
        then
            mv -f $iapoutdir/$stateid.csv.tmp $iapoutdir/$stateid.csv
        else
            rm -f $iapoutdir/$stateid.csv
        fi
        if [ -f $iapoutdir/$stateid.rej.tmp ]
        then
            mv -f $iapoutdir/$stateid.rej.tmp $iapoutdir/$stateid.rej
        else
            rm -f $iapoutdir/$stateid.rej
        fi
    done
}

date
unset DISPLAY
export CLASSPATH=DecodePlate.jar:pdfbox-1.8.10.jar:commons-logging-1.2.jar

#
# Make sure we have all the necessary jar files.
# Two apache libraries and the DecodePlate jar file itself.
#
if [ ! -f pdfbox-1.8.10.jar ]
then
    wget http://archive.apache.org/dist/pdfbox/1.8.10/pdfbox-1.8.10.jar
fi

if [ ! -f commons-logging-1.2.jar ]
then
    wget http://apache.mirrors.lucidnetworks.net//commons/logging/binaries/commons-logging-1.2-bin.tar.gz
    tar xzfO commons-logging-1.2-bin.tar.gz \
            commons-logging-1.2/commons-logging-1.2.jar > \
            commons-logging-1.2.jar
    rm commons-logging-1.2-bin.tar.gz
fi

if [ DecodePlate.jar -ot DecodePlate.java ]
then
    rm -f DecodePlate.jar
    javac -Xlint:deprecation DecodePlate.java Lib.java
    jar cf DecodePlate.jar DecodePlate*.class Lib*.class
    rm -f DecodePlate*.class Lib*.class
fi

if [ readoneline -ot readoneline.c ]
then
    cc -O2 -Wall -o readoneline readoneline.c
fi

if [ ReadArptDgmPng.exe -ot ReadArptDgmPng.cs ]
then
    mcs -debug -out:ReadArptDgmPng.exe -reference:System.Drawing.dll ReadArptDgmPng.cs
fi

if [ cureffdate -ot cureffdate.c ]
then
    cc -o cureffdate cureffdate.c
fi

#
# Create output directories
#
cycles28=`./cureffdate -28 -x yyyymmdd`
cycles56=`./cureffdate     -x yyyymmdd`
apdoutdir=datums/apdgeorefs_$cycles28
iapoutdir=datums/iapgeorefs_$cycles28
mkdir -p $apdoutdir $iapoutdir

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

