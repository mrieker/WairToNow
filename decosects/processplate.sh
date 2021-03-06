#!/bin/bash
#
#  $1 = stream id
#  $2 = state code
#  $3 = faaid
#  $4 = icaoid
#  $5 = chart code
#  $6 = chart name
#  $7 = pdf name
#  $8 = output dir
#
##echo = = = = = = = = = =
##set -x
streamid=$1                             ## 2
statecode=$2                            ## MA
faaid=$3                                ## BVY
icaoid=$4                               ## KBVY
chartcode=$5                            ## ALT
chartname=$6                            ## ALTERNATE MINIMUMS
pdfbase=$7                              ## 00026POUNC.PDF
dirout=$8                               ## datums/aptplates_20190103
pdfsplit=${pdfbase:0:3}/${pdfbase:3}    ## 000/26POUNC.PDF

##echo "streamid=[$streamid] statecode=[$statecode] faaid=[$faaid] icaoid=[$icaoid] chartcode=[$chartcode] chartname=[$chartname] pdfbase=[$pdfbase]"

if [ "$pdfbase" == "DELETED_JOB.PDF" ]
then
    exit 0
fi

# Rename the temp PNG files created by ghostscript to their permanent names
#  $1 = pngtemp, eg datums/aptplates_20150625/pngtemp/ne1/to.png
#  $2 = streamid, eg 2
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
#  $3 = streamid, eg 2
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

# Start of script

set -e
if [ "$dirout" == "" ]
then
    echo missing dirout
    exit 1
fi

pdfdir=$dirout/pdftemp              ## datums/aptplates_20190103/pdftemp
pngdir=$dirout/pngtemp              ## datums/aptplates_20190103/pngtemp
gifdir=$dirout/gif_150              ## datums/aptplates_20190103/gif_150

case $chartcode in

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

        pdftemp=$pdfdir/$pdfbase

        pngtemp=$pngdir/${pdfsplit/[.]PDF/.png}

        ## Maybe we already have the corresponding PNG file(s)
        ## If not, convert from PDF file
        if [ ! -f $pngtemp.p1 ]
        then
            mkdir -p `dirname $pngtemp`
            gs -q -dQuiet -dSAFER -dBATCH -dNOPAUSE -dNOPROMT -dMaxBitmap=500000000 -dAlignToPixels=0 -dGridFitTT=2 \
                -sDEVICE=pngalpha -dTextAlphaBits=4 -dGraphicsAlphaBits=4 -r300x300 \
                -sOutputFile=$pngtemp.$streamid.p%d $pdftemp
            movepngtempfiles $pngtemp $streamid
        fi

        ## Some plates are split up into separate pages already
        ## eg, "DP-BEVERLY EIGHT" and "DP-BEVERLY EIGHT, CONT.1"
        ## So we make the continuation pages part of the primary plate
        contpage=0
        titlenc=${chartname%, CONT.*}
        if [ "$titlenc" != "$chartname" ]
        then

            ## Continuation page, find primary page
            primary_gifbase=`grep $faaid,"\"$chartcode-$titlenc\"", aptplates.tmp/$statecode.$streamid`
            if [ "$primary_gifbase" != "" ]
            then
                contpage=1

                ## Found primary page GIF file name, eg, 050/39beverly_c.gif
                primary_gifbase=${primary_gifbase##*,}

                ## Convert the single PNG file to single GIF file
                ## PNG file is named as given in URL
                ## GIF file is named using the primary page GIF file name
                contnum=${chartname##*, CONT.}
                pagenum=$((contnum+1))

                ## See if we already have corresponding continuation page GIF file
                gifpermpn=$gifdir/$primary_gifbase.p$pagenum
                if [ ! -f $gifpermpn ]
                then
                    convert -alpha Remove $pngtemp.p1 $gifpermpn.$streamid.gif
                    mv -f $gifpermpn.$streamid.gif $gifpermpn
                fi
            fi
        fi

        if [ $contpage == 0 ]
        then

            ## Not a continuation page
            ## Maybe we already have the corresponding GIF file(s)
            ## If not, convert from the PNG file(s)
            gifname=${pdfsplit/[.]PDF/.gif}
            gifperm=$dirout/gif_150/$gifname
            if [ ! -f $gifperm.p1 ]
            then
                mkdir -p `dirname $gifperm`
                convertpngtemptogifperm $pngtemp $gifperm $streamid
            fi

            ## Set up the .csv line: FAAID,TYPE-TITLE,GIFFILE
            if [ -f $gifperm.p1 ]
            then
                echo $faaid,"\"$chartcode-$chartname\"",$gifname >> aptplates.tmp/$statecode.$streamid
            fi
        fi
    ;;

    *) echo unknown plate type $chartcode airport $faaid ;;
esac
