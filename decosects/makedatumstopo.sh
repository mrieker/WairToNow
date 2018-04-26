#!/bin/bash
#
#  Build the datums/topo directory contents
#

function doit
{
    while read line
    do
        echo $line
        $line
    done
}

set -e
cd `dirname $0`

if [ maketopodatafiles -ot maketopodatafiles.c ]
then
    cc -g -Wall -O2 -o maketopodatafiles maketopodatafiles.c
fi

if [ SelectTopoZips.exe -ot SelectTopoZips.cs ]
then
    mcs -debug -out:SelectTopoZips.exe -reference:System.Drawing.dll SelectTopoZips.cs ChartTiff.cs
fi

if [ ! -f datums/topo/0.zip.save ]
then
    rm -f ETOPO1_Ice_g_int.xyz.gz*
    wget -nv http://www.ngdc.noaa.gov/mgg/global/relief/ETOPO1/data/ice_surface/grid_registered/xyz/ETOPO1_Ice_g_int.xyz.gz

    rm -rf datums/topo
    mkdir datums/topo
    zcat ETOPO1_Ice_g_int.xyz.gz | ./maketopodatafiles
    rm ETOPO1_Ice_g_int.xyz.gz

    cd datums/topo
    for ((i=-90;i<90;i++))
    do
        zip -r ./$i.zip ./$i
        mv ./$i.zip ./$i.zip.save
        rm -rf ./$i
    done
    cd ../..
fi

if [ ! -f datums/topo/0.zip ]
then
    rm -f datums/topo/*.zip
    mono --debug SelectTopoZips.exe | doit
fi

if [ ! -f datums/topo.zip ]
then
    cd datums
    zip -0 -q topo.zip topo/*.zip
fi

