package frontend.databeans;

import java.util.Comparator;


public class TweetBeanComparator implements Comparator<TweetBean> {
    @Override public int compare(TweetBean o1, TweetBean o2) {
        if (o2.count.compareTo(o1.count) == 0) {
            return o1.tid.compareTo(o2.tid);
        } else {
            return o2.count.compareTo(o1.count);
        }
    }
}

