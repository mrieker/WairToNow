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

if [ "$1" == "renamepdf" ]
then
    lcpdf=$2
    ucpdf=${lcpdf/[.]pdf/.PDF}
    mv -v $lcpdf $ucpdf
    exit
fi

effdate=`./cureffdate -28 yyyymmdd`
expdate=`./cureffdate -28 -x yyyymmdd`
if [ "$expdate" == "" ]
then
    exit
fi

# Download plate files from FAA and unzip them

mkdir -p datums/aptplates_$expdate/pdftemp
for fid in A B C D
do
    if [ ! -f datums/DDTPP${fid}_$expdate.zip ]
    then
        wget -q -O datums/DDTPP${fid}_$expdate.zip.tmp https://aeronav.faa.gov/upload_313-d/terminal/DDTPP${fid}_${effdate:2}.zip
        mv datums/DDTPP${fid}_$expdate.zip.tmp datums/DDTPP${fid}_$expdate.zip
    fi
    unzip -n -q -d datums/aptplates_$expdate/pdftemp datums/DDTPP${fid}_$expdate.zip
    find datums/aptplates_$expdate/pdftemp -name \*.pdf -exec ./get_aptplates.sh renamepdf {} \;
done
if [ ! -f datums/DDTPPE_$expdate.xml.gz ]
then
    rm -f d-TPP_Metafile.xml datums/aptplates_$expdate/pdftemp/d-TPP_Metafile.xml datums/DDTPPE_$expdate.zip.tmp datums/DDTPPE_$expdate.zip
    wget -q -O datums/DDTPPE_$expdate.zip.tmp https://aeronav.faa.gov/upload_313-d/terminal/DDTPPE_${effdate:2}.zip
    mv datums/DDTPPE_$expdate.zip.tmp datums/DDTPPE_$expdate.zip
    unzip -n -q datums/DDTPPE_$expdate.zip d-TPP_Metafile.xml
    gzip d-TPP_Metafile.xml
    mv d-TPP_Metafile.xml.gz datums/DDTPPE_$expdate.xml.gz
    rm datums/DDTPPE_$expdate.zip
fi
if [ ! -f datums/aptplates_$expdate/pdftemp/d-TPP_Metafile.xml ]
then
    zcat datums/DDTPPE_$expdate.xml.gz > datums/aptplates_$expdate/pdftemp/d-TPP_Metafile.xml
fi

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
java ProcessPlates $nt datums/aptplates_$expdate/pdftemp/d-TPP_Metafile.xml datums/airports_$expdate.csv datums/aptplates_$expdate

# concat all the aptplates.tmp/*.* files

while [ "`echo aptplates.tmp/*`" != 'aptplates.tmp/*' ]
do
    postprocessonestate aptplates.tmp/*
done

rmdir aptplates.tmp
