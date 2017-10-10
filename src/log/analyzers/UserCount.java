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

import static java.lang.String.format;

/**
 *
 * @author shihab
 */
public class UserCount implements Analyzer {

    private static final Logger logger = Logger.getLogger(UserCount.class);
    private static final Gson gson = new GsonBuilder().create();
    private final Type mapType = new TypeToken<Map<String, Object>>() {
    }.getType();
    private final Pattern pattern = Pattern.compile("^(\\d{17})\\s+INFO - R \\S+ (\\w+) - (.*)$");
    private final HashMap<Long, Set<Long>> userEntry = new HashMap<>();
    private final Map<Long, Long> userCountMap = new HashMap<>();
    private final String GET_USER_SQL = "SELECT userid FROM analytics_user_entry WHERE time = %d";
    private final String GET_USER_COUNT_SQL = "SELECT count FROM analytics_unique_user_count WHERE time = %d";
    private static final String USER_INSERTION_SQL
            = "INSERT IGNORE INTO analytics_user_entry (time, userid) VALUES (?, ?) ";

    private static final String DEL_USER_SQL = "DELETE FROM analytics_user_entry WHERE time < %d;";

    private static final String UPDATE_USER_COUNT_SQL = "INSERT INTO analytics_unique_user_count (time, count) "
            + "SELECT time, count(*) AS count FROM analytics_user_entry GROUP BY time "
            + "ON DUPLICATE KEY UPDATE count = count + VALUES(count);";

    private static final String INSERT_USER_COUNT_SQL = "INSERT INTO analytics_unique_user_count (time, count) VALUES (?, ?)"
            + " ON DUPLICATE KEY UPDATE count = VALUES(count)";

    private static final String DELETE_USER_SQL = "DELETE FROM analytics_user_entry WHERE time >= ? and time < ?";
    private static final String DELETE_USER_COUNT_SQL = "DELETE FROM analytics_unique_user_count WHERE time >= ? and time < ?";

    private final Connection sqlConnection;

    private static final int LOOKBACK_DAYS = 30;
    private final long lookbackTime;
    private long processingLogDay;
    private Set<Long> processedUserIds = new HashSet<>();

    public UserCount(Connection sqlConnection) {
        this.sqlConnection = sqlConnection;
        lookbackTime = getLookbackTimestamp();
    }

    @Override
    public void clear() {
        userEntry.clear();
        userCountMap.clear();
        processedUserIds.clear();
        processingLogDay = 0;
    }

    @Override
    public boolean processLog(String log) {
        Matcher m = pattern.matcher(log);
        if (m.matches()) {
            long time = Long.parseLong(m.group(1).substring(0, 8));

            if (processingLogDay != time) {
                processingLogDay = time;
                processedUserIds = getUserIds(processingLogDay);
                long userCount = getUserCount(processingLogDay);
                userCountMap.put(processingLogDay, userCount);
            }

//            if(time < lookbackTime) {
//                return success;
//            }
            String method = m.group(2);
            String paramValue = m.group(3);
            Long userId = getUserId(paramValue, method);

            if (userId == null || processedUserIds.contains(userId)) {
                return false;
            }

            buildUserEntry(time, userId);
            processedUserIds.add(userId);

            if (userCountMap.containsKey(time)) {
                userCountMap.put(time, userCountMap.get(time) + 1L);
            } else {
                userCountMap.put(time, 1L);
            }

            return true;
        }
        return false;
    }

    private void buildUserEntry(long time, long userid) {
        if (userEntry.containsKey(time)) {
            userEntry.get(time).add(userid);
        } else {
            Set<Long> list = new HashSet<>();
            list.add(userid);
            userEntry.put(time, list);
        }
    }

    @Override
    public void saveToDB() throws SQLException {
        insertUserEntry();
//        delPreviousUser();
        updateUserCount();
    }

    private void insertUserEntry() throws SQLException {

        try (PreparedStatement prepStmt = sqlConnection.prepareStatement(USER_INSERTION_SQL)) {
            int batchLimit = Tools.SQL_BATCH_LIMIT;
            for (Map.Entry<Long, Set<Long>> childEntry : userEntry.entrySet()) {
                Long time = childEntry.getKey();
                for (long userid : childEntry.getValue()) {
                    prepStmt.setLong(1, time);
                    prepStmt.setLong(2, userid);
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

    private void delPreviousUser() throws SQLException {
        String sql = format(DEL_USER_SQL, lookbackTime);
        try (PreparedStatement prepStmt = sqlConnection.prepareStatement(sql)) {
            prepStmt.addBatch();
            prepStmt.executeBatch();
        }
    }

    private void updateUserCount() throws SQLException {

        try (PreparedStatement prepStmt = sqlConnection.prepareStatement(INSERT_USER_COUNT_SQL)) {
            int batchLimit = Tools.SQL_BATCH_LIMIT;
            for (Map.Entry<Long, Long> entry : userCountMap.entrySet()) {
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
        try (PreparedStatement deleStmt = sqlConnection.prepareCall(DELETE_USER_SQL)) {
            deleStmt.setLong(1, startTime);
            deleStmt.setLong(2, endTime);
            deleStmt.execute();
        }
        try (PreparedStatement deleStmt2 = sqlConnection.prepareCall(DELETE_USER_COUNT_SQL)) {
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
//        cal.setTime(new Date());
        cal.add(Calendar.DATE, -LOOKBACK_DAYS);
        return Long.parseLong(sdf.format(cal.getTime()));
    }

    private Set<Long> getUserIds(long time) {
        String sql = String.format(GET_USER_SQL, time);
        Set<Long> userIds = new HashSet<>();
        try (PreparedStatement prepStmt = sqlConnection.prepareStatement(sql)) {
            ResultSet rs = prepStmt.executeQuery();
            while (rs.next()) {
                long userid = rs.getLong("userid");
                userIds.add(userid);
            }
        } catch (Exception ex) {
            logger.error("", ex);
        }
        return userIds;
    }

    private Long getUserId(String paramValue, String method) {
        String userIdKey = Constant.METHOD_USER_KEY.get(method);
        Long userId = null;
        try {
            if (userIdKey != null) {
                Map<String, Object> paramValueMap = gson.fromJson(paramValue, mapType);
                userId = gson.toJsonTree(paramValueMap.get(userIdKey)).getAsLong();
                return userId;
            }
        } catch (Exception ex) {
            logger.error("", ex);
        }
        return userId;
    }

    private long getUserCount(long time) {
        String sql = String.format(GET_USER_COUNT_SQL, time);
        try (PreparedStatement prepStmt = sqlConnection.prepareStatement(sql)) {
            ResultSet rs = prepStmt.executeQuery(sql);
            if (rs.next()) {
                return rs.getLong("count");
            }
        } catch (Exception ex) {
            logger.error("", ex);
        }
        return 0L;
    }

    private static class Constant {

        private static final Map<String, String> METHOD_USER_KEY = new HashMap<>();

        static {
            METHOD_USER_KEY.put("getMissedCallList", "calleeId");
            METHOD_USER_KEY.put("userOnlineStatus", "userId");
        }
    }
}
