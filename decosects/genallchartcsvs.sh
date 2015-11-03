#!/bin/bash -v
#
#  Create a .csv file for every chart.
#  It summarizes the .htm and .tfw data.
#  Takes a half hour to run.
#

set -e

function readtiffnames
{
    while read tiffname
    do
        spacename="${tiffname%%.tif}"
        undername="${spacename// /_}"
        if [ ! -f "$spacename.htm" ]
        then
            continue
        fi
        if [ ! -f "$spacename.tfw" ]
        then
            continue
        fi
        if [ -f "$undername.csv" ]
        then
            continue
        fi
        echo @@@@@@@@@@@@@@@@@@@@@ $spacename
        mono --debug ../GenChartsCSV.exe \
                $undername.csv           \
                "$spacename"             \
                "$spacename.htm"         \
                chartlist_all.htm        \
                "$spacename.tfw"
        if [ "${spacename:0:3}" == "ENR" ]
        then
            # enroute TFW data is bad so must be generated
            # takes about 5 mins for each one of these to run
            if mono --debug ../ReadEnrouteTiff.exe \
                    "$spacename"                \
                    -csvoutfile $undername.csv
            then
                echo successfully decoded tfw in $spacename
            else
                echo failed to decode tfw in $spacename
            fi
        fi
    done
    rm -f genallchartcsvs.tmp.$1
}

function splittiffnames
{
    n=0
    while read tiffname
    do
        n=$((n+1))
        echo $tiffname >> genallchartcsvs.tmp.$n
        if [ $n == $1 ]
        then
            n=0
        fi
    done
}

cd `dirname $0`

if [ GenChartsCSV.exe -ot GenChartsCSV.cs ]
then
    gmcs -debug -out:GenChartsCSV.exe GenChartsCSV.cs
fi

if [ ReadEnrouteTiff.exe -ot ReadEnrouteTiff.cs ]
then
    gmcs -debug -unsafe -out:ReadEnrouteTiff.exe -reference:System.Drawing.dll ReadEnrouteTiff.cs ChartTiff.cs
fi

cd charts

rm -f genallchartcsvs.tmp.*

ls *.tif | splittiffnames 9
ls -l genallchartcsvs.tmp.*

readtiffnames 1 < genallchartcsvs.tmp.1 &
readtiffnames 2 < genallchartcsvs.tmp.2 &
readtiffnames 3 < genallchartcsvs.tmp.3 &
readtiffnames 4 < genallchartcsvs.tmp.4 &
readtiffnames 5 < genallchartcsvs.tmp.5 &
readtiffnames 6 < genallchartcsvs.tmp.6 &
readtiffnames 7 < genallchartcsvs.tmp.7 &
readtiffnames 8 < genallchartcsvs.tmp.8 &
readtiffnames 9 < genallchartcsvs.tmp.9 &

sleep 10

while [ "`echo genallchartcsvs.tmp.*`" != 'genallchartcsvs.tmp.*' ]
do
    set +v
    sleep 10
done

