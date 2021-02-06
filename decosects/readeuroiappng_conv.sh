#!/bin/bash
#
# $1 = pdf name in ~/nfs/ without ~/nfs/ and without ending .pdf
#       eg, ED_AD_2_EDBC_4-2-1_en_2016-07-21
#
# fetch pdf example:
#   wget -O ~/nfs/LA_AD_2_LATI_24-23_en_2016-12-08.pdf \
#       https://www.ead.eurocontrol.int/eadbasic/pamslight-E51C7857F1A504E7D7D388E910FF3656/OQX5W2MELX7K4/EN/Charts/AD/AIRAC/LA_AD_2_LATI_24%20-%2023_en_2016-12-08.pdf
#
#  LA         = ticks we handle
#  UD Armenia = ticks in light gray GYUMRI/SHIRAK ILS/DME RWY 02
#  LO Austria = small numbering LOWG-NDB-35C LO_AD_2_LOWG_24-6-1_en_2020-08-13
#  ED Germany = ticks we handle
#
cd `dirname $0`
set -e -x
time gs -q -dQuiet -dSAFER -dBATCH -dNOPAUSE -dNOPROMT -dMaxBitmap=500000000 \
    -dAlignToPixels=0 -dGridFitTT=2 -sDEVICE=png16m -dTextAlphaBits=4 \
    -dGraphicsAlphaBits=4 -r300x300 \
    -sOutputFile=ReadEuroIAPPng_learnpngs/$1.png \
    ~/nfs/$1.pdf
