//    Copyright (C) 2018, Mike Rieker, Beverly, MA USA
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

// java ProcessPlates <nthreads> <xmlfile>

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import java.io.File;
import java.util.LinkedList;
import java.util.TreeMap;

public class ProcessPlates {
    public static class WorkItem {
        String state_code;
        String faaid;
        String icaoid;
        String chart_code;
        String chart_name;
        String pdf_name;
        TreeMap<Integer,WorkItem> conts;

        public String getKey ()
        {
            return state_code + "//" + faaid + "//" + chart_code + "//" + chart_name;
        }
    }

    public static int nrunning;
    public static LinkedList<WorkItem> contpages = new LinkedList<> ();
    public static TreeMap<String,WorkItem> workqueue = new TreeMap<> ();

    /*
        <?xml version="1.0" encoding="UTF-8"?>
        <digital_tpp cycle="1812" from_edate="0901Z  11/08/18" to_edate="0901Z  12/06/18">
          <state_code ID="AK" state_fullname="Alaska">
            <city_name ID="ADAK ISLAND" volume="AK-1">
              <airport_name ID="ADAK" military="N" apt_ident="ADK" icao_ident="PADK" alnum="1244">
                <record>
                  <chartseq>10100</chartseq>
                  <chart_code>MIN</chart_code>
                  <chart_name>TAKEOFF MINIMUMS</chart_name>
                  <useraction></useraction>
                  <pdf_name>AKTO.PDF</pdf_name>
                  <cn_flg>N</cn_flg>
                  <cnsection></cnsection>
                  <cnpage></cnpage>
                  <bvsection>L</bvsection>
                  <bvpage></bvpage>
                  <procuid></procuid>
                  <two_colored>N</two_colored>
                  <civil></civil>
                  <faanfd15></faanfd15>
                  <faanfd18></faanfd18>
                  <copter></copter>
                  <amdtnum></amdtnum>
                  <amdtdate></amdtdate>
                </record>
    */

    public static void main (String[] args)
            throws Exception
    {
        int nthreads = Integer.parseInt (args[0]);

        File xmlfile = new File (args[1]);
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance ();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder ();
        Document doc = dBuilder.parse (xmlfile);
        doc.getDocumentElement ().normalize ();
        NodeList dtpplist = doc.getElementsByTagName ("digital_tpp");
        for (int ndtpp = dtpplist.getLength (); -- ndtpp >= 0;) {
            Element dtppnode = (Element) dtpplist.item (ndtpp);
            NodeList statelist = dtppnode.getElementsByTagName ("state_code");
            for (int nstate = statelist.getLength (); -- nstate >= 0;) {
                Element statenode = (Element) statelist.item (nstate);
                String state_code = statenode.getAttribute ("ID");   // 2-letter state code
                //System.out.println ("state_code=" + state_code);
                NodeList citylist = statenode.getElementsByTagName ("city_name");
                for (int ncity = citylist.getLength (); -- ncity >= 0;) {
                    Element citynode = (Element) citylist.item (ncity);
                    NodeList airportlist = citynode.getElementsByTagName ("airport_name");
                    for (int nairport = airportlist.getLength (); -- nairport >= 0;) {
                        Element airportnode = (Element) airportlist.item (nairport);
                        String faaid = airportnode.getAttribute ("apt_ident");
                        String icaoid = airportnode.getAttribute ("icao_ident");
                        //System.out.println ("  faaid=" + faaid + " icaoid=" + icaoid);
                        NodeList recordlist = airportnode.getElementsByTagName ("record");
                        String chart_code = null;
                        String chart_name = null;
                        String pdf_name   = null;
                        for (int nrecord = recordlist.getLength (); -- nrecord >= 0;) {
                            Element recordnode = (Element) recordlist.item (nrecord);
                            NodeList valuelist = recordnode.getElementsByTagName ("*");
                            for (int nvalue = valuelist.getLength (); -- nvalue >= 0;) {
                                Element valuenode = (Element) valuelist.item (nvalue);
                                switch (valuenode.getTagName ()) {
                                    case "chart_code": {
                                        chart_code = valuenode.getTextContent ();
                                        break;
                                    }
                                    case "chart_name": {
                                        chart_name = valuenode.getTextContent ();
                                        break;
                                    }
                                    case "pdf_name": {
                                        pdf_name = valuenode.getTextContent ();
                                        break;
                                    }
                                }
                            }
                            /*System.out.println ("state_code=" + state_code +
                                    " faaid=" + faaid + " icaoid=" + icaoid +
                                    " chart_code=" + ((chart_code == null) ? "(null)" : chart_code) +
                                    " chart_name=" + ((chart_name == null) ? "(null)" : chart_name) +
                                    " pdf_name="   + ((pdf_name   == null) ? "(null)" : pdf_name));*/
                            if ((chart_code != null) && (chart_name != null) && (pdf_name != null)) {
                                WorkItem wi = new WorkItem ();
                                wi.state_code = state_code;
                                wi.faaid      = faaid;
                                wi.icaoid     = icaoid;
                                wi.chart_code = chart_code;
                                wi.chart_name = chart_name;
                                wi.pdf_name   = pdf_name;

                                String key = wi.getKey ();
                                int i = key.indexOf (", CONT.");
                                if (i < 0) {
                                    workqueue.put (key, wi);
                                } else {
                                    contpages.addLast (wi);
                                }
                            }
                        }
                    }
                }
            }
        }

        for (WorkItem contwi : contpages) {
            String key = contwi.getKey ();
            int i = key.indexOf (", CONT.");
            int j = Integer.parseInt (key.substring (i + 7));
            WorkItem mainwi = workqueue.get (key.substring (0, i).trim ());
            if (mainwi == null) {
                System.err.println ("can't find main file for " + key);
            } else {
                // trim spaces before ", CONT." because of "CO//KGJT//DP//GRAND MESA ONE  , CONT.1"
                contwi.chart_name = mainwi.chart_name + ", CONT." + j;
                if (mainwi.conts == null) mainwi.conts = new TreeMap<Integer,WorkItem> ();
                mainwi.conts.put (j, contwi);
            }
        }

        synchronized (workqueue) {
            for (nrunning = 0; nrunning < nthreads; nrunning ++) {
                WorkThread wt = new WorkThread ();
                wt.streamid = nrunning + 1;
                wt.start ();
            }
            while (nrunning > 0) {
                workqueue.wait ();
            }
        }
    }

    public static class WorkThread extends Thread {
        int streamid;

        @Override
        public void run ()
        {
            try {
                while (true) {
                    WorkItem wi;
                    synchronized (workqueue) {
                        if (workqueue.isEmpty ()) {
                            -- nrunning;
                            workqueue.notifyAll ();
                            return;
                        }
                        String key = workqueue.firstKey ();
                        wi = workqueue.get (key);
                        workqueue.remove (key);
                    }
                    processWorkItem (wi);
                    if (wi.conts != null) {
                        for (WorkItem contwi : wi.conts.values ()) {
                            processWorkItem (contwi);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace ();
            }
        }

        public void processWorkItem (WorkItem wi)
                throws Exception
        {
            ProcessBuilder pb = new ProcessBuilder ("./processplate.sh",
                    Integer.toString (streamid), wi.state_code, wi.faaid, wi.icaoid, wi.chart_code, wi.chart_name, wi.pdf_name);
            Process p = pb.inheritIO ().start ();
            p.waitFor ();
            int ec = p.exitValue ();
            if (ec != 0) {
                System.err.println ("ec " + ec + " for " + wi.icaoid + " " + wi.chart_code + " " + wi.chart_name);
            }
        }
    }
}
