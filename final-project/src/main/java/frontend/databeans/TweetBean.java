package frontend.databeans;

public class TweetBean {
    public Long tid;
    public String timestamp;
    public Long userid;
    public String username;
    public String text;
    public Integer favorite_count;
    public Integer retweet_count;
    public Integer count;

    public TweetBean(Long tid, String timestamp, Long userid, String username, String text, Integer favorite_count, Integer retweet_count) {
        this.tid = tid;
        this.timestamp = timestamp;
        this.userid = userid;
        this.username = username;
        this.text = text;
        this.favorite_count = favorite_count;
        this.retweet_count = retweet_count;
        this.count = favorite_count + retweet_count;
    }
}
