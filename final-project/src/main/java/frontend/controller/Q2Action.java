package frontend.controller;

import frontend.dao.MySQLDAO;
import frontend.databeans.KeywordCountBean;
import frontend.databeans.KeywordCountComparator;
import io.undertow.server.HttpServerExchange;
import org.apache.commons.collections4.map.LRUMap;

import java.util.*;


public class Q2Action extends Action {
    private static final String teamInfo = "majorLoads,051924137118\n";
    private MySQLDAO dao;

    public Q2Action(MySQLDAO dao) {
        this.dao = dao;
    }

    private String response(String keywords, String n, String userId) {
        String[] keywordList = keywords.split(",");
        Integer num = Integer.parseInt(n);
        Map<String, Map<String, String>> keyMaps = dao.getKeywordCounts(keywordList);
        if (keyMaps.isEmpty()) {
            return "empty";
        }
        List<KeywordCountBean> keyCounts = new ArrayList<>(keyMaps.size());

        for (String key : keyMaps.keySet()) {  // keyMaps[key] = {hashtags:[...], counts: [...], users: [...]}

            Map<String, String> keyMap = keyMaps.get(key);
            List<String> counts = new ArrayList<>(Arrays.asList(keyMap.get("hashtags").split(",")));
            List<String> hashtags = new ArrayList<>(Arrays.asList(keyMap.get("counts").split(",")));
            List<String> users = new ArrayList<>(Arrays.asList(keyMap.get("users").split(":")));

            if (hashtags.size() != counts.size() || counts.size() != users.size()) {
                return "exception";
            }

            for (int i=0; i<hashtags.size(); i++) {
                String hashtag = hashtags.get(i);

                int count = Integer.parseInt(counts.get(i));

                List<String> usersByTag = new ArrayList<>(Arrays.asList(users.get(i).split(",")));
                if (usersByTag.contains(userId)) {
                    keyCounts.add(new KeywordCountBean(count + 1, hashtag));
                } else {
                    keyCounts.add(new KeywordCountBean(count, hashtag));
                }
            }
        }

        keyCounts.sort(new KeywordCountComparator());
        StringBuilder sb = new StringBuilder();
        sb.append(teamInfo);

        if (keyCounts.size() >= num) {
            for (int idx=0; idx<num; idx++) {
                sb.append("#").append(keyCounts.get(idx).getHashtag());
                if (idx == num - 1) { sb.append("\n"); }
                else { sb.append(","); }
            }
        } else {
            for (int idx=0; idx<keyCounts.size(); idx++) {
                sb.append("#").append(keyCounts.get(idx).getHashtag());
                if (idx == keyCounts.size() - 1) { sb.append("\n"); }
                else { sb.append(","); }
            }
        }

        return sb.toString();
    }

    public String handleGet(final HttpServerExchange exchange) {
        // this is all input validation code
        Map<String, Deque<String>> params = exchange.getQueryParameters();
        if (!params.containsKey("keywords") || !params.containsKey("n") || !params.containsKey("user_id")) { return teamInfo; }

        String keywords  = params.get("keywords").poll();
        String n = params.get("n").poll();
        String userId = params.get("user_id").poll();

        if (keywords.equals("") || n.equals("") || userId.equals("")) { return teamInfo; }

        return response(keywords, n, userId);
    }

    public String handlePost(final HttpServerExchange exchange) { return handleGet(exchange); }
}
