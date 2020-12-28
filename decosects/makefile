
BINS = \
	DecodePlate2.jar \
	readoneline \
	ReadArptDgmPng.exe \
	cureffdate \
	DiffArptDgmCsvs.exe \
	ParseCifp.jar \
	GenChartsCSV.exe \
	GetAirportIDs.exe \
	GetFixes.exe \
	WriteAirwaysCsv.exe \
	WriteLocalizersCsv.exe \
	WriteNavaidsCsv.exe \
	MakeWaypoints.exe \
	MakeObstructions.exe \
	ProcessPlates.jar \
	GetIFRChartNames.exe \
	maketopodatafiles \
	SelectTopoZips.exe \
	PurgeOldCharts.exe \
	ReadTiffFile.exe \
	VerifyOutline.exe \
	ParseChartList.jar \
	ReadSlippies.jar \
	IntlMetafsDaemon.exe \
	UpdateOaOfmMetafs.exe

LIBS = \
	jsoup-1.9.2.jar \
	pdfbox-1.8.10.jar \
	commons-logging-1.2.jar

default: $(BINS)

clean:
	rm -f $(BINS) $(LIBS)

DecodePlate2.jar: DecodePlate2.java Lib.java pdfbox-1.8.10.jar commons-logging-1.2.jar
	rm -f DecodePlate2.jar DecodePlate2*.class Lib*.class
	CLASSPATH=DecodePlate2.jar:pdfbox-1.8.10.jar:commons-logging-1.2.jar javac -Xlint:deprecation DecodePlate2.java Lib.java
	jar cf DecodePlate2.jar DecodePlate2*.class Lib*.class
	rm -f DecodePlate2*.class Lib*.class

readoneline: readoneline.c
	cc -O2 -Wall -o readoneline readoneline.c

ReadArptDgmPng.exe: ReadArptDgmPng.cs FindWaypoints.cs
	mcs -debug -out:ReadArptDgmPng.exe -reference:Mono.Data.Sqlite.dll -reference:System.Data.dll \
		-reference:System.Drawing.dll ReadArptDgmPng.cs FindWaypoints.cs

cureffdate: cureffdate.c
	cc -o cureffdate cureffdate.c

DiffArptDgmCsvs.exe: DiffArptDgmCsvs.cs
	mcs -debug -out:DiffArptDgmCsvs.exe DiffArptDgmCsvs.cs

ParseCifp.jar: ParseCifp.java Lib.java Waypts.java
	rm -f ParseCifp.jar *.class
	javac ParseCifp.java Lib.java Waypts.java
	jar cf ParseCifp.jar *.class
	rm -f *.class

GenChartsCSV.exe: GenChartsCSV.cs
	mcs -debug -out:GenChartsCSV.exe GenChartsCSV.cs

GetAirportIDs.exe: GetAirportIDs.cs GetTZForLL.cs
	mcs -debug -out:GetAirportIDs.exe GetAirportIDs.cs GetTZForLL.cs -reference:System.Data.dll -reference:Mono.Data.Sqlite.dll

GetFixes.exe: GetFixes.cs
	mcs -debug -out:GetFixes.exe GetFixes.cs

WriteAirwaysCsv.exe: WriteAirwaysCsv.cs
	mcs -debug -out:WriteAirwaysCsv.exe WriteAirwaysCsv.cs

WriteLocalizersCsv.exe: WriteLocalizersCsv.cs Topography.cs
	mcs -debug -out:WriteLocalizersCsv.exe WriteLocalizersCsv.cs Topography.cs

WriteNavaidsCsv.exe: WriteNavaidsCsv.cs Topography.cs
	mcs -debug -out:WriteNavaidsCsv.exe WriteNavaidsCsv.cs Topography.cs

MakeWaypoints.exe: MakeWaypoints.cs GeoContext.cs GetTZForLL.cs Topography.cs
	mcs -debug -out:MakeWaypoints.exe MakeWaypoints.cs GeoContext.cs GetTZForLL.cs Topography.cs -reference:System.Data.dll -reference:Mono.Data.Sqlite.dll

MakeObstructions.exe: MakeObstructions.cs
	mcs -debug -out:MakeObstructions.exe MakeObstructions.cs -reference:System.Data.dll -reference:Mono.Data.Sqlite.dll

ProcessPlates.jar: ProcessPlates.java Lib.java
	rm -f ProcessPlates.jar *.class
	javac ProcessPlates.java Lib.java
	jar cf ProcessPlates.jar *.class
	rm -f *.class

GetIFRChartNames.exe: GetIFRChartNames.cs
	mcs -debug -out:GetIFRChartNames.exe GetIFRChartNames.cs

maketopodatafiles: maketopodatafiles.c
	cc -g -Wall -O2 -o maketopodatafiles maketopodatafiles.c

SelectTopoZips.exe: SelectTopoZips.cs ChartTiff.cs
	mcs -debug -out:SelectTopoZips.exe -reference:System.Drawing.dll SelectTopoZips.cs ChartTiff.cs

PurgeOldCharts.exe: PurgeOldCharts.cs
	mcs -debug -out:PurgeOldCharts.exe PurgeOldCharts.cs

ReadTiffFile.exe: ReadTiffFile.cs ChartTiff.cs
	mcs -debug -out:ReadTiffFile.exe -reference:System.Drawing.dll ReadTiffFile.cs ChartTiff.cs

VerifyOutline.exe: VerifyOutline.cs ChartTiff.cs
	mcs -debug -out:VerifyOutline.exe -reference:System.Drawing.dll VerifyOutline.cs ChartTiff.cs

ParseChartList.jar: ParseChartList.java jsoup-1.9.2.jar
	rm -f ParseChartList.jar *.class
	CLASSPATH=.:jsoup-1.9.2.jar javac -Xlint:deprecation ParseChartList.java
	jar cf ParseChartList.jar *.class
	rm -f *.class

ReadSlippies.jar: ReadSlippies.java
	rm -f ReadSlippies.jar *.class
	javac -Xlint:deprecation ReadSlippies.java
	jar cf ReadSlippies.jar *.class
	rm -f *.class

IntlMetafsDaemon.exe: IntlMetafsDaemon.cs
	mcs -debug -out:IntlMetafsDaemon.exe IntlMetafsDaemon.cs -reference:System.Data.dll -reference:Mono.Data.Sqlite.dll

UpdateOaOfmMetafs.exe: UpdateOaOfmMetafs.cs
	mcs -debug -out:UpdateOaOfmMetafs.exe UpdateOaOfmMetafs.cs -reference:System.Data.dll -reference:Mono.Data.Sqlite.dll

jsoup-1.9.2.jar:
	wget -nv https://jsoup.org/packages/jsoup-1.9.2.jar

pdfbox-1.8.10.jar:
	wget -nv http://archive.apache.org/dist/pdfbox/1.8.10/pdfbox-1.8.10.jar

commons-logging-1.2.jar:
	wget -nv http://apache.mirrors.lucidnetworks.net//commons/logging/binaries/commons-logging-1.2-bin.tar.gz
	tar xzfO commons-logging-1.2-bin.tar.gz \
		commons-logging-1.2/commons-logging-1.2.jar > \
		commons-logging-1.2.jar
	rm commons-logging-1.2-bin.tar.gz

