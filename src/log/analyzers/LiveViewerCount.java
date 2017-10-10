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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import log.util.Tools;
import org.apache.log4j.Logger;

/**
 *
 * @author shihab
 */
public class LiveViewerCount implements Analyzer {

    private static final Logger logger = Logger.getLogger(LiveViewerCount.class);
    private static final Gson gson = new GsonBuilder().create();
    private final Type mapType = new TypeToken<Map<String, Object>>() {
    }.getType();
    private final Pattern pattern = Pattern.compile("^(\\d{17})\\s+INFO - R \\S+ updateStreamViewCount - (.*)$");
    private final HashMap<Long, Set<Long>> viewerEntry = new HashMap<>();
    private final Map<Long, Long> viewerCountMap = new HashMap<>();

    private final String GET_VIEWER_SQL = "SELECT viewerid FROM analytics_live_viewer_entry WHERE time = %d";
    private final String GET_VIEWER_COUNT_SQL = "SELECT count FROM analytics_unique_live_viewer_count WHERE time = %d";

    private static final String VIEWER_INSERTION_SQL
            = "INSERT IGNORE INTO analytics_live_viewer_entry (time, viewerid) VALUES (?, ?) ";

    private static final String INSERT_VIEWER_COUNT_SQL = "INSERT INTO analytics_unique_live_viewer_count (time, count) VALUES (?, ?)"
            + " ON DUPLICATE KEY UPDATE count = VALUES (count)";

    private static final String DELETE_VIEWER_SQL = "DELETE FROM analytics_live_viewer_entry WHERE time >= ? and time < ?";
    private static final String DELETE_VIEWER_COUNT_SQL = "DELETE FROM analytics_unique_live_viewer_count WHERE time >= ? and time < ?";

    private final Connection sqlConnection;

    private static final int LOOKBACK_DAYS = 30;
    private final long lookbackTime;
    private long latestLogTime;
    private Set<Long> viewerids = new HashSet<>();

    public LiveViewerCount(Connection sqlConnection) {
        this.sqlConnection = sqlConnection;
        lookbackTime = getLookbackTimestamp();
    }

    @Override
    public void clear() {
        viewerEntry.clear();
        viewerCountMap.clear();
        viewerids.clear();
        latestLogTime = 0;
    }

    @Override
    public boolean processLog(String log) {
        Matcher m = pattern.matcher(log);
        boolean success = false;
        if (m.matches()) {
            long time = Long.parseLong(m.group(1).substring(0, 8));
            String paramValue = m.group(2);
            Long viewerId = getViewerId(paramValue);

            if (viewerId == null) {
                return success;
            }

            if (latestLogTime != time) {
                latestLogTime = time;
                viewerids = getViewerIds(latestLogTime);
                long viewerCount = getViewerCount(latestLogTime);
                viewerCountMap.put(latestLogTime, viewerCount);
            }

            if (viewerids.contains(viewerId)) {
                return success;
            }

            buildViewerEntry(time, viewerId);
            viewerids.add(viewerId);
            updateCount(time, 1);
            return success;
        }
        return success;
    }

    private void buildViewerEntry(long time, long viewerid) {
        if (viewerEntry.containsKey(time)) {
            viewerEntry.get(time).add(viewerid);
        } else {
            Set<Long> list = new HashSet<>();
            list.add(viewerid);
            viewerEntry.put(time, list);
        }
    }

    @Override
    public void saveToDB() throws SQLException {
        insertViewerEntry();
        updateViewerCount();
    }

    private void insertViewerEntry() throws SQLException {

        try (PreparedStatement prepStmt = sqlConnection.prepareStatement(VIEWER_INSERTION_SQL)) {
            int batchLimit = Tools.SQL_BATCH_LIMIT;
            for (Map.Entry<Long, Set<Long>> childEntry : viewerEntry.entrySet()) {
                Long time = childEntry.getKey();
                for (long viewerId : childEntry.getValue()) {
                    prepStmt.setLong(1, time);
                    prepStmt.setLong(2, viewerId);
                    prepStmt.addBatch();
                    prepStmt.clearParameters();
                    batchLimit -= 1;

                    if (batchLimit <= 0) {
                        prepStmt.executeBatch();
                        prepStmt.clearBatch();
                        batchLimit = Tools.SQL_BATCH_LIMIT;
                    }
                }
            }

            prepStmt.executeBatch();
            prepStmt.clearBatch();
        }
    }

    private void updateViewerCount() throws SQLException {

        try (PreparedStatement prepStmt = sqlConnection.prepareStatement(INSERT_VIEWER_COUNT_SQL)) {
            int batchLimit = Tools.SQL_BATCH_LIMIT;
            for (Map.Entry<Long, Long> entry : viewerCountMap.entrySet()) {
                long time = entry.getKey();
                long count = entry.getValue();
                prepStmt.setLong(1, time);
                prepStmt.setLong(2, count);
                prepStmt.addBatch();
                prepStmt.clearParameters();

                batchLimit -= 1;
                if (batchLimit <= 0) {
                    prepStmt.executeBatch();
                    prepStmt.clearBatch();
                    batchLimit = Tools.SQL_BATCH_LIMIT;
                }
            }
            prepStmt.executeBatch();
            prepStmt.clearBatch();
        }
    }

    @Override
    public void recalculate(long startTime, long endTime) throws SQLException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void deleteFromDB(long startTime, long endTime) throws SQLException {
        try (PreparedStatement deleStmt = sqlConnection.prepareCall(DELETE_VIEWER_SQL)) {
            deleStmt.setLong(1, startTime);
            deleStmt.setLong(2, endTime);
            deleStmt.execute();
        }
        try (PreparedStatement deleStmt2 = sqlConnection.prepareCall(DELETE_VIEWER_COUNT_SQL)) {
            deleStmt2.setLong(1, startTime);
            deleStmt2.setLong(2, endTime);
            deleStmt2.execute();
        }
    }

    @Override
    public void close() throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    private long getLookbackTimestamp() {
        DateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, -LOOKBACK_DAYS);
        return Long.parseLong(sdf.format(cal.getTime()));
    }

    private Set<Long> getViewerIds(long time) {
        String sql = String.format(GET_VIEWER_SQL, time);
        Set<Long> viewerIds = new HashSet<>();
        try (PreparedStatement prepStmt = sqlConnection.prepareStatement(sql)) {
            ResultSet rs = prepStmt.executeQuery();
            while (rs.next()) {
                long viewerid = rs.getLong("viewerid");
                viewerIds.add(viewerid);
            }
        } catch (Exception ex) {
            logger.error("", ex);
        }
        return viewerIds;
    }

    private Long getViewerId(String paramValue) {
        Long viewerId = null;
        try {
            Map<String, Object> paramValueMap = gson.fromJson(paramValue, mapType);
            if (paramValueMap.containsKey("ssnUserId")) {
                viewerId = gson.toJsonTree(paramValueMap.get("ssnUserId")).getAsLong();
            } else if (paramValueMap.containsKey("sessionUserId")) {
                viewerId = gson.toJsonTree(paramValueMap.get("sessionUserId")).getAsLong();
            }

            return viewerId;
        } catch (Exception ex) {
            logger.error("", ex);
        }
        return viewerId;
    }

    private long getViewerCount(long time) {
        String sql = String.format(GET_VIEWER_COUNT_SQL, time);
        long viewerCount = 0;
        try (PreparedStatement prepStmt = sqlConnection.prepareStatement(sql)) {
            ResultSet rs = prepStmt.executeQuery(sql);
            while (rs.next()) {
                viewerCount = rs.getLong("count");
            }
        } catch (Exception ex) {
            logger.error("", ex);
        }
        return viewerCount;
    }

    private void updateCount(long time, long value) {
        if (viewerCountMap.containsKey(time)) {
            viewerCountMap.put(time, viewerCountMap.get(time) + value);
        } else {
            viewerCountMap.put(time, value);
        }
    }
}
