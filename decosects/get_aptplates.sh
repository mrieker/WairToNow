#!/bin/bash
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
set -e

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

# Start of script

if [ cureffdate -ot cureffdate.c ]
then
    cc -o cureffdate cureffdate.c
fi
if [ ProcessPlates.jar -ot ProcessPlates.java ]
then
    rm -f *.class
    javac ProcessPlates.java
    jar cf ProcessPlates.jar *.class
    rm -f *.class
fi

airac=`./cureffdate -28 airac`
aixd=`./cureffdate -28 -x yyyymmdd`
expdate=`./cureffdate -28 -x yyyymmdd`
if [ "$expdate" == "" ]
then
    exit
fi

# Download plate files from FAA and unzip them

mkdir -p datums/aptplates_$expdate/pdftemp
for fid in A B C D E
do
    if [ ! -f datums/DDTPP${fid}_$expdate.zip ]
    then
        wget -q -O datums/DDTPP${fid}_$expdate.zip.tmp https://aeronav.faa.gov/upload_313-d/terminal/DDTPP${fid}_20$airac.zip
        mv datums/DDTPP${fid}_$expdate.zip.tmp datums/DDTPP${fid}_$expdate.zip
    fi
    if [ $fid == E ]
    then
        unzip -n -q -d datums/aptplates_$expdate/pdftemp datums/DDTPP${fid}_$expdate.zip d-TPP_Metafile.xml
    else
        unzip -n -q -d datums/aptplates_$expdate/pdftemp datums/DDTPP${fid}_$expdate.zip
    fi
done

# Convert PDF files to GIFs
# and write datafiles

rm -rf aptplates.tmp
mkdir aptplates.tmp

nt=$1
if [ "$nt" == "" ]
then
    nt=12
fi

export CLASSPATH=ProcessPlates.jar
java ProcessPlates $nt datums/aptplates_$expdate/pdftemp/d-TPP_Metafile.xml

# concat all the aptplates.tmp/*.* files

while [ "`echo aptplates.tmp/*`" != 'aptplates.tmp/*' ]
do
    postprocessonestate aptplates.tmp/*
done

rmdir aptplates.tmp
