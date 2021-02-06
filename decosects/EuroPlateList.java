//    Copyright (C) 2021, Mike Rieker, Beverly, MA USA
//    www.outerworldapps.com
//
//    This program is free software; you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation; version 2 of the License.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU General Public License for more details.
//
//    EXPECT it to FAIL when someone's HeALTh or PROpeRTy is at RISk.
//
//    You should have received a copy of the GNU General Public License
//    along with this program; if not, write to the Free Software
//    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
//    http://www.gnu.org/licenses/gpl-2.0.html

// list and download european plates
// takes about 3 minutes

// javac EuroPlateList.java
// java EuroPlateList datums/europlatepdfs_ 20210128 > datums/europlatelist_20210128.dat

// output lines:
//  *two-letter country code                            \ each country
//  +plate title                        \ each file     |
//  -plate pdf base file name           |               |
//  -plate effective date yyyy-mm-dd    |               |
//  -icaoid                             /               /
//   ^null if pdf base file name bad (file not downloaded)
//  *   - end of it all

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class EuroPlateList {
    public static HashMap<String,String> cookies = new HashMap<> ();
    public static String pdfdirbase;
    public static String pdfexpdate;

    public static void main (String[] args)
            throws Exception
    {
        pdfdirbase = args[0];
        pdfexpdate = args[1];

        // log in, sets up JSESSIONID cookie
        URL loginurl = new URL ("https://www.ead.eurocontrol.int/fwf-eadbasic/ajaxlogin/ajax_security_check.jspx");
        BufferedReader lgirdr = new BufferedReader (new FileReader ("../webdata/euroctllogin.dat"));
        String username  = lgirdr.readLine ();  // basic free account login
        String password  = lgirdr.readLine ();
        lgirdr.close ();
        String loginjson = doHttpRequestString (loginurl, ("j_username=" + username + "&j_password=" + password).getBytes ());
        if (! loginjson.replace (" ", "").toLowerCase ().contains ("\"loggedin\":true")) {
            throw new Exception ("login failed " + loginjson);
        }

        // read AIP Library page
        URL aipliburl = new URL (loginurl, "/fwf-eadbasic/restricted/user/aip/aip_overview.faces");
        String aiplibhtml = doHttpRequestString (aipliburl, null);
        dumpCookies ();

        // get javax.faces.ViewState value
        String javaxfacesviewstate = getJavaxFacesViewState (aiplibhtml);
        //System.err.println ("javaxfacesviewstate:" + javaxfacesviewstate);

        // loop through all countries
        Map<String,String> countries = getCountryList (aiplibhtml);
        for (String key : countries.keySet ()) {
            //System.err.println ("main*: country " + key + "=" + countries.get (key));
            System.out.println ("*" + key);

            // get country's page
            String countryreq =
                     "javax.faces.partial.ajax=true" +
                    "&javax.faces.source=mainForm:querySearch" +
                    "&javax.faces.partial.execute=mainForm" +
                    "&javax.faces.partial.render=mainForm:searchResults+mainForm" +
                    "&mainForm:querySearch=mainForm:querySearch" +
                    "&mainForm=mainForm" +
                    "&mainForm:selectAuthorityCode_focus=" +
                    "&mainForm:selectAuthorityCode_input=" + key +
                    "&mainForm:selectAuthorityType_focus=" +
                    "&mainForm:selectAuthorityType_input=C" +
                    "&mainForm:selectLanguage_focus=" +
                    "&mainForm:selectLanguage_input=EN" +
                    "&mainForm:selectAipType_focus=" +
                    "&mainForm:selectAipType_input=Charts" +
                    "&mainForm:selectAipPart_focus=" +
                    "&mainForm:selectAipPart_input=AD" +
                    "&javax.faces.ViewState=" + javaxfacesviewstate;

            int linksperpage = 15;

            for (int firstlink = linksperpage;; firstlink += linksperpage) {

                // read page and download plates listed thereon
                String countryhtml = doHttpRequestString (aipliburl, countryreq.getBytes ());
                if (! parseCountryHtml (aipliburl, key, countryhtml)) break;

                //System.err.println ("main*: firstlink=" + firstlink);

                // get country's 2nd page
                countryreq =
                         "javax.faces.partial.ajax=true" +
                        "&javax.faces.source=mainForm:searchResults" +
                        "&javax.faces.partial.execute=mainForm:searchResults" +
                        "&javax.faces.partial.render=mainForm:searchResults" +
                        "&mainForm:searchResults=mainForm:searchResults" +
                        "&mainForm:searchResults_pagination=true" +
                        "&mainForm:searchResults_first=" + firstlink +
                        "&mainForm:searchResults_rows=" + linksperpage +
                        "&mainForm:searchResults_encodeFeature=true" +
                        "&mainForm=mainForm" +
                        "&mainForm:selectAuthorityCode_focus=" +
                        "&mainForm:selectAuthorityCode_input=" + key +
                        "&mainForm:selectAuthorityType_focus=" +
                        "&mainForm:selectAuthorityType_input=C" +
                        "&mainForm:selectLanguage_focus=" +
                        "&mainForm:selectLanguage_input=EN" +
                        "&mainForm:selectAipType_focus=" +
                        "&mainForm:selectAipType_input=Charts" +
                        "&mainForm:selectAipPart_focus=" +
                        "&mainForm:selectAipPart_input=AD" +
                        "&mainForm:searchResults_selection=" +
                        "&javax.faces.ViewState=" + javaxfacesviewstate;
            }
        }
        System.out.println ("*");
    }

    public static void dumpCookies ()
    {
        for (String key : cookies.keySet ()) {
            //System.err.println ("cookie:" + key + "=" + cookies.get (key));
        }
    }

    // extract javax.faces.ViewState value from AIP Library html
    // it is passed to the country page requests
    public static String getJavaxFacesViewState (String aiplibhtml)
    {
        int i = aiplibhtml.indexOf ("<form id=\"mainForm\"");
        int j = aiplibhtml.indexOf ("name=\"javax.faces.ViewState\"", i);
        int k = aiplibhtml.indexOf ("value=\"", j);
        int m = aiplibhtml.indexOf ('"', k + 7);
        return aiplibhtml.substring (k + 7, m);
    }

    // extract country list from AIP Library html
    //  <select id="mainForm:selectAuthorityCode_input" ...>
    //    <option value="LA">
    //      Albania (LA)
    //    </option>
    //    <option value="UD">
    //      Armenia (UD)
    //    </option>
    //    <option value="LO">
    //      Austria (LO)
    //    </option>
    //    <option value="UB">
    //      Azerbaijan (UB)
    //    </option>
    //    <option value="UM">
    //      Belarus (UM)
    //    </option>
    //    ...
    //  </select>
    public static Map<String,String> getCountryList (String aiplibhtml)
    {
        Map<String,String> map = new TreeMap<> ();
        int i = aiplibhtml.indexOf ("<select id=\"mainForm:selectAuthorityCode_input\"");
        int j = aiplibhtml.indexOf ("</select", i);
        while (true) {
            int k0 = aiplibhtml.indexOf ("<option value=\"", i);
            if (k0 < 0) break;
            if (k0 > j) break;
            k0 += 15;
            int k1 = aiplibhtml.indexOf ("\">", k0);
            String key = aiplibhtml.substring (k0, k1);
            int k2 = aiplibhtml.indexOf ("(" + key + ")", k1);
            String val = aiplibhtml.substring (k1 + 2, k2).trim ();
            map.put (key, val);
            i = k2;
        }
        return map;
    }

    // parse the plate links from the country page
    // the table rows begin with '<tr data-ri=' and end with '</tr>'
    // the contents of the rows is proper xml so use xml parser
    //   col 0: effective date yyyy-mm-dd
    //   col 1: <A HREF="downloadlink">pdf name</A>
    //   col 4: plate title
    //  <tr data-ri="0" data-rk="8937298d-14c1-4b0f-99c2-c9784285c646" class="ui-widget-content ui-datatable-even ui-datatable-selectable" role="row" aria-selected="false">
    //  <td role="gridcell" class="uibs-ais-column-s">2016-12-08</td>
    //  <td role="gridcell" class="uibs-ais-column-m">
    //    <a href="/fwf-eadbasic/aip/redirect?link=http%3A%2F%2Fwww.ead.eurocontrol.int%2Feadbasic%2Fpamslight-DBB489C32E1A1CEC41F917A6232CE471%2FOQX5W2MELX7K4%2FEN%2FCharts%2FAD%2FAIRAC%2FLA_AD_2_LATI_24+-+11_en_2016-12-08.pdf&amp;authorityCode=LA" target="_blank" class="wrap-data">LA_AD_2_LATI_24 - 11_en.pdf</a>
    //    <div id="mainForm:searchResults:0:j_idt135" class="ui-tooltip ui-widget ui-widget-content ui-shadow ui-corner-all">IB-903: File not found.</div>
    //    <script id="mainForm:searchResults:0:j_idt135_s" type="text/javascript">$(function(){PrimeFaces.cw("Tooltip","widget_mainForm_searchResults_0_j_idt135",{id:"mainForm:searchResults:0:j_idt135",widgetVar:"widget_mainForm_searchResults_0_j_idt135",showEffect:"fade",hideEffect:"fade",target:"mainForm:searchResults:0:missingDocument"});});</script>
    //  </td>
    //  <td role="gridcell" class="uibs-ais-column-s"><a href="/fwf-eadbasic/aip/redirect?link=&amp;authorityCode=LA" target="_blank"></a></td>
    //  <td role="gridcell" class="uibs-ais-column-s">AIRAC</td>
    //  <td role="gridcell" class="uibs-ais-column-l">AD 2 LATI STANDARD DEPARTURE CHART - INSTRUMENT (SID) - ICAO RWY 17</td>
    //  <td role="gridcell" class="uibs-ais-column-xs"><div class="ui-row-toggler ui-icon ui-icon-circle-triangle-e"></div></td>
    //  </tr>
    // downloads files
    // writes parsed results to stdout
    // returns true: there may be more pages
    //        false: there are no more pages
    public static boolean parseCountryHtml (URL aipliburl, String ccode, String html)
            throws Exception
    {
        // the pdf files seem to be named <ccode>_AD_2_<icaoid>blabla.pdf
        // the icaoid doesn't seem to be anywhere else consistently
        String pdfnamepfx = ccode + "_AD_2_";
        int pdfnamepfxlen = pdfnamepfx.length ();

        // loop through all table rows marked with '<tr data-ri=' in the page html
        boolean more = false;
        int j;
        for (int i = 0; (i = html.indexOf ("<tr data-ri=", i)) >= 0; i = j) {
            j = html.indexOf ("</tr>", i);
            String tablerow = html.substring (i, j + 5);

            // get these items by parsing the row as XML
            String effdate  = null;
            String hreflink = null;
            String pdfname  = null;
            String pltitle  = null;
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance ();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder ();
            Document doc = dBuilder.parse (new InputSource (new StringReader (tablerow)));
            doc.getDocumentElement ().normalize ();
            //printXmlNode (doc, "");

            NodeList trlist = doc.getChildNodes ();
            Node trnode = trlist.item (0);
            NodeList tdlist = trnode.getChildNodes ();
            int nchilds = tdlist.getLength ();
            int colno = 0;
            for (int k = 0; k < nchilds; k ++) {
                Node tdnode = tdlist.item (k);
                if (tdnode.getNodeName ().equals ("td")) {
                    switch (colno ++) {
                        case 0: {
                            effdate = tdnode.getTextContent ().trim ();
                            break;
                        }
                        case 1: {
                            NodeList alist = tdnode.getChildNodes ();
                            int nachilds = alist.getLength ();
                            for (int m = 0; m < nachilds; m ++) {
                                Node achild = alist.item (m);
                                if (achild.getNodeName ().equals ("a")) {
                                    NamedNodeMap aattrs = achild.getAttributes ();
                                    Node ahrefattr = aattrs.getNamedItem ("href");
                                    hreflink = ahrefattr.getTextContent ();
                                    pdfname  = achild.getTextContent ().trim ();
                                }
                            }
                            break;
                        }
                        case 4: {
                            pltitle = tdnode.getTextContent ().trim ();
                            break;
                        }
                    }
                }
            }

            // if we got all the items, write output lines and make sure file is downloaded
            if ((pltitle != null) && (pdfname != null) && (effdate != null) && (hreflink != null)) {
                System.out.println ("+" + pltitle);
                System.out.println ("-" + pdfname);
                System.out.println ("-" + effdate);

                // try to extract airport icaoid from the pdf file name
                String icaoid = null;
                if (pdfname.endsWith (".pdf") && (pdfname.length () > pdfnamepfxlen + 8)) {
                    icaoid = pdfname.substring (pdfnamepfxlen, pdfnamepfxlen + 4);
                    for (int k = 0; k < 4; k ++) {
                        char c = icaoid.charAt (k);
                        if ((c < 'A') || (c > 'Z')) {
                            icaoid = null;
                            break;
                        }
                    }
                }

                // if can't get icaoid, just skip the file
                // otherwise, make sure it is downloaded
                if (icaoid == null) {
                    System.out.println ("-");
                } else {
                    try {
                        downloadPlate (aipliburl, ccode, pdfname, effdate, hreflink);
                        System.out.println ("-" + icaoid);
                    } catch (Exception e) {
                        System.err.println ("exception downloading " + ccode + " " + pdfname + " " + hreflink);
                        e.printStackTrace (System.err);
                        System.out.println ("-");
                    }
                }
                System.out.flush ();
                more = true;
            }
        }
        return more;
    }

    public static void printXmlNode (Node node, String indent)
    {
        switch (node.getNodeType ()) {
            case Node.CDATA_SECTION_NODE: {
                System.out.println (indent + "<![CDATA[" + node.getNodeValue ().replace ("\n", " ") + "]]>");
                break;
            }
            case Node.DOCUMENT_NODE:
            case Node.ELEMENT_NODE: {
                System.out.print (indent + "<" + node.getNodeName ());
                NamedNodeMap attrs = node.getAttributes ();
                if (attrs != null) {
                    int nattrs = attrs.getLength ();
                    for (int i = 0; i < nattrs; i ++) {
                        Node attr = attrs.item (i);
                        System.out.print (" " + attr.getNodeName () + "=\"" + attr.getNodeValue () + "\"");
                    }
                }
                NodeList childs = node.getChildNodes ();
                int nchilds = childs.getLength ();
                if (nchilds > 0) {
                    System.out.println (">");
                    String childindent = indent + "    ";
                    for (int i = 0; i < nchilds; i ++) {
                        printXmlNode (childs.item (i), childindent);
                    }
                    System.out.println (indent + "</" + node.getNodeName () + ">");
                } else {
                    System.out.println ("/>");
                }
                break;
            }
            case Node.TEXT_NODE: {
                System.out.println (indent + "text=" + node.getNodeValue ());
                break;
            }
            default: {
                System.out.println (indent + "misc=" + node.getNodeName () + "=" + node.getTextContent ().trim ());
                break;
            }
        }
    }

    // download plate .pdf if we don't already have it
    //  input:
    //   aipliburl = base URL
    //   ccode     = country code (eg, "ED" for Germany)
    //   pdfname   = name of .pdf file to put on disk
    //   effdate   = plate effective date yyyy-mm-dd
    //   hreflink  = download link relative to aipliburl
    //  output:
    //   file written to pdfdirbase_pdfexpdate/ccode/pdfname.effdate
    public static void downloadPlate (URL aipliburl, String ccode, String pdfname, String effdate, String hreflink)
            throws Exception
    {
        // if file exists as is, we're done as is
        String newname = pdfdirbase + pdfexpdate + "/" + ccode + "/" + pdfname + "." + effdate;
        File newfile = new File (newname);
        if (newfile.exists ()) return;

        // make sure the pdfdirbase_pdfexpdate/ccode directory exists
        newfile.getParentFile ().mkdirs ();

        // maybe there is the same file in an older expiration date directory
        // search for first one found of pdfdirbase_*/ccode/pdfname.effdate
        // if so, make an hardlink
        File pdfdirscan = new File (pdfdirbase).getParentFile ();
        for (File pdfdirold : pdfdirscan.listFiles ()) {
            if (pdfdirold.getPath ().startsWith (pdfdirbase)) {
                File oldfile = new File (pdfdirold, ccode + "/" + pdfname + "." + effdate);
                if (oldfile.exists ()) {
                    java.nio.file.Path oldpath = oldfile.toPath ();
                    java.nio.file.Path newpath = newfile.toPath ();
                    java.nio.file.Files.createLink (newpath, oldpath);
                    return;
                }
            }
        }

        // nowhere to be found, download from eurocontrol
        File newtemp = new File (newname + ".tmp");
        InputStream his = doHttpRequestStream (new URL (aipliburl, hreflink), null);
        try {
            FileOutputStream fos = new FileOutputStream (newtemp);
            int fsize = 0;
            try {
                byte[] buf = new byte[4096];
                for (int rc; (rc = his.read (buf)) > 0;) {
                    fos.write (buf, 0, rc);
                    fsize += rc;
                }
            } finally {
                fos.close ();
            }
            if (fsize < 2000) throw new IOException ("file too small " + fsize);
            if (! newtemp.renameTo (newfile)) throw new IOException ("error renanaming " + newname);
        } finally {
            his.close ();
        }
        Thread.sleep (12345);
    }

    // do http request
    //  input:
    //   urlobj = url to retrieve
    //   data = null: GET request
    //          else: POST request data
    //  output:
    //   returns page contents
    public static String doHttpRequestString (URL urlobj, byte[] data)
            throws Exception
    {
        InputStream his = doHttpRequestStream (urlobj, data);
        try {
            StringBuilder rsb = new StringBuilder ();
            BufferedReader rdr = new BufferedReader (new InputStreamReader (his));
            for (String line; (line = rdr.readLine ()) != null;) {
                rsb.append (line);
                rsb.append ('\n');
            }
            return rsb.toString ();
        } finally {
            his.close ();
        }
    }

    // do http request
    //  input:
    //   urlobj = url to retrieve
    //   data = null: GET request
    //          else: POST request data
    //  output:
    //   returns stream for reading page contents
    public static InputStream doHttpRequestStream (URL urlobj, byte[] data)
            throws Exception
    {
        // some urls have spaces that the server doesn't understand (we get 404)
        String urlstr = urlobj.toString ();
        if (urlstr.contains (" ")) {
            urlobj = new URL (urlstr.replace (" ", "%20"));
        }
        //System.err.println ("doHttpRequestStream*: url=" + urlobj);

        HttpURLConnection httpcon = (HttpURLConnection) urlobj.openConnection ();
        try {
            httpcon.setRequestMethod ((data == null) ? "GET" : "POST");

            // set outgoing cookies
            if (! cookies.isEmpty ()) {
                StringBuilder csb = new StringBuilder ();
                for (String key : cookies.keySet ()) {
                    if (csb.length () > 0) csb.append (';');
                    csb.append (key);
                    csb.append ('=');
                    csb.append (cookies.get (key));
                }
                httpcon.setRequestProperty ("Cookie", csb.toString ());
            }

            // connect and send outgoing data
            if (data != null) {
                httpcon.setDoOutput (true);
                httpcon.setFixedLengthStreamingMode (data.length);
            }
            httpcon.connect ();
            if (data != null) {
                OutputStream out = httpcon.getOutputStream ();
                out.write (data);
            }

            // get response code and reply headers
            int rc = httpcon.getResponseCode ();
            Map<String,List<String>> rawhdrs = httpcon.getHeaderFields ();
            HashMap<String,List<String>> lchdrs = new HashMap<> ();
            for (String key : rawhdrs.keySet ()) {
                if (key != null) {
                    lchdrs.put (key.toLowerCase (), rawhdrs.get (key));
                }
            }

            // update cookies
            List<String> cvals = lchdrs.get ("set-cookie");
            if (cvals != null) {
                for (String cval : cvals) {
                    int i = cval.indexOf (';');
                    if (i >= 0) cval = cval.substring (0, i);
                    i = cval.indexOf ('=');
                    if (i < 0) cookies.remove (cval);
                    else {
                        String n = cval.substring (0, i);
                        String v = cval.substring (++ i);
                        cookies.put (n, v);
                    }
                }
            }

            switch (rc) {

                // success, return reply stream
                case HttpURLConnection.HTTP_OK: {
                    InputStream his = httpcon.getInputStream ();
                    httpcon = null;
                    return his;
                }

                // moved permanently/temporarily, retry with the given GET url
                case HttpURLConnection.HTTP_MOVED_PERM:
                case HttpURLConnection.HTTP_MOVED_TEMP: {

                    // create composite url
                    String location = lchdrs.get ("location").get (0);
                    URL newlocurl = new URL (urlobj, location);

                    // sometimes we get location=https://www.https://www.ead.eurocontrol.int/
                    // ...meaning switch the http->https but leave the filename part as is
                    String newfile = newlocurl.getFile ();
                    if (newfile.equals ("") || newfile.equals ("/")) {
                        newlocurl = new URL (newlocurl, urlobj.getFile ());
                    }

                    // try again with new url, always in GET mode
                    //System.err.println ("doHttpRequestStream*: rc=" + rc + " location=" + location + " newurl=" + newlocurl);
                    return doHttpRequestStream (newlocurl, null);
                }

                // success, return reply stream
                case HttpURLConnection.HTTP_NOT_FOUND: {
                    throw new FileNotFoundException (urlobj.toString ());
                }

                default: {
                    throw new Exception ("http response code " + rc);
                }
            }
        } finally {
            if (httpcon != null) httpcon.disconnect ();
        }
    }
}
