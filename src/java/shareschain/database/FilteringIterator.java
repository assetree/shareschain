
package shareschain.database;

import shareschain.util.Filter;

import java.util.Iterator;
import java.util.NoSuchElementException;

public final class FilteringIterator<T> implements Iterator<T>, Iterable<T>, AutoCloseable {

    private final DBIterator<T> dbIterator;
    private final Filter<T> filter;
    private final int from;
    private final int to;
    private T next;
    private boolean hasNext;
    private boolean iterated;
    private int count;

    public FilteringIterator(DBIterator<T> dbIterator, Filter<T> filter) {
        this(dbIterator, filter, 0, Integer.MAX_VALUE);
    }

    public FilteringIterator(DBIterator<T> dbIterator, int from, int to) {
        this(dbIterator, t -> true, from, to);
    }

    public FilteringIterator(DBIterator<T> dbIterator, Filter<T> filter, int from, int to) {
        this.dbIterator = dbIterator;
        this.filter = filter;
        this.from = from;
        this.to = to;
    }

    @Override
    public boolean hasNext() {
        if (hasNext) {
            return true;
        }
        while (dbIterator.hasNext() && count <= to) {
            next = dbIterator.next();
            if (filter.ok(next)) {
                if (count >= from) {
                    count += 1;
                    hasNext = true;
                    return true;
                }
                count += 1;
            }
        }
        hasNext = false;
        return false;
    }

    @Override
    public T next() {
        if (hasNext) {
            hasNext = false;
            return next;
        }
        while (dbIterator.hasNext() && count <= to) {
            next = dbIterator.next();
            if (filter.ok(next)) {
                if (count >= from) {
                    count += 1;
                    hasNext = false;
                    return next;
                }
                count += 1;
            }
        }
        throw new NoSuchElementException();
    }

    @Override
    public void close() {
        dbIterator.close();
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<T> iterator() {
        if (iterated) {
            throw new IllegalStateException("Already iterated");
        }
        iterated = true;
        return this;
    }

}
