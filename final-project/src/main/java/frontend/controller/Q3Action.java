package frontend.controller;

import frontend.dao.MySQLDAO;
import frontend.utils.Template;
import io.undertow.server.HttpServerExchange;
import org.json.JSONArray;

import java.util.*;


public class Q3Action extends Action {
    private static final String teamInfo = "majorLoads,051924137118\n";
    private MySQLDAO dao;
    private static HashSet<String> fuck_words;

    public Q3Action(MySQLDAO dao) {
        this.dao = dao;
        this.fuck_words = Template.fkWords;
    }

    private static Map<String, Double> sortByComparatorDouble(Map<String, Double> unsortMap, final boolean order,final boolean tie_order)
    {

        List<Map.Entry<String, Double>> list = new LinkedList<Map.Entry<String, Double>>(unsortMap.entrySet());

        // Sorting the list based on values
        Collections.sort(list, new Comparator<Map.Entry<String, Double>>()
        {
            public int compare(Map.Entry<String, Double> o1,
                    Map.Entry<String, Double> o2)
            {
                if (order)
                {
                    int r = o1.getValue().compareTo(o2.getValue());
                    if (r==0){
                        if(tie_order) {
                            r = o1.getKey().compareTo(o2.getKey());
                        }
                        else{
                            r = o2.getKey().compareTo(o1.getKey());
                        }
                    }
                    return r;

                }
                else
                {
                    int r= o2.getValue().compareTo(o1.getValue());
                    if(r==0){
                        if(tie_order) {
                            r = o1.getKey().compareTo(o2.getKey());
                        }
                        else{
                            r = o2.getKey().compareTo(o1.getKey());
                        }
                    }
                    return r;
                }
            }
        });

        // Maintaining insertion order with the help of LinkedList
        Map<String, Double> sortedMap = new LinkedHashMap<String, Double>();
        for (Map.Entry<String, Double> entry : list)
        {
            sortedMap.put(entry.getKey(), entry.getValue());
        }

        return sortedMap;
    }
    private static Map<String, Integer> sortByComparatorInteger(Map<String, Integer> unsortMap, final boolean order,final boolean tie_order)
    {

        List<Map.Entry<String, Integer>> list = new LinkedList<Map.Entry<String, Integer>>(unsortMap.entrySet());

        // Sorting the list based on values
        Collections.sort(list, new Comparator<Map.Entry<String, Integer>>()
        {
            public int compare(Map.Entry<String, Integer> o1,
                    Map.Entry<String, Integer> o2)
            {
                if (order)
                {
                    int r = o1.getValue().compareTo(o2.getValue());
                    if (r==0){
                        if(tie_order) {
                            r = o1.getKey().compareTo(o2.getKey());
                        }
                        else{
                            r = o2.getKey().compareTo(o1.getKey());
                        }
                    }
                    return r;

                }
                else
                {
                    int r= o2.getValue().compareTo(o1.getValue());
                    if(r==0){
                        if(tie_order) {
                            r = o1.getKey().compareTo(o2.getKey());
                        }
                        else{
                            r = o2.getKey().compareTo(o1.getKey());
                        }
                    }
                    return r;
                }
            }
        });

        // Maintaining insertion order with the help of LinkedList
        Map<String, Integer> sortedMap = new LinkedHashMap<String, Integer>();
        for (Map.Entry<String, Integer> entry : list)
        {
            sortedMap.put(entry.getKey(), entry.getValue());
        }

        return sortedMap;
    }
    private String response(String time_start, String time_end, String uid_start, String uid_end, String n1, String n2){//,Map<String, Map<String, JSONArray>> resp) {
        Map<String, Map<String, JSONArray>> resp = dao.getQ3Response(uid_start, uid_end);
        //Gson gson = new Gson();
        //String json = gson.toJson(resp);
        //System.out.println(json);

        HashMap<String,Double> wScoreAccum = new HashMap<>();
        HashMap<String,HashSet<String>> wordsInTweetIdx= new HashMap<>();
        Integer totalTweets = 0;
        Integer t_start = Integer.parseInt(time_start);
        Integer t_end = Integer.parseInt(time_end);

        for (String uuid : resp.keySet()) {
            Map<String, JSONArray> uuidMap = resp.get(uuid);
            //JSONArray tids = uuidMap.get("tid");
            JSONArray timestamps = uuidMap.get("ts");
            JSONArray tweetWords = uuidMap.get("tw");
            JSONArray censoredTweets = uuidMap.get("ct");
            JSONArray numWords = uuidMap.get("nw");
            JSONArray impactScores = uuidMap.get("is");
            for (Integer i = 0 ; i < tweetWords.length(); i++) {
                if(timestamps.getInt(i) < t_start || timestamps.getInt(i) > t_end){
                    continue;
                }
                String words = (String) tweetWords.get(i);
                String [] wordList = words.split(" ");
                for(String word: wordList) {
                    if (wScoreAccum.containsKey(word)) {
                        wScoreAccum.put(word, wScoreAccum.get(word) + (Math.log(1 + impactScores.getInt(i)) /
                                numWords.getInt(i)));
                    } else {
                        wScoreAccum.put(word, (Math.log(1 + impactScores.getInt(i)) /
                                numWords.getInt(i)));
                    }
                    if(wordsInTweetIdx.containsKey(word)){
                        wordsInTweetIdx.get(word).add(uuid+"_"+ i.toString());
                    }
                    else{
                        HashSet<String> x = new HashSet<>();
                        x.add(uuid+"_"+i.toString());
                        wordsInTweetIdx.put(word, x);
                    }
                }
                totalTweets++;
            }
            //totalTweets += tweetWords.length();
        }
        for(Map.Entry<String,Double> entry :wScoreAccum.entrySet()){
            String word = entry.getKey();
            Double score = entry.getValue();
            Integer tweetsWithThisWord = wordsInTweetIdx.get(word).size();
            entry.setValue(score * (Math.log(((double)totalTweets)/tweetsWithThisWord)));
        }
        Map<String,Double>sortedWords = sortByComparatorDouble(wScoreAccum,false,true);
        int topCount =0;
        Integer n1_int = Integer.parseInt(n1);
        Integer n2_int = Integer.parseInt(n2);
        StringBuilder res = new StringBuilder(500);
        res.append(teamInfo);
        //StringBuilder topicWords = new StringBuilder(100);
        HashMap<String,Integer> tiduuidImpact = new HashMap<String,Integer>();
        for(Map.Entry<String,Double> entry: sortedWords.entrySet()){
            if(topCount >= n1_int){
                break;
            }

            String word = entry.getKey();
            Double score = entry.getValue();

            HashSet<String> tweetIdx = wordsInTweetIdx.get(word);
            if(fuck_words.contains(word)){
                char[] w_char = word.toCharArray();
                for(int i=1;i<word.length()-1;i++){
                    w_char[i]= '*';
                }
                word = new String(w_char);
            }
            res.append(word).append(":").append(String.format("%.2f", score)).append("\t");
            for(String uuid_i : tweetIdx){
                String[] uuid_i_arr = uuid_i.split("_");
                Integer i = Integer.parseInt(uuid_i_arr[1]);
                Integer is = resp.get(uuid_i_arr[0]).get("is").getInt(i);
                String tid = resp.get(uuid_i_arr[0]).get("tid").getString(i);
                tiduuidImpact.put(tid+"_"+uuid_i,is);

            }
            topCount++;
        }
        res.deleteCharAt(res.length()-1);
        res.append("\n");
        Map <String,Integer> sortedImpacts =sortByComparatorInteger(tiduuidImpact,false,false);
        topCount =0;
        StringBuilder tweetsBuilder = new StringBuilder();
        for(Map.Entry<String,Integer> entry:sortedImpacts.entrySet()){
            if(topCount >= n2_int){
                break;
            }
            String [] tid_uuid_i = entry.getKey().split("_");
            Integer impact_score = entry.getValue();
            Integer i = Integer.parseInt(tid_uuid_i[2]);
            String censored_tweet = (String)resp.get(tid_uuid_i[1]).get("ct").get(i);
            res.append(impact_score).append("\t").append(tid_uuid_i[0]).append("\t").append(censored_tweet).append("\n");
            topCount++;
        }

        res.deleteCharAt(res.length()-1);

        return res.toString();
    }

    public String handleGet(final HttpServerExchange exchange) {
        // this is all input validation code
        Map<String, Deque<String>> params = exchange.getQueryParameters();

        if (!params.containsKey("time_start") || !params.containsKey("time_end") || !params.containsKey("uid_start") || !params.containsKey("uid_end") || !params.containsKey("n1") || !params.containsKey("n2")) { return teamInfo; }

        String time_start = params.get("time_start").poll();
        String time_end = params.get("time_end").poll();
        String uid_start = params.get("uid_start").poll();
        String uid_end = params.get("uid_end").poll();
        String n1 = params.get("n1").poll();
        String n2 = params.get("n2").poll();

        if (time_start.equals("") || time_end.equals("") || uid_start.equals("") ||    uid_end.equals("") || n1.equals("") || n2.equals("")
                || Integer.parseInt(time_end) < Integer.parseInt(time_start)|| Long.parseLong(uid_end) < Long.parseLong(uid_start)) { return teamInfo; }

        return response(time_start, time_end, uid_start, uid_end, n1, n2);
    }

    public String handlePost(final HttpServerExchange exchange) { return handleGet(exchange); }
}

