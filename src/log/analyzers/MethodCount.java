package log.analyzers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import log.util.Tools;

/**
 *
 * @author sazzad
 */
public class MethodCount implements Analyzer {

    private final Pattern pattern = Pattern.compile("^(\\d{17})\\s+INFO - R \\S+ (\\w+) .*$");

    private final HashMap<String, HashMap<Long, Long>> countMap = new HashMap<>();

    private static final String METHOD_COUNT_SQL
            = "INSERT INTO analytics_method_count (method, time, count) VALUES (?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE count = count + VALUES (count)";
    
    private final String DELETE_METHOD_COUNT = "DELETE FROM analytics_method_count WHERE time >= ? and time < ?";
    private final Connection sqlConnection;

    public MethodCount(Connection sqlConnection) throws SQLException {
        this.sqlConnection = sqlConnection;
    }

    @Override
    public void clear() {
        this.countMap.clear();
    }

    @Override
    public void close() {
        clear();
    }

    @Override
    public boolean processLog(String log) {
        Matcher m = pattern.matcher(log);
        if (m.matches()) {
            Long time = Long.parseLong(m.group(1).substring(0, 10));
            String method = m.group(2);

            HashMap<Long, Long> hm;
            if (countMap.containsKey(method)) {
                hm = countMap.get(method);
            }
            else {
                hm = new HashMap<Long, Long>();
                countMap.put(method, hm);
            }

            if (hm.containsKey(time)) {
                hm.put(time, hm.get(time) + 1L);
            }
            else {
                hm.put(time, 1L);
            }

            return true;
        }
        return false;
    }

    @Override
    public void saveToDB() throws SQLException {

        try (PreparedStatement prepStmt = sqlConnection.prepareStatement(METHOD_COUNT_SQL)) {
            for (Map.Entry<String, HashMap<Long, Long>> parentEntry : countMap.entrySet()) {
                int batchLimit = Tools.SQL_BATCH_LIMIT;
                String method = parentEntry.getKey();

                for (Map.Entry<Long, Long> childEntry : parentEntry.getValue().entrySet()) {
                    Long time = childEntry.getKey();
                    Long count = childEntry.getValue();

                    prepStmt.setString(1, method);
                    prepStmt.setLong(2, time);
                    prepStmt.setLong(3, count);
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
    }

    @Override
    public void recalculate(long startTime, long endTime) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void deleteFromDB(long startTime, long endTime) throws SQLException {
        try (PreparedStatement deleStmt = sqlConnection.prepareCall(DELETE_METHOD_COUNT)) {
            deleStmt.setLong(1, startTime);
            deleStmt.setLong(2, endTime);
            deleStmt.execute();
        }
    }
}
