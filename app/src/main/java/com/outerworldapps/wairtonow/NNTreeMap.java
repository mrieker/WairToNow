package com.outerworldapps.wairtonow;

import android.support.annotation.NonNull;

import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.TreeMap;

public class NNTreeMap<K,V> extends TreeMap<K,V> {
    public NNTreeMap () { super (); }
    public NNTreeMap (Comparator<K> comp) { super (comp); }

    public @NonNull
    V nnget (@NonNull K key)
    {
        V val = super.get (key);
        if (val == null) throw new NoSuchElementException (key.toString ());
        return val;
    }
    public @NonNull
    V nnremove (@NonNull K key)
    {
        V val = super.remove (key);
        if (val == null) throw new NoSuchElementException (key.toString ());
        return val;
    }
}
