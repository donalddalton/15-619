package frontend.dao;

import java.sql.*;
import java.util.*;

import frontend.databeans.TweetBean;
import org.json.*;


public class MySQLDAO {
    private List<Connection> connectionPool = new ArrayList<>();
    private String JDBC_URL;
    private String DB_USER;
    private String DB_PWD;

    public MySQLDAO(String ip, String username, String password) {
        JDBC_URL = "jdbc:mysql://" + ip + "/twitter?useSSL=false&useUnicode=true&character_set_server=utf8mb4";
        DB_USER = username;
        DB_PWD = password;
        final int INIT_POOL_SIZE = 10;
        for (int i=0; i<INIT_POOL_SIZE; i++) {
            try {
                connectionPool.add(DriverManager.getConnection(JDBC_URL, DB_USER, DB_PWD));
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private synchronized Connection getConnection() {
        String jdbcDriver = "com.mysql.jdbc.Driver";
        if (connectionPool.size() > 0) {
            return connectionPool.remove(connectionPool.size() - 1);
        }
        try {
            Class.forName(jdbcDriver);
        } catch (ClassNotFoundException e) {
            System.out.println(e);
        }
        try {
            return DriverManager.getConnection(JDBC_URL, DB_USER, DB_PWD);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private synchronized void releaseConnection(Connection con) {
        connectionPool.add(con);
    }

    public List<TweetBean> read(Long uid1, Long uid2, String uuid, int seq) {
        Connection con = null;
        try {
            con = getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM q4 WHERE uuid BETWEEN ? AND ?");
            pstmt.setLong(1, uid1);
            pstmt.setLong(2, uid2);
            ResultSet rs = pstmt.executeQuery();
            List<TweetBean> tweetList = new ArrayList<>();
            while (rs.next()) {
                long tid = rs.getLong("tweetid");
                String timestamp = rs.getString("timestamp");
                long userid = rs.getLong("uuid");
                String username = rs.getString("username");
                String text = rs.getString("text");
                int favorite_count = rs.getInt("favorite_count");
                int retweet_count = rs.getInt("retweet_count");
                TweetBean tb = new TweetBean(tid, timestamp, userid, username, text, favorite_count, retweet_count);
                tweetList.add(tb);
            }
            rs.close();
            pstmt.close();
            releaseConnection(con);

            return tweetList;
        } catch (Exception e) {
            try {
                if (con != null) { con.close(); }
            } catch (SQLException e2) {}
            e.printStackTrace();
            throw new RuntimeException();
        }
    }

    public void write(Long tid, String timestamp, String uuid, Long userid, String username, String text, int favorite_count, int retweet_count, int seq) {
        Connection con = null;
        try {
            con = getConnection();
            PreparedStatement pstmt = con.prepareStatement("INSERT INTO q4 (tweetid, timestamp, uuid, username, text, favorite_count, retweet_count) \n" +
                                                                "VALUES(?, ?, ?, ?, ?, ?, ?) \n" +
                                                                "ON DUPLICATE KEY UPDATE tweetid = VALUES(tweetid), timestamp = VALUES(timestamp), uuid = VALUES(uuid), username = VALUES(username), text = VALUES(text), favorite_count = VALUES(favorite_count), retweet_count = VALUES(retweet_count)");
            pstmt.setLong(1, tid);
            pstmt.setString(2, timestamp);
            pstmt.setLong(3, userid);
            pstmt.setString(4, username);
            pstmt.setString(5, text);
            pstmt.setInt(6, favorite_count);
            pstmt.setInt(7, retweet_count);

            int result = pstmt.executeUpdate();
            System.out.println("WRITE RESULT: " + String.valueOf(result));

            pstmt.close();
            releaseConnection(con);

        } catch (Exception e) {
            try {
                if (con != null) { con.close(); }
            } catch (SQLException e2) {}
            e.printStackTrace();
            throw new RuntimeException();
        }
    }

    public void set(String field, Long tid, String payload, String uuid, int seq) {
        Connection con = null;
        try {
            con = getConnection();
            PreparedStatement pstmt = con.prepareStatement("UPDATE q4 \n" +
                    "SET " + field + "=? \n" +
                    "WHERE tweetid=?");
            if (field.equals("text")) {
                pstmt.setString(1, payload);
            } else {
                pstmt.setLong(1, Long.parseLong(payload));  // must be favorite_count or retweet_count
            }
            pstmt.setLong(2, tid);
            int result = pstmt.executeUpdate();
            System.out.println("SET RESULT: " + String.valueOf(result));
            pstmt.close();
            releaseConnection(con);

        } catch (Exception e) {
            try {
                if (con != null) { con.close(); }
            } catch (SQLException e2) {}
            e.printStackTrace();
            throw new RuntimeException();
        }
    }

    public void delete(Long tid, String uuid, int seq) {
        Connection con = null;
        try {
            con = getConnection();
            PreparedStatement pstmt = con.prepareStatement("DELETE FROM q4 WHERE tweetid=?");
            pstmt.setLong(1, tid);

            int result = pstmt.executeUpdate();
            System.out.println("DELETE RESULT: " + String.valueOf(result));

            pstmt.close();
            releaseConnection(con);

        } catch (Exception e) {
            try {
                if (con != null) { con.close(); }
            } catch (SQLException e2) {}
            e.printStackTrace();
            throw new RuntimeException();
        }
    }


    public Map<String, Map<String, JSONArray>> getQ3Response(String uid_start, String uid_end) {
        Connection con = null;
        try {
            con = getConnection();
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM q3 WHERE uuid BETWEEN ? AND ?");
            pstmt.setLong(1, Long.parseLong(uid_start));
            pstmt.setLong(2, Long.parseLong(uid_end));
            ResultSet rs = pstmt.executeQuery();
            Map<String, Map<String, JSONArray>> hm = new HashMap<>();
            while (rs.next()) {
                String uuid = rs.getString("uuid");
                String attributes = rs.getString("attributes");
                JSONObject obj = new JSONObject(attributes);
                JSONArray tid = obj.getJSONArray("tid");
                JSONArray ts = obj.getJSONArray("ts");
                JSONArray tw = obj.getJSONArray("tw");
                JSONArray ct = obj.getJSONArray("ct");
                JSONArray nw = obj.getJSONArray("nw");
                JSONArray is = obj.getJSONArray("is");
                Map<String, JSONArray> inner = new HashMap<>();
                inner.put("tid", tid);
                inner.put("ts", ts);
                inner.put("tw", tw);
                inner.put("ct", ct);
                inner.put("nw", nw);
                inner.put("is", is);
                hm.put(uuid, inner);
            }
            rs.close();
            pstmt.close();
            releaseConnection(con);

            return hm;
        } catch (Exception e) {
            try {
                if (con != null) {
                    con.close();
                }
            } catch (SQLException e2) {}
            throw new RuntimeException();
        }
    }


    public Map<String, Map<String, String>> getKeywordCounts(String[] keyword) {
        Connection con = null;
        try {
            con = getConnection();

            String table = "q2";
            PreparedStatement pstmt = con.prepareStatement("SELECT * FROM " + table + " WHERE keyword IN (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

            for (int i=0; i<10; i++) {
                if (i >= keyword.length) {
                    pstmt.setString(i+1, "NULL");
                } else {
                    pstmt.setString(i+1, keyword[i]);
                }
            }

            ResultSet rs = pstmt.executeQuery();
            Map<String, Map<String, String>> hm = new HashMap<>();
            while (rs.next()) {
                String key = rs.getString("keyword");
                String hashtags = rs.getString("hashtags");
                String counts = rs.getString("counts");
                String users = rs.getString("users");
                Map<String, String> keyWordMap = new HashMap<>();
                keyWordMap.put("hashtags", hashtags);
                keyWordMap.put("counts", counts);
                keyWordMap.put("users", users);
                hm.put(key, keyWordMap);
            }
            rs.close();
            pstmt.close();
            releaseConnection(con);

            return hm;
        } catch (Exception e) {
            try {
                if (con != null) {
                    con.close();
                }
            } catch (SQLException e2) {}
//            for (String s : keyword) {
//                System.out.println(s);
//            }
            // e.printStackTrace();
            return new HashMap<>();
            //throw new RuntimeException();

        }
    }
}