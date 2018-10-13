
package shareschain.util;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.tika.Tika;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class Search {

    private static final Analyzer analyzer = new StandardAnalyzer();

    public static String[] parseTags(String tags, int minTagLength, int maxTagLength, int maxTagCount) {
        if (tags.trim().length() == 0) {
            return Convert.EMPTY_STRING;
        }
        List<String> list = new ArrayList<>();
        try (TokenStream stream = analyzer.tokenStream(null, tags)) {
            CharTermAttribute attribute = stream.addAttribute(CharTermAttribute.class);
            String tag;
            stream.reset();
            while (stream.incrementToken() && list.size() < maxTagCount &&
                    (tag = attribute.toString()).length() <= maxTagLength && tag.length() >= minTagLength) {
                if (!list.contains(tag)) {
                    list.add(tag);
                }
            }
            stream.end();
        } catch (IOException e) {
            throw new RuntimeException(e.toString(), e);
        }
        return list.toArray(new String[list.size()]);
    }

    public static String detectMimeType(byte[] data, String filename) {
        Tika tika = new Tika();
        try {
            return tika.detect(data, filename);
        } catch (NoClassDefFoundError e) {
            Logger.logErrorMessage("Error running Tika parsers", e);
            return null;
        }
    }

    public static String detectMimeType(byte[] data) {
        Tika tika = new Tika();
        try {
            return tika.detect(data);
        } catch (NoClassDefFoundError e) {
            Logger.logErrorMessage("Error running Tika parsers", e);
            return null;
        }
    }

    private Search() {}

}
