package frontend.databeans;


public class KeywordCountBean {
    private Integer count;
    private String hashtag;

    public KeywordCountBean(Integer count, String hashtag) {
        this.count = count;
        this.hashtag = hashtag;
    }

    public Integer getCount() {
        return count;
    }

    public String getHashtag() {
        return hashtag;
    }
}
