package frontend.controller;

import frontend.dao.MySQLDAO;
import frontend.databeans.TweetBean;
import frontend.databeans.TweetBeanComparator;
import frontend.utils.PercentDecoder;
import io.undertow.server.HttpServerExchange;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.lang.Math;


public class Q4Action extends Action {
    private static final String teamInfo = "majorLoads,051924137118\n";
    private static final String success = "majorLoads,051924137118\nsuccess\n";
    private MySQLDAO dao;
    private static final Map<Integer, String> ENDPOINT_MAP;
    static
    {
        ENDPOINT_MAP = new HashMap<>();
        ENDPOINT_MAP.put(0, "http://ec2-34-239-227-6.compute-1.amazonaws.com/q4");
        ENDPOINT_MAP.put(1, "http://ec2-34-239-227-6.compute-1.amazonaws.com/q4");
        ENDPOINT_MAP.put(2, "http://ec2-34-239-227-6.compute-1.amazonaws.com/q4");
        ENDPOINT_MAP.put(3, "http://ec2-34-239-227-6.compute-1.amazonaws.com/q4");
        ENDPOINT_MAP.put(4, "http://ec2-34-239-227-6.compute-1.amazonaws.com/q4");
        ENDPOINT_MAP.put(5, "http://ec2-18-204-214-136.compute-1.amazonaws.com/q4");
        ENDPOINT_MAP.put(6, "http://ec2-18-204-214-136.compute-1.amazonaws.com/q4");
    }

    public Q4Action(MySQLDAO dao) {
        this.dao = dao;
    }

    private String read(Long uid1, Long uid2, int n, String uuid, int seq) {
        List<TweetBean> tweetList = dao.read(uid1, uid2, uuid, seq);
        tweetList.sort(new TweetBeanComparator());
        int num = 0;
        if (n > tweetList.size()) {
            num = tweetList.size();
        } else {
            num = n;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(teamInfo);
        for (int i=0; i < num; i++) {
            TweetBean tb = tweetList.get(i);
            sb.append(tb.tid)
                    .append("\t")
                    .append(tb.timestamp)
                    .append("\t")
                    .append(tb.userid)
                    .append("\t")
                    .append(tb.username)
                    .append("\t")
                    .append(tb.text)
                    .append("\t")
                    .append(tb.favorite_count)
                    .append("\t")
                    .append(tb.retweet_count)
                    .append("\n");
        }
        sb.deleteCharAt(sb.length() - 1);

        return sb.toString();
    }

    private String write(Long tid, String timestamp, String uuid, Long userid, String username, String text, int favorite_count, int retweet_count, int seq) {
        dao.write(tid, timestamp, uuid, userid, username, text, favorite_count, retweet_count, seq);
        return success;
    }

    private String set(String field, Long tid, String payload, String uuid, int seq) {
        dao.set(field, tid, payload, uuid, seq);
        return success;
    }

    private String delete(Long tid, String uuid, int seq) {
        dao.delete(tid, uuid, seq);
        return success;
    }

    public Integer getHash(String uuid) {
        return Math.abs(uuid.hashCode() % ENDPOINT_MAP.size());
    }

    public String handleGet(final HttpServerExchange exchange) {
        Map<String, Deque<String>> params = exchange.getQueryParameters();

        if (!params.containsKey("uuid") || !params.containsKey("seq")) { return teamInfo; }

        String uuid = params.get("uuid").poll();
        int seq = Integer.parseInt(params.get("seq").poll());

        if (!params.containsKey("op")) { return teamInfo; }

        String op = params.get("op").poll();
        HashSet<String> ops = new HashSet<>();
        ops.add("read");
        ops.add("write");
        ops.add("set");
        ops.add("delete");

        if (!ops.contains(op)) { return teamInfo; }

        if (!params.containsKey("f")) {
            System.out.println("no key found");
            Integer hash = getHash(uuid);
            System.out.println("hash: " + hash.toString());
            System.out.println("url: " + ENDPOINT_MAP.get(hash));
            try {
                String s = exchange.getQueryString();
                StringBuilder sb = new StringBuilder();
                sb.append(ENDPOINT_MAP.get(hash)).append("?");
                if (s.length() > 0) {
                    sb.append(s).append("&");
                }
                sb.append("f").append("=").append("true");

                URL url = new URL(sb.toString());

                // URL url = new URL("http://ec2-35-169-124-29.compute-1.amazonaws.com:80/q4?op=write&payload=%7B\"created_at\":\"Sat%20May%2010%2016:36:25%20%2B0000%202014\",\"favorite_count\":22,\"id\":69,\"text\":\"Cloud%20Computing\",\"user\":%7B\"screen_name\":\"new\",\"id\":619%7D,\"retweet_count\":33%7D&uuid=E&seq=1");
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("GET");
                // con.setDoOutput(true);
                con.setConnectTimeout(3000);
                con.setReadTimeout(3000);

                int responseCode = con.getResponseCode();
                System.out.println(responseCode);
//              System.out.println("\nSending 'GET' request to URL : " + url);
//              System.out.println("Response Code : " + responseCode);
                String r;
                if (op.equals("read")) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                    String inputLine;
                    StringBuffer response = new StringBuffer();
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    r = response.toString();
                    in.close();
                } else {
                    r = success;
                }

                return r;
            } catch (IOException e) {
                e.printStackTrace();
                return teamInfo;
            }
        }
        System.out.println("key found");
        // System.out.println("OPERATION: " + op);
        // System.out.println("uuid: " + String.valueOf(uuid));
        // System.out.println("seq: " + String.valueOf(seq));

        String response;
        if (op.equals("read")) {
            if (!params.containsKey("uid1") || !params.containsKey("uid2") || !params.containsKey("n") || !params.containsKey("uuid") || !params.containsKey("seq")) { return teamInfo; }
            Long uid1 = Long.parseLong(params.get("uid1").poll());
            Long uid2 = Long.parseLong(params.get("uid2").poll());
            int n = Integer.parseInt(params.get("n").poll());

//            System.out.println("uid1: " + String.valueOf(uid1));
//            System.out.println("uid2: " + String.valueOf(uid2));
//            System.out.println("n: " + String.valueOf(n));

            response = read(uid1, uid2, n, uuid, seq);
        } else if (op.equals("write")) {
            if (!params.containsKey("payload") || !params.containsKey("uuid") || !params.containsKey("seq")) { return teamInfo; }
            String payload = PercentDecoder.decode(params.get("payload").poll());
            JSONObject obj = new JSONObject(payload);

//            for (String k : obj.keySet()) {
//                System.out.println("Key: " + k);
//                System.out.println(obj.get(k));
//            }

            if (!obj.has("id") || !obj.has("user") || !obj.has("created_at") || !obj.has("text") || !obj.has("favorite_count") || !obj.has("retweet_count")) { return teamInfo; }
            Long tid = obj.getLong("id");
            String timestamp = obj.getString("created_at");
            String text = obj.getString("text");
            int favorite_count = obj.getInt("favorite_count");
            int retweet_count = obj.getInt("retweet_count");

            JSONObject user = (JSONObject) obj.get("user");

//            System.out.println(user);
//            for (String k : user.keySet()) {
//                System.out.println("User Key: " + k);
//                System.out.println(user.get(k));
//            }

            Long userid = user.getLong("id");
            String username = user.getString("screen_name");

//            System.out.println("tid: " + String.valueOf(tid));
//            System.out.println("timestamp: " + timestamp);
//            System.out.println("userid: " + String.valueOf(userid));
//            System.out.println("username: " + username);
//            System.out.println("text: " + text);
//            System.out.println("favorite_count: " + String.valueOf(favorite_count));
//            System.out.println("retweet_count: " + String.valueOf(retweet_count));

            //response = success;
            response = write(tid, timestamp, uuid, userid, username, text, favorite_count, retweet_count, seq);
        } else if (op.equals("set")) {
            // http://ec2-54-236-240-180.compute-1.amazonaws.com/op=set&field=favorite_count&tid=447028085670412288&payload=69&uuid=unique_id&seq=1
            // op=set & field=field_to_set & tid=tweet_id & payload=string & uuid=unique_id & seq=sequence_number
            if (!params.containsKey("field") || !params.containsKey("tid") || !params.containsKey("payload") || !params.containsKey("uuid") || !params.containsKey("seq")) { return teamInfo; }
            Long tid = Long.parseLong(params.get("tid").poll());
            String payload = PercentDecoder.decode(params.get("payload").poll());  //TODO not sure if this works
            String field = params.get("field").poll();

//            System.out.println("tid: " + String.valueOf(tid));
//            System.out.println("payload: " + payload);
//            System.out.println("field: " + field);

            response = set(field, tid, payload, uuid, seq);
        } else if (op.equals("delete")) {
            // GET /q4?op=delete & tid=tweet_id & uuid=unique_id & seq=sequence_number
            if (!params.containsKey("tid") || !params.containsKey("uuid") || !params.containsKey("seq")) { return teamInfo; }
            Long tid = Long.parseLong(params.get("tid").poll());
            response = delete(tid, uuid, seq);
        } else {
            response = teamInfo;
        }
        return response;
    }

    public String handlePost(final HttpServerExchange exchange) { return handleGet(exchange); }
}