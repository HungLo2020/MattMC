package com.seibel.distanthorizons.core.util.objects.quadTree;

/** Incremental CPU topology/value notifications for a retained consumer. */
public interface QuadTreeMutationObserver<T> {
    void added(long key);
    void valueChanged(long key, T previous, T current);
    void childrenRemoved(long key);
    void removed(long key);
}
