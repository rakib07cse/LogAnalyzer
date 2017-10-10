/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package log.analyzers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import log.util.Tools;
import org.apache.log4j.Logger;

import static java.lang.String.format;

public class LiveStreamHistory implements Analyzer {

    private static final Logger logger = Logger.getLogger(LiveStreamHistory.class);

    public static final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssSSS");
    private static final Gson gson = new GsonBuilder().create();
    private final Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
    private final Pattern lshPattern = Pattern.compile("^(\\d{17})\\s+INFO - LiveStreamHistory->(.*)$");
    private static final String ZERO_TIMESTAMP = "00000000000000000";
    private final List<Map<String, Object>> dtoValueStore = new ArrayList<>();
    private static final String QUERY_TEMPLATE = "INSERT INTO analytics_live_stream (%s) VALUES (%s) ON DUPLICATE KEY UPDATE %s;";

    private final String DELETE_LIVE_STREAM = "DELETE FROM analytics_live_stream  WHERE logtime >= ? and logtime < ? ";
    private final Connection sqlConnection;

    public LiveStreamHistory(Connection sqlConnection) {
        this.sqlConnection = sqlConnection;
    }

    @Override
    public void clear() {
        dtoValueStore.clear();
    }

    @Override
    public boolean processLog(String log) {
        return processLiveStreamHistory(log);
    }

    private boolean processLiveStreamHistory(String log) {
        Matcher m = lshPattern.matcher(log);
        if (m.matches()) {
            String timestamp = m.group(1);
            String methodParams = m.group(2);
            Map<String, Object> dtoValue = gson.fromJson(methodParams, mapType);
            dtoValue.put("logtime", toTimeStamp(timestamp));
            dtoValueStore.add(dtoValue);
            return true;
        }
        return false;
    }

    private long toTimeStamp(long prefix) {
        return toTimeStamp(String.valueOf(prefix));
    }

    private long toTimeStamp(String timeStr) {
        try {
            Date date = sdf.parse(timeStr.concat(ZERO_TIMESTAMP).substring(0, 17));
            return date.getTime();
        }
        catch (Exception ex) {
            logger.error("", ex);
            return 0L;
        }
    }

    @Override
    public void saveToDB() throws SQLException {
        insertLiveStream();
    }

    private void insertLiveStream() throws SQLException {
        try (Statement stmt = sqlConnection.createStatement()) {
            int batchLimit = Tools.SQL_BATCH_LIMIT;
            for (Map<String, Object> dtoValue : dtoValueStore) {
                String query = buildQuery(dtoValue);
                stmt.addBatch(query);
                batchLimit -= 1;
                if (batchLimit <= 0) {
                    stmt.executeBatch();
                    stmt.clearBatch();
                    batchLimit = Tools.SQL_BATCH_LIMIT;
                }
            }
            stmt.executeBatch();
            stmt.clearBatch();
        }
    }

    private String buildQuery(Map<String, Object> dtoValue) {
        StringBuilder aggColumn = new StringBuilder();
        StringBuilder aggValue = new StringBuilder();
        StringBuilder aggUpdate = new StringBuilder();
        boolean first = true;
        String updateFormat = "%s=VALUES(%s)";

        for (Map.Entry<String, String> entry : Constant.KEY_COL_NAME.entrySet()) {
            String logKey = entry.getKey();
            String column = entry.getValue();
            Object logValue = dtoValue.get(logKey);

            if (logValue != null) {
                if (first) {
                    first = false;
                }
                else {
                    aggColumn.append(",");
                    aggValue.append(",");
                    aggUpdate.append(",");
                }

                aggColumn.append(column);
                aggUpdate.append(format(updateFormat, column, column));

                if (Constant.STR_TYPE_COL.contains(column)) {
                    String value = logValue.toString().replace("\"", "\\\"");
                    aggValue.append("\"");
                    aggValue.append(value);
                    aggValue.append("\"");
                }
                else {
                    aggValue.append(logValue);
                }
            }
        }

        if (aggValue.length() <= 0 || aggColumn.length() <= 0) {
            return null;
        }

        String query = String.format(QUERY_TEMPLATE, aggColumn, aggValue, aggUpdate);
        return query;
    }

    @Override
    public void recalculate(long startTime, long endTime) throws SQLException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void deleteFromDB(long startTime, long endTime) throws SQLException {
        long startTimestamp = toTimeStamp(startTime);
        long endTimestamp = toTimeStamp(endTime);

        try (PreparedStatement prepStmt = sqlConnection.prepareStatement(DELETE_LIVE_STREAM)) {
            prepStmt.setLong(1, startTimestamp);
            prepStmt.setLong(2, endTimestamp);
            prepStmt.execute();
        }
    }

    @Override
    public void close() throws IOException {
        clear();
    }

    private static class Constant {

        private static final List<String> STR_TYPE_COL = Arrays.asList("country", "chatserverip",
                "streamserverip", "tags", "viewerserverip", "profileimage", "title", "streamid", "name");

        private static final Map<String, String> KEY_COL_NAME = new HashMap<>();

        static {
            KEY_COL_NAME.put("cnty", "country");
            KEY_COL_NAME.put("chatport", "chatport");
            KEY_COL_NAME.put("chatServerIp", "chatserverip");
            KEY_COL_NAME.put("streamIp", "streamserverip");
            KEY_COL_NAME.put("streamPort", "streamport");
            KEY_COL_NAME.put("catList", "tags");
            KEY_COL_NAME.put("isVrfid", "userstatus");
            KEY_COL_NAME.put("vwrIp", "viewerserverip");
            KEY_COL_NAME.put("vwrPort", "viewerserverport");
            KEY_COL_NAME.put("dvcc", "devicecategory");
            KEY_COL_NAME.put("endTm", "endtime");
            KEY_COL_NAME.put("giftOn", "gifton");
            KEY_COL_NAME.put("type", "isfeatured");
            KEY_COL_NAME.put("lat", "latitude");
            KEY_COL_NAME.put("lc", "likecount");
            KEY_COL_NAME.put("lon", "longitude");
            KEY_COL_NAME.put("fn", "name");
            KEY_COL_NAME.put("prIm", "profileimage");
            KEY_COL_NAME.put("uId", "ringid");
            KEY_COL_NAME.put("stTm", "starttime");
            KEY_COL_NAME.put("ttl", "title");
            KEY_COL_NAME.put("utId", "userid");
            KEY_COL_NAME.put("streamId", "streamid");
            KEY_COL_NAME.put("vwc", "viewcount");
            KEY_COL_NAME.put("coin", "startcoin");
            KEY_COL_NAME.put("endCoin", "endcoin");
            KEY_COL_NAME.put("utTyp", "userType");
            KEY_COL_NAME.put("rmid", "roomid");
            KEY_COL_NAME.put("device", "device");
            KEY_COL_NAME.put("tariff", "tariff");
            KEY_COL_NAME.put("ftrdScr", "featuredScore");
            KEY_COL_NAME.put("mType", "streamMediaType");
            KEY_COL_NAME.put("logtime", "logtime");
        }
    }
}
