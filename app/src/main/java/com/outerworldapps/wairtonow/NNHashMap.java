package com.outerworldapps.wairtonow;

import android.support.annotation.NonNull;

import java.util.HashMap;
import java.util.NoSuchElementException;

public class NNHashMap<K,V> extends HashMap<K,V> {
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
