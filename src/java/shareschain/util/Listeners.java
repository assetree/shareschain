
package shareschain.util;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
// 观察者模式
public final class Listeners<T,E extends Enum<E>> {

    private final ConcurrentHashMap<Enum<E>, List<Listener<T>>> listenersMap = new ConcurrentHashMap<>();

    public boolean addListener(Listener<T> listener, Enum<E> eventType) {
        synchronized (eventType) {
            List<Listener<T>> listeners = listenersMap.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());
            return listeners.add(listener);
        }
    }

    public boolean removeListener(Listener<T> listener, Enum<E> eventType) {
        synchronized (eventType) {
            List<Listener<T>> listeners = listenersMap.get(eventType);
            if (listeners != null) {
                return listeners.remove(listener);
            }
        }
        return false;
    }

    public void notify(T t, Enum<E> eventType) {
        List<Listener<T>> listeners = listenersMap.get(eventType);
        if (listeners != null) {
            for (Listener<T> listener : listeners) {
                listener.notify(t);
            }
        }
    }

}
