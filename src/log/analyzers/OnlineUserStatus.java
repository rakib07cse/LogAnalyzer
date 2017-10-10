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
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import log.util.Tools;
import org.apache.log4j.Logger;

/**
 *
 * @author shihab
 */
public class OnlineUserStatus implements Analyzer {

    private static final Logger logger = Logger.getLogger(OnlineUserStatus.class);
    private static final Gson gson = new GsonBuilder().create();
    private final Type mapType = new TypeToken<Map<String, Object>>() {
    }.getType();
    private final Pattern pattern = Pattern.compile("^(\\d{17})\\s+INFO - R \\S+ userOnlineStatus - (.*)$");
    private final Map<Long, Map<String, Object>> userOnlineInfo = new HashMap<>();
    private final Connection sqlConnection;
    private static final String USER_INSERTION_SQL = "INSERT IGNORE INTO analytics_user_online_status (time, userid, status) VALUES (?, ?, ?) ";
    private static final String DELETE_ONLINE_USER_STATUS_COUNT = "DELETE FROM analytics_user_online_status WHERE time >= ? and time < ?";

    public OnlineUserStatus(Connection sqlConnection) {
        this.sqlConnection = sqlConnection;
    }

    @Override
    public void clear() {
        userOnlineInfo.clear();
    }

    @Override
    public boolean processLog(String log) {
        Matcher m = pattern.matcher(log);
        if (m.matches()) {
            long time = Long.parseLong(m.group(1));
            String paramValue = m.group(2);
            Map<String, Object> paramMap = stringToMap(paramValue);

            if (paramMap.isEmpty()) {
                return false;
            }
            userOnlineInfo.put(time, paramMap);
        }

        return true;
    }

    private Map<String, Object> stringToMap(String source) {
        Map<String, Object> target = new HashMap<>();
        try {
            target = gson.fromJson(source, mapType);
        } catch (Exception ex) {
            logger.error("", ex);
        }
        return target;
    }

    @Override
    public void saveToDB() throws SQLException {
        try (PreparedStatement prepStmt = sqlConnection.prepareStatement(USER_INSERTION_SQL)) {
            int batchLimit = Tools.SQL_BATCH_LIMIT;
            for (Map.Entry<Long, Map<String, Object>> childEntry : userOnlineInfo.entrySet()) {
                Long time = childEntry.getKey();
                Map<String, Object> param = childEntry.getValue();
                try {
                    long userid = gson.toJsonTree(param.get(Constant.USERID)).getAsLong();
                    long status = gson.toJsonTree(param.get(Constant.STATUS)).getAsLong();
                    prepStmt.setLong(1, time);
                    prepStmt.setLong(2, userid);
                    prepStmt.setLong(3, status);
                } catch (Exception ex) {
                    logger.error("", ex);
                    continue;
                }

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
        try (PreparedStatement deleStmt = sqlConnection.prepareCall(DELETE_ONLINE_USER_STATUS_COUNT)) {
            deleStmt.setLong(1, startTime);
            deleStmt.setLong(2, endTime);
            deleStmt.execute();
        }
    }

    @Override
    public void close() throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    private static class Constant {

        private static final String USERID = "userId";
        private static final String STATUS = "status";
    }
}
