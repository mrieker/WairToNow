#!/bin/bash
#
#  Update a live webpages directory from the workng webpages directory
#  Run this script after reviewing all diagram differences
#
#   $1 = directory pointed to by client MaintView.dldir URL
#   should be run sudo as the $1 directory owner
#   also creates $1/../decosects and $1/../webdata if not there already
#
set -e
oldpwd=$PWD
cd $1
outroot=$PWD
cd $oldpwd
owner=`stat -c '%U' $outroot`
if [ "$owner" != "`whoami`" ]
then
    sudo -u $owner $0 $outroot
    exit
fi
cd `dirname $0`

mkdir -p $outroot/../decosects
mkdir -p $outroot/../webdata/acrauploads
mkdir -p $outroot/../webdata/iaputil
mkdir -p $outroot/apdreview
mkdir -p $outroot/charts
mkdir -p $outroot/datums
mkdir -p $outroot/streets
mkdir -p $outroot/viewiap
ln -fs $PWD/../app/src/main/assets $outroot
ln -fs $PWD/datums/topo $outroot/datums
ln -fs $PWD/datums/topo.zip $outroot/datums
touch $outroot/streets/lock.file

# Copy chart directory and .csv softlink to output
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
            ln -s $PWD/$1$2         $outroot/$1$2
            ln -s $PWD/$1$2.csv     $outroot/$1$2.csv
            ln -s $PWD/$1$2.wtn.zip $outroot/$1$2.wtn.zip
        fi
    fi
}

# Get latest versions of chart directories
# Copy directory and .csv softlink to output
# Delete older ones from output
#  stdin = list of directories in charts directory
function processchartdirs
{
    lastname=
    lastvers=0
    while read chartdir
    do
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

# Copy latest datums file/directory softlink to output
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
        ln -s $PWD/$1$lastvers$2 $outroot/$1$lastvers$2
    fi
}

# Copy latest datums file/directory softlink to output
# Delete older ones from output
#  $1 = path before version number
#  $2 = suffix after version number
function processdatums
{
    ls -d $1*$2 | processdatum $1 $2
}

find charts -mindepth 1 -maxdepth 1 -type d | sort | processchartdirs

processdatums datums/airports_       .csv
processdatums datums/airways_        .csv
processdatums datums/apdgeorefs_
processdatums datums/aptinfo_
processdatums datums/aptplates_
processdatums datums/fixes_          .csv
processdatums datums/iapcifps_
processdatums datums/iapgeorefs2_
processdatums datums/intersections_  .csv
processdatums datums/localizers_     .csv
processdatums datums/navaids_        .csv
processdatums datums/runways_        .csv
processdatums datums/statezips_
processdatums datums/waypoints_      .db.gz

find . -mindepth 1 -maxdepth 1 -type f -exec cp -au {} $outroot/../decosects/ \;

cd ..
cp -au $PWD/webpages/*.csv  $outroot/
cp -au $PWD/webpages/*.html $outroot/
cp -au $PWD/webpages/*.php  $outroot/
cp -au $PWD/webpages/*.txt  $outroot/

