#!/bin/bash -v
#
#  Download all airport PDF files and convert to GIF files
#
#  Output is datums/aptplates_$expdate/gif_150/...
#                   aptplates_$expdate/state/$state.csv
#
#  They are on 28 day cycle
#
#  Takes 3 hours to run
#
#  $1 = "" : do all plates of all airports
#  $1 = icaoid : do all plates of just the one airport
#

#
#  Split stdin into files airportids.tmp.{1..$1}
#
function splitairports
{
    n=0
    while read aptid
    do
        n=$((n+1))
        echo $aptid >> airportids.tmp.$n
        if [ $n == $1 ]
        then
            n=0
        fi
    done
}

# Rename the temp PNG files created by ghostscript to their permanent names
#  $1 = pngtemp, eg datums/aptplates_20150625/pngtemp/ne1/to.png
#  $2 = stream, eg 2
function movepngtempfiles
{
    for ((p=1;; p++))
    do
        if [ ! -f "$1.$2.p$p" ]
        then
            break
        fi
        mv -f $1.$2.p$p $1.p$p
    done
}

#  $1 = pngtemp, eg datums/aptplates_20150625/pngtemp/ne1/to.png
#  $2 = gifperm, eg datums/aptplates_20150625/gif_150/ne1/to.gif
#  $3 = stream, eg 2
function convertpngtemptogifperm
{
    for ((p=1;; p++))
    do
        if [ ! -f "$1.p$p" ]
        then
            break
        fi
        convert -alpha Remove $1.p$p $2.$3.gif
        mv -f $2.$3.gif $2.p$p
    done
}

#
#  Download one PDF file and convert to GIF
#    $1 = stream number
#    $2 = flag
#    $3 = state
#    $4 = faaid
#    $5 = pdfurl
#    $6 = type
#    $7 = title
#
function downloadone
{
    set +e

    stream=$1
    flag=$2
    state=$3
    faaid=$4
    pdfurl=$5
    type=$6
    title="$7"
    title=${title//\//}

    if [ "$flag" == "D" ]
    then
        return
    fi

    case $type in

        ## Skip these chart types
        ##  AHS-AFD HOT SPOT
        ##  HOT-HOT SPOT
        ##  LAH-LAHSO
        AHS|HOT|LAH) ;;

        ## Save these chart tyoes
        ##  APD- airport diagrams
        ##  [O]DP- departure procedures
        ##  IAP- instrument approaches
        ##  MIN- alternate/takeoff minimums
        ##  STAR- standard approach routes
        APD|DP|IAP|MIN|ODP|STAR)
            echo "$faaid $type $title"

            ## Maybe we already have the corresponding PDF file
            ## If not, fetch from the FAA website
            pdfbase=`basename $pdfurl`
            pdfbase=${pdfbase:0:3}/${pdfbase:3}
            pdftemp=$dir/pdftemp/$pdfbase
            if [ ! -f $pdftemp ]
            then
                mkdir -p `dirname $pdftemp`
                rm -f $pdftemp.$stream
                wget -nv $pdfurl -O $pdftemp.$stream
                if [ ! -s $pdftemp.$stream ]
                then
                    rm -f $pdftemp.$stream
                    wget -nv $pdfurl -O $pdftemp.$stream
                fi
                if [ -s $pdftemp.$stream ]
                then
                    mv -f $pdftemp.$stream $pdftemp
                fi
            fi

            ## Maybe we already have the corresponding PNG file(s)
            ## If not, convert from PDF file
            pngtemp=$dir/pngtemp/${pdfbase/.pdf/.png}
            if [ ! -f $pngtemp.p1 ]
            then
                mkdir -p `dirname $pngtemp`
                gs -q -dQuiet -dSAFER -dBATCH -dNOPAUSE -dNOPROMT -dMaxBitmap=500000000 -dAlignToPixels=0 -dGridFitTT=2 \
                    -sDEVICE=pngalpha -dTextAlphaBits=4 -dGraphicsAlphaBits=4 -r300x300 \
                    -sOutputFile=$pngtemp.$stream.p%d $pdftemp
                movepngtempfiles $pngtemp $stream
            fi

            ## Some plates are split up into separate pages already
            ## eg, "DP-BEVERLY EIGHT" and "DP-BEVERLY EIGHT, CONT.1"
            ## So we make the continuation pages part of the primary plate
            contpage=0
            titlenc=${title%, CONT.*}
            if [ "$titlenc" != "$title" ]
            then

                ## Continuation page, find primary page
                primary_gifbase=`grep $faaid,"\"$type-$titlenc\"", aptplates.tmp/$state.$stream`
                if [ "$primary_gifbase" != "" ]
                then
                    contpage=1

                    ## Found primary page GIF file name, eg, gif_150/050/39beverly_c.gif
                    primary_gifbase=${primary_gifbase##*,}

                    ## Convert the single PNG file to single GIF file
                    ## PNG file is named as given in URL
                    ## GIF file is named using the primary page GIF file name
                    contnum=${title##*, CONT.}
                    pagenum=$((contnum+1))

                    ## See if we already have corresponding continuation page GIF file
                    gifbasepn=$primary_gifbase.p$pagenum
                    gifpermpn=$dir/$gifbasepn
                    if [ ! -f $gifpermpn ]
                    then
                        convert -alpha Remove $pngtemp.p1 $gifpermpn.$stream.gif
                        mv -f $gifpermpn.$stream.gif $gifpermpn
                    fi
                fi
            fi

            if [ $contpage == 0 ]
            then

                ## Not a continuation page
                ## Maybe we already have the corresponding GIF file(s)
                ## If not, convert from the PNG file(s)
                gifbase=gif_150/${pdfbase/.pdf/.gif}
                gifperm=$dir/$gifbase
                if [ ! -f $gifperm.p1 ]
                then
                    mkdir -p `dirname $gifperm`
                    convertpngtemptogifperm $pngtemp $gifperm $stream
                fi

                ## Set up the .csv line: FAAID,TYPE-TITLE,GIFFILE
                if [ -f $gifperm.p1 ]
                then
                    echo $faaid,"\"$type-$title\"",$gifbase >> aptplates.tmp/$state.$stream
                fi
            fi
        ;;

        *) echo unknown plate type $type airport $faaid ;;
    esac
}

#
#  Download all PDF files given in stdin and process them
#  Lines are pairs of:
#      <flag> <state> <faaid> <aptdiagpdfurl> <type>
#      <title>
#
function downloadall
{
    dir=datums/aptplates_$expdate
    mkdir -p $dir

    while read airport
    do
        read title
        downloadone $1 $airport "$title"
    done
}

#
#  Download all PDF files given by $1 file
#  Lines in airportids.tmp.$1 are <icaoid>,<faaid>,...
#
function getaptplates
{
    set +e
    mono --debug GetAptPlates.exe $airac < airportids.tmp.$1 | downloadall $1
    rm airportids.tmp.$1
}

#
#  Process just one airport whos icaoid is given in $1
#
function justdooneairport
{
    set -e
    grep "^$1," datums/airports_$aixd.csv | mono --debug GetAptPlates.exe $airac | downloadall 0
    dir=datums/aptplates_$expdate
    stdir=$dir/state
    mkdir -p $stdir
    tmpfile=`echo aptplates.tmp/*`
    state=${tmpfile#*.tmp/}
    state=${state%.*}
    sort -u $stdir/$state.csv $tmpfile > justdooneairport.tmp
    mv -f justdooneairport.tmp $stdir/$state.csv
    rm -rf aptplates.tmp
}

#
#  Take the contents of aptplates.tmp/<stateid>.*
#  and concat to dir/state/<stateid>.csv
#
function postprocessonestate
{
    dir=datums/aptplates_$expdate
    stdir=$dir/state
    mkdir -p $stdir
    tmpfile=$1                              # get a sample aptplates.tmp/<stateid>.<n> filename
    state=${tmpfile#*.tmp/}                 # get the <stateid> from that name
    state=${state%.*}
    tmpfile=${tmpfile%.*}                   # get the aptplates.tmp/<stateid> part
    sort -u $tmpfile.* > $stdir/$state.csv  # create final output file
    rm $tmpfile.*
}

#
#  Start of script...
#
cd `dirname $0`
set -e

if [ cureffdate -ot cureffdate.c ]
then
    cc -o cureffdate cureffdate.c
fi

if [ GetAptPlates.exe -ot GetAptPlates.cs ]
then
    mcs -debug -out:GetAptPlates.exe GetAptPlates.cs
fi

rm -rf airportids.tmp.* aptplates.tmp
mkdir aptplates.tmp
airac=`./cureffdate airac`
aixd=`./cureffdate -x yyyymmdd`
expdate=`./cureffdate -28 -x yyyymmdd`
if [ "$expdate" == "" ]
then
    exit
fi

if [ "$1" != "" ]
then
    justdooneairport $1
    exit
fi

splitairports 9 < datums/airports_$aixd.csv
getaptplates 1 &
getaptplates 2 &
getaptplates 3 &
getaptplates 4 &
getaptplates 5 &
getaptplates 6 &
getaptplates 7 &
getaptplates 8 &
getaptplates 9 &

# wait for the threads to finish

while [ "`echo airportids.tmp.*`" != 'airportids.tmp.*' ]
do
    sleep 10
done

# concat all the aptplates.tmp/*.* files

while [ "`echo aptplates.tmp/*`" != 'aptplates.tmp/*' ]
do
    postprocessonestate aptplates.tmp/*
done

rmdir aptplates.tmp

