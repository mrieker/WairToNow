#!/bin/bash
#
# Create per-chart wtn zip files
# Takes about 7 minutes to do all charts
#

function processchart
{
    cd charts
    csvpath=$1
    chart=`basename $csvpath .csv`
    if [ ! -f $chart.wtn.zip ]
    then
        echo $chart
        rm -f $chart.tmp.zip
        zip -0 -q $chart.tmp.zip $chart.csv
        cd $chart
        zip -0 -D -q -r ../$chart.tmp.zip *
        cd ..
        mv -f $chart.tmp.zip $chart.wtn.zip
    fi
}

cd `dirname $0`

if [ "$1" != "" ]
then
    processchart $1
else
    find charts -maxdepth 1 -name \*.csv -print0 | xargs -0 -n 1 -P 8 -r ./makewtnzipfiles.sh
fi
