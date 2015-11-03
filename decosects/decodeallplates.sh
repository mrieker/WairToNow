#!/bin/bash

#
# Process airport diagrams (APD) from a state to get their georef info.
# Writes to $apdoutdir/$stateid.csv
#
function processdiagrams
{
    csvoutfile=$apdoutdir/$stateid.csv
    if [ ! -f $csvoutfile ]
    then
        touch $csvoutfile
    fi

    while read line  # eg, BVY,"APD-AIRPORT DIAGRAM",gif_150/050/39ad.gif
    do
        faaid=${line%%,*}
        gifname=${line##*,}
        pdfname=${gifname/gif_150/pdftemp}
        pdfname=datums/aptplates_$cycles28/${pdfname/.gif/.pdf}

        csvname=decodeallplates.apdcsv_$faaid.csv
        logname=decodeallplates.apdcsv_$faaid.log
        pngname=decodeallplates.apdcsv_$faaid.png

        icaoid=`grep "^[0-9A-Z]*,$faaid," datums/airports_$cycles56.csv`
        icaoid=${icaoid%%,*}

        if ! grep -q "^$icaoid," $csvoutfile
        then
            rm -f $csvname $logname $pngname

            echo "---------------- $faaid APD-AIRPORT DIAGRAM"

            gs -q -dQuiet -dSAFER -dBATCH -dNOPAUSE -dNOPROMT -dMaxBitmap=500000000 -dAlignToPixels=0 -dGridFitTT=2 \
                -sDEVICE=pngalpha -dTextAlphaBits=4 -dGraphicsAlphaBits=4 -r300x300 -dFirstPage=1 -dLastPage=1 \
                -sOutputFile=$pngname $pdfname

            if [ -s $pngname ]
            then
                mono --debug ReadArptDgmPng.exe $pngname -verbose -csvoutfile $csvname -csvoutid $icaoid > $logname 2>&1
            fi

            if [ -s $csvname ]
            then
                cat $csvname >> $csvoutfile
                rm -f $csvname $logname $pngname
            else
                echo "APD $faaid failed $pdfname"
            fi
        fi
    done
}

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
        grep ',"IAP-' $statefile | grep -v ',"IAP-TACAN' | grep -v ',"IAP-COPTER TACAN' \
            | grep -v ',"IAP-HI-' | grep -v '(RNP)' | grep -v ' VISUAL ' \
            | grep -v 'SA CAT I' | grep -v 'CAT II' \
                > decodeallplates.$stateid.tmp
        rm -f $iapoutdir/$stateid.csv.tmp
        rm -f $iapoutdir/$stateid.rej.tmp
        java DecodePlate -verbose \
            -csvout $iapoutdir/$stateid.csv.tmp \
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

cd `dirname $0`
date
unset DISPLAY
export CLASSPATH=DecodePlate.jar:pdfbox-1.8.10.jar:commons-logging-1.2.jar

#
# Make sure we have all the necessary jar files.
# Two apache libraries and the DecodePlate jar file itself.
#
if [ ! -f pdfbox-1.8.10.jar ]
then
    wget http://mirrors.ibiblio.org/apache//pdfbox/1.8.10/pdfbox-1.8.10.jar
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
    gmcs -debug -out:ReadArptDgmPng.exe -reference:System.Drawing.dll ReadArptDgmPng.cs
fi

#
# Create output directories
#
cycles28=`cat datums/aptplates_expdate.dat`
cycles56=`cat datums/aptinfo_expdate.dat`
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

