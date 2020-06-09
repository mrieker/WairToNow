package com.outerworldapps.wairtonow;

import android.support.annotation.NonNull;

import java.util.NoSuchElementException;

public class NNThreadLocal<V> extends ThreadLocal<V> {
    public @NonNull
    V nnget ()
    {
        V val = super.get ();
        if (val == null) throw new NoSuchElementException ();
        return val;
    }
}
