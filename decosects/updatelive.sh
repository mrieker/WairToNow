#!/bin/bash
#
#  Update a live webpages directory from the workng webpages directory
#  Run this script after reviewing all diagram differences
#
#   $1 = directory pointed to by client MaintView.dldir URL
#   should be run sudo as the $1 directory owner
#   also creates $1/../decosects and $1/../webdata if not there already
#

# Copy chart .csv and .wtn.zip hardlink to output
# Delete older ones from output
#  $1 = chart directory without number, eg, charts/Denver_SEC_
#  $2 = version number, eg, 96
function processchartdir
{
    if [ "$1" != "" ]
    then
        if [ -s $1$2.csv ]
        then
            rm -f $outroot/$1*
            linkit $1$2.csv
            linkit $1$2.wtn.zip
        fi
    fi
}

# Get latest versions of chart directories
# Copy .wtn.zip and .csv hardlink to output
# Delete older ones from output
#  stdin = list of directories in charts directory
function processchartdirs
{
    lastname=
    lastvers=0
    while read wtnzippath
    do
        chartdir=${wtnzippath%.wtn.zip}
        thisname=${chartdir%_*}_
        thisvers=${chartdir##*_}
        if [ "$thisname" != "$lastname" ]
        then
            processchartdir "$lastname" $lastvers
            lastname=$thisname
            lastvers=0
        fi
        if [ $thisvers -gt $lastvers ]
        then
            lastvers=$thisvers
        fi
    done
    processchartdir "$lastname" $lastvers
}

# Copy latest datums file hardtlink to output
# Delete older ones from output
#  $1 = path before version number
#  $2 = suffix after version number
#  stdin = list of filenames matching $1*$2
function processdatum
{
    lastvers=0
    while read datumname
    do
        thisvers=${datumname#$1}
        thisvers=${thisvers%$2}
        if [ ${#thisvers} -eq 8 ]
        then
            if [ $thisvers -gt $lastvers ]
            then
                lastvers=$thisvers
            fi
        fi
    done
    if [ "$lastvers" != "0" ]
    then
        rm -f $outroot/$1*$2
        linkit $1$lastvers$2
    fi
}

# Copy latest datums file hardlink to output
# Delete older ones from output
#  $1 = path before version number
#  $2 = suffix after version number
function processdatums
{
    ls -d $1*$2 | processdatum $1 $2
}

# Copy latest datums/statezips files hardlink to output
# Delete older ones from output
#  $1 = path before version number
function processstatezips
{
    lastvers=0
    while read datumname
    do
        thisvers=${datumname#$1}
        if [ ${#thisvers} -eq 8 ]
        then
            if [ $thisvers -gt $lastvers ]
            then
                lastvers=$thisvers
            fi
        fi
    done
    if [ "$lastvers" != "0" ]
    then
        rm -rf $outroot/$1*
        mkdir -p $outroot/$1$lastvers
        ls $PWD/$1$lastvers | while read zipname
        do
            linkit $1$lastvers/$zipname
        done
    fi
}

# hardlink topo .zip.save file
function processtopozipsave
{
    while read zippath
    do
        linkit ${zippath/[.]zip/.zip.save}
    done
}

# try hardlink then softlink
# hardlinking might need /proc/sys/fs/protected_hardlink = 0
#  $1 = relative file to link (charts/... or datums/...)
function linkit
{
    if ! ln -f $1 $outroot/$1 2> /dev/null
    then
        ln -fs $PWD/$1 $outroot/$1
    fi
}

# script starts here
set -e
oldpwd=$PWD
cd `dirname $0`
scriptdir=$PWD
script=$PWD/`basename $0`
cd $oldpwd
cd $1
outroot=$PWD
owner=`stat -c '%U' $outroot`
if [ "$owner" != "`whoami`" ]
then
    ssh $owner@localhost $script $outroot
    exit
fi

cd $scriptdir
mkdir -p $outroot/../decosects
mkdir -p $outroot/../webdata/acrauploads
mkdir -p $outroot/../webdata/iaputil
mkdir -p $outroot/apdreview
mkdir -p $outroot/charts
mkdir -p $outroot/datums
mkdir -p $outroot/viewiap
ln -fs $PWD/../app/src/main/assets $outroot

linkit datums/topo.zip
rm -rf $outroot/datums/topo
mkdir -p $outroot/datums/topo
find datums/topo -mindepth 1 -maxdepth 1 -empty | processtopozipsave

find charts -mindepth 1 -maxdepth 1 -name \*.wtn.zip | sort | processchartdirs

processdatums datums/airports_       .csv
processdatums datums/airways_        .csv
processdatums datums/aptplates_
processdatums datums/fixes_          .csv
processdatums datums/intersections_  .csv
processdatums datums/localizers_     .csv
processdatums datums/navaids_        .csv
processdatums datums/runways_        .csv
processdatums datums/obstructions_   .db.gz
processdatums datums/waypoints_      .db.gz
processdatums datums/waypointsoa_    .db.gz
processdatums datums/waypointsofm_   .db.gz
processdatums datums/wayptabbs_      .db.gz
processdatums datums/wayptabbsoa_    .db.gz

ls -d datums/statezips_* | processstatezips datums/statezips_

find . -mindepth 1 -maxdepth 1 -type f -exec cp -au {} $outroot/../decosects/ \;

cd ..
cp -au $PWD/webpages/*.csv  $outroot/
cp -au $PWD/webpages/*.html $outroot/
cp -au $PWD/webpages/*.php  $outroot/
cp -au $PWD/webpages/*.txt  $outroot/

