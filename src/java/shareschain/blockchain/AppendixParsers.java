
package shareschain.blockchain;

import shareschain.account.PublicKeyAnnouncementAppendix;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

public final class AppendixParsers {

    public static Collection<Appendix.Parser> getParsers() {
        return parsersMap.values();
    }

    public static Appendix.Parser getParser(int appendixType) {
        return parsersMap.get(appendixType);
    }

    public static Collection<Appendix.Parser> getPrunableParsers() {
        return prunableParsers;
    }

    private static final SortedMap<Integer,Appendix.Parser> parsersMap;
    static {
        SortedMap<Integer,Appendix.Parser> map = new TreeMap<>();
        map.put(PublicKeyAnnouncementAppendix.appendixType, PublicKeyAnnouncementAppendix.appendixParser);
        parsersMap = Collections.unmodifiableSortedMap(map);
    }

    private static final List<Appendix.Parser> prunableParsers;
    static {
        List<Appendix.Parser> list = new ArrayList<>();
        prunableParsers = Collections.unmodifiableList(list);
    }

    private AppendixParsers() {}

}
