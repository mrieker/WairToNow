package com.outerworldapps.wairtonow;

import android.support.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.NoSuchElementException;

public class NNLinkedHashMap<K,V> extends LinkedHashMap<K,V> {
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
