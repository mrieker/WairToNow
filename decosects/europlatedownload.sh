#!/bin/bash -e
#
#  Download European approach plates
#  Write state zip files
#
cd `dirname $0`

# process the output from EuroPlateList.java
# queues to xargs which converts the pdfs to gifs and tries to georeference then creates zip
# input to function is in form:
#  *2-letter country code (null for end-of-file)
#  +plate title
#  -pdf file name
#  -effective date
#  -icaoid  << after file has been written
#           (blank if unable to decode icaoid or unable to download odf)
# output is in form:
#  @country code
#  .plate title     (blank for end-of-country)
#  .pdf file name   (blank for end-of-country)
#  .effective date  (blank for end-of-country)
#  .icaoid          (blank for end-of-country)
function processplates
{
    ccode=
    while read line
    do

        # * means subsequent airports belong to that country
        if [ "${line:0:1}" == "*" ]
        then

            # queue entry to xargs to handle end of country
            if [ "$ccode" != "" ]
            then
                echo "@$ccode"
                echo "."
                echo "."
                echo "."
                echo "."
            fi

            # get new country code
            # we get a null string here as the last line in the file
            ccode="${line:1}"
            if [ "$ccode" == "" ]
            then
                echo "europlatedownload normal completion" 1>&2
                return
            fi

            # if already have zip file, skip all contents
            # otherwise wipe all country-specific temp files
            if [ -f datums/statezips_$expdate/EUR-$ccode.zip ]
            then
                ccode=
            else
                rm -rf epdtemp/$ccode.*
            fi
        fi

        # + means the beginning of a four-line block of data for a plate
        if [ "${line:0:1}" == "+" ]
        then

            # read the other 3 lines and extract info from each
            # if icaoid is blank, means java can't figure out the icaoid and so it didn't bother downloading the pdf
            read line2
            read line3
            if ! read line4
            then
                break
            fi
            pltitle="${line:1}"
            pdfname="${line2:1}"
            effdate="${line3:1}"
            icaoid="${line4:1}"

            # skip if we already have the EUR- .zip file
            if [ "$ccode" == "" ] ; then continue ; fi

            # skip these useless charts to reduce zip file size
            pltuc="${pltitle^^}"
            if [ "${pltuc/AERODROME OBSTACLE CHART/}"  != "$pltuc" ] ; then continue ; fi
            if [ "${pltuc/FAS DATA BLOCK/}"            != "$pltuc" ] ; then continue ; fi
            if [ "${pltuc/INS REFERENCE/}"             != "$pltuc" ] ; then continue ; fi
            if [ "${pltuc/LIST OF COORDINATES/}"       != "$pltuc" ] ; then continue ; fi
            if [ "${pltuc/LIST OF WAYPOINTS/}"         != "$pltuc" ] ; then continue ; fi
            if [ "${pltuc/STANDARD ARRIVAL ROUTES/}"   != "$pltuc" ] ; then continue ; fi
            if [ "${pltuc/STANDARD DEPARTURE ROUTES/}" != "$pltuc" ] ; then continue ; fi
            if [ "${pltuc/TABULAR DESCRIPTION/}"       != "$pltuc" ] ; then continue ; fi
            if [ "${pltuc/TAXI RESTRICTIONS/}"         != "$pltuc" ] ; then continue ; fi

            # queue the entry to xargs
            touch "epdtemp/queued.$ccode.$pdfname.$effdate.$icaoid"
            echo "@$ccode"
            echo ".$pltitle"
            echo ".$pdfname"
            echo ".$effdate"
            echo ".$icaoid"
        fi
    done
    echo "europlatedownload early termination" 1>&2
}

function runit
{
    if [ -f datums/europlatelist_$expdate.dat ]
    then
        processplates < datums/europlatelist_$expdate.dat
    else
        export CLASSPATH=EuroPlateList.jar
        java EuroPlateList datums/europlatepdfs_ $expdate | tee datums/europlatelist_$expdate.dat.tmp | processplates
        mv datums/europlatelist_$expdate.dat.tmp datums/europlatelist_$expdate.dat
    fi
}

expdate=`./cureffdate -28 -x yyyymmdd`

if [ "$1" == "" ]
then

    # run the whole thing

    rm -rf epdtemp*
    mkdir epdtemp
    runit | xargs -d '\n' -L 5 -P 12 ./europlatedownload.sh xargscallback
    rm -rf epdtemp*

    # create waypoint databases

    if [ ! -f datums/waypointsoa_$expdate.db.gz ]
    then
        echo "europlatedownload creating waypointsoa" 1>&2
        rm -f datums/waypointsoa_$expdate.db*
        mono --debug MakeWaypoints.exe $expdate datums/waypointsoa_$expdate.db 0 1
        gzip -c datums/waypointsoa_$expdate.db > datums/waypointsoa_$expdate.db.gz.tmp
        mv datums/waypointsoa_$expdate.db.gz.tmp datums/waypointsoa_$expdate.db.gz
    fi

    if [ ! -f datums/wayptabbsoa_$expdate.db.gz ]
    then
        echo "europlatedownload creating wayptabbsoa" 1>&2
        rm -f datums/wayptabbsoa_$expdate.db*
        mono --debug MakeWaypoints.exe $expdate datums/wayptabbsoa_$expdate.db 1 1
        gzip -c datums/wayptabbsoa_$expdate.db > datums/wayptabbsoa_$expdate.db.gz.tmp
        mv datums/wayptabbsoa_$expdate.db.gz.tmp datums/wayptabbsoa_$expdate.db.gz
    fi

    if [ ! -f datums/waypointsofm_$expdate.db.gz ]
    then
        echo "europlatedownload creating waypointsofm" 1>&2
        rm -f datums/waypointsofm_$expdate.db*
        mono --debug MakeWaypoints.exe $expdate datums/waypointsofm_$expdate.db 0 2
        gzip -c datums/waypointsofm_$expdate.db > datums/waypointsofm_$expdate.db.gz.tmp
        mv datums/waypointsofm_$expdate.db.gz.tmp datums/waypointsofm_$expdate.db.gz
    fi
fi

if [[ ( "$1" == "xargscallback" ) && ( "$2$3$4$5$6" != "" ) ]]
then

    # process one file
    #  $2 = @ccode
    #  $3 = .pltitle    just the . means end of country
    #  $4 = .pdfname
    #  $5 = .effdate
    #  $6 = .icaoid     just the . means it didn't download

    echo " <$2> <$3> <$4> <$5> <$6>" 1>&2

    ccode=$2
    if [ "${ccode:0:1}" != "@" ]
    then
        echo "out of sync" 1>&2
        exit 255
    fi

    ccode="${2:1}"
    pltitle="${3:1}"
    pdfname="${4:1}"
    effdate="${5:1}"
    icaoid="${6:1}"

    if [ "$pltitle" != "" ]
    then

        if [ "$icaoid" != "" ]
        then

            # there is a pdf file to process

            temp=epdtemp/$BASHPID

            rm -rf $temp.*

            # convert the pdf file to png (300 dpi)
            # png in common directory assuming plate with same effdate never changes
            pngname="${pdfname/[.]pdf/.png}"
            pdfpath="datums/europlatepdfs_$expdate/$ccode/$pdfname.$effdate"
            pngpath="datums/europlatepngs/$ccode/$pngname.$effdate"
            if [ ! -f "$pngpath.p1" ]
            then
                ln "$pdfpath" $temp.pdf
                gs -q -dQuiet -dSAFER -dBATCH -dNOPAUSE -dNOPROMT -dMaxBitmap=500000000 -dAlignToPixels=0 -dGridFitTT=2 \
                    -sDEVICE=png16m -dTextAlphaBits=4 -dGraphicsAlphaBits=4 -r300x300 -dFirstPage=1 -dLastPage=1 \
                    -sOutputFile=$temp.png $temp.pdf > $temp.err 2>&1
                mkdir -p datums/europlatepngs/$ccode
                if [ -s $temp.err ]
                then
                    echo "gs error $icaoid,\"$pltitle\",$effdate,$gifname" 1>&2
                    cat $temp.err 1>&2
                    touch "$pngpath.p1"
                else
                    mono --debug TrimEuroIAPPng.exe $temp.png $temp.png2
                    if [ -f $temp.png2 ]
                    then
                        mv -f $temp.png2 $temp.png
                    fi
                    mv $temp.png "$pngpath.p1"
                fi
            fi

            # convert the png to gif (150 dpi, much smaller file) and annotate with expiration date
            # put annotation on bottom so it doesn't mess up georeferencing
            gifname="${pdfname/[.]pdf/.gif}"
            gifpath="datums/europlategifs_$expdate/$ccode/$gifname"
            if [ ! -f "$gifpath.p1" ]
            then
                mkdir -p datums/europlategifs_$expdate/$ccode
                if [ -s "$pngpath.p1" ]
                then
                    convert -limit thread 1 "$pngpath.p1" -alpha Remove -resize 50% \
                        -gravity South -background White -splice 0x30 \
                        -font ../app/src/main/assets/DejaVuSansMono.ttf -pointsize 20 \
                        -annotate +0+4 "check for update by ${expdate:0:4}-${expdate:4:2}-${expdate:6:2}  ©EUROCONTROL" \
                        $temp.gif
                    mv $temp.gif "$gifpath.p1"
                else
                    touch "$gifpath.p1"
                fi
            fi

            # make entry in csv file so it will show up on airport waypoint page in app
            csvname="${pdfname/[.]pdf/.csv}"
            csvpath="datums/europlatecsvs_$expdate/$ccode/$csvname"
            if [ ! -f "$csvpath" ]
            then
                mkdir -p datums/europlatecsvs_$expdate/$ccode
                if [ -s "$gifpath.p1" ]
                then
                    echo "$icaoid,\"$pltitle\",$effdate,$gifname" > "$csvpath"
                else
                    touch "$csvpath"
                fi
            fi

            # scan the png for georeference marks
            # - common directory for actual computation
            # - separate directory for this expdate cycle
            geoname="${pdfname/[.]pdf/.geo}"
            gccpath="datums/europlategeos/$ccode/$geoname.$effdate"
            if [ ! -f "$gccpath" ]
            then
                mkdir -p datums/europlategeos/$ccode
                if mono --debug ReadEuroIAPPng.exe "$pngpath.p1" -scale 0.5 -csvoutfile $temp.geo -csvoutid "$icaoid,\"$pltitle\"" 2> /dev/null
                then
                    if [ -f $temp.geo ]
                    then
                        mv $temp.geo "$gccpath"
                    else
                        touch "$gccpath"
                    fi
                else
                    touch "$gccpath"
                fi
            fi
            geopath="datums/europlategeos_$expdate/$ccode/$geoname"
            if [ ! -f "$geopath" ]
            then
                mkdir -p datums/europlategeos_$expdate/$ccode
                ln "$gccpath" "$geopath"
            fi

            rm -rf $temp.*
        fi

        rm -f "epdtemp/queued.$ccode.$pdfname.$effdate.$icaoid"

    else

        # end-of-country processing

        while [ "`echo epdtemp/queued.$ccode.*`" != "epdtemp/queued.$ccode.*" ]
        do
            sleep 1
        done

        bdir=$PWD
        temp=$bdir/epdtemp/$ccode

        rm -rf $temp.dir $temp.zip
        mkdir $temp.dir

        touch $temp.dir/apdgeorefs.csv

        mkdir -p datums/europlatecsvs_$expdate/$ccode
        touch datums/europlatecsvs_$expdate/$ccode/@.csv
        sort -u datums/europlatecsvs_$expdate/$ccode/*.csv > $temp.dir/aptplates2.csv

        touch $temp.dir/iapcifps.csv

        mkdir -p datums/europlategeos_$expdate/$ccode
        touch datums/europlategeos_$expdate/$ccode/@.geo
        sort -u datums/europlategeos_$expdate/$ccode/*.geo > $temp.dir/iapgeorefs3.csv

        cd $temp.dir
        zip -q $temp.zip *
        cd $bdir

        if [ -d datums/europlategifs_$expdate/$ccode ]
        then
            cd datums/europlategifs_$expdate/$ccode
            zip -q -0 $temp.zip *
            cd $bdir
        fi

        mv $temp.zip datums/statezips_$expdate/EUR-$ccode.zip

        rm -rf $temp.*

        echo datums/statezips_$expdate/EUR-$ccode.zip complete 1>&2
    fi
fi
