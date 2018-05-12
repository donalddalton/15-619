package frontend.databeans;

import java.util.Comparator;


public class KeywordCountComparator implements Comparator<KeywordCountBean> {
    @Override public int compare(KeywordCountBean o1, KeywordCountBean o2) {
        if (o2.getCount().compareTo(o1.getCount()) == 0) {
            return o1.getHashtag().compareTo(o2.getHashtag());
        } else {
            return o2.getCount().compareTo(o1.getCount());
        }
    }
}
