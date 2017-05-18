/**
 * Merge CIFP records from old CIFP file that are missing in new file.
 * Seems they cut out a lot of VOR CIFP stuff in March 2017.
 * So the old CIFP data comes from cycle ending 2017-03-02.
 */

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.LinkedList;
import java.util.TreeMap;

public class MergeOldCifps {

    // args[0] = new CIFP state file
    // args[1] = old CIFP state file
    public static void main (String[] args) throws Exception
    {
        // read new FAA file
        TreeMap<String,LinkedList<String>> newfile = readCifpFile (args[0]);

        // read old FAA file
        TreeMap<String,LinkedList<String>> oldfile = readCifpFile (args[1]);

        // go through old file
        // for each IAP in old file what is not found in new file,
        //  copy from old file to new file
        for (String key : oldfile.keySet ()) {
            if (newfile.get (key) == null) {
                System.err.println ("restoring " + key);
                newfile.put (key, oldfile.get (key));
            }
        }

        // write out updated new file
        for (LinkedList<String> ll : newfile.values ()) {
            for (String line : ll) {
                System.out.println (line);
            }
        }
    }

    // read CIFP file into a tree
    // each CIFP record begins with <ICAOID>,<APPID>,...
    // the tree key is <ICAOID>,<APPID> eg KLWM,S23 for KLWM VOR 23
    // the linked list is all the records beginning with that <ICAOID>,<APPID>
    public static TreeMap<String,LinkedList<String>> readCifpFile (String name) throws Exception
    {
        BufferedReader br = new BufferedReader (new FileReader (name));
        TreeMap<String,LinkedList<String>> tm = new TreeMap<> ();
        for (String line; (line = br.readLine ()) != null;) {
            int i = line.indexOf (',');
            if (i < 0) continue;
            int j = line.indexOf (',', i + 1);
            if (j < 0) continue;
            String key = line.substring (0, j);
            LinkedList<String> ll = tm.get (key);
            if (ll == null) {
                ll = new LinkedList<> ();
                tm.put (key, ll);
            }
            ll.addLast (line);
        }
        return tm;
    }
}
