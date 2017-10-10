package log.analyzers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import log.util.Tools;

/**
 *
 * @author shihab
 */
public class ActivityCount implements Analyzer {

    private final Map<String, Map<Long, Long>> countMap = new HashMap<String, Map<Long, Long>>();

    private final Pattern pattern = Pattern.compile("^(\\d{17})\\s+INFO - R \\S+ (\\w+) .*$");

    private final String ACTIVITY_METHOD_SQL = "SELECT activity, method FROM analytics_activity_method_map";

    private final Map<String, Set<String>> methodActivityMap = new HashMap<String, Set<String>>();
    private final Map<String, Set<String>> activityMethodMap = new HashMap<String, Set<String>>();

    private final String ACTIVITY_COUNT_SQL = "INSERT INTO analytics_activity_count (activity, time, count) VALUES (?,?,?)"
            + " ON DUPLICATE KEY UPDATE count = count + VALUES(count)";

    private final String DELETE_ACTIVITY_COUNT = "DELETE FROM analytics_activity_count WHERE time >= ? and time < ? ";

    private final String INSERT_ACTIVITY_COUNT = "INSERT INTO analytics_activity_count (activity, time, count) "
            + "SELECT '%s', time, SUM(count) AS count FROM analytics_method_count "
            + "WHERE method IN %s AND time >= ? AND time < ? GROUP BY time";

    private final Connection sqlConnection;

    public ActivityCount(Connection sqlConnection) throws SQLException {
        this.sqlConnection = sqlConnection;
        initActivity();
    }

    @Override
    public void clear() {
        countMap.clear();
    }

    @Override
    public void close() {
        clear();
    }

    private void initActivity() throws SQLException {

        try (PreparedStatement statement = sqlConnection.prepareStatement(ACTIVITY_METHOD_SQL)) {
            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                String activity = rs.getString("activity");
                String method = rs.getString("method");
                buildActivity(activity, method);
                buildActivityMethodMap(activity, method);
            }
        }
    }

    private void buildActivity(String activity, String method) {
        if (methodActivityMap.containsKey(method)) {
            methodActivityMap.get(method).add(activity);
        }
        else {
            Set<String> list = new HashSet<String>();
            list.add(activity);
            methodActivityMap.put(method, list);
        }
    }

    private void buildActivityMethodMap(String activity, String method) {

        if (activityMethodMap.containsKey(activity)) {
            activityMethodMap.get(activity).add(method);
        }
        else {
            Set<String> list = new HashSet<String>();
            list.add(method);
            activityMethodMap.put(activity, list);
        }
    }

    @Override
    public boolean processLog(String log) {
        Matcher matcher = pattern.matcher(log);
        if (matcher.matches()) {
            Long time = Long.parseLong(matcher.group(1).substring(0, 10));
            String method = matcher.group(2);
            if (!methodActivityMap.containsKey(method)) {
                return false;
            }

            Collection<String> activities = methodActivityMap.get(method);
            updateCounter(activities, time);

            return true;
        }
        return false;
    }

    private void updateCounter(Collection<String> activities, Long time) {
        Map<Long, Long> childMap;
        for (String activity : activities) {
            if (countMap.containsKey(activity)) {
                childMap = countMap.get(activity);
            }
            else {
                childMap = new HashMap<Long, Long>();
                countMap.put(activity, childMap);
            }

            if (childMap.containsKey(time)) {
                childMap.put(time, childMap.get(time) + 1L);
            }
            else {
                childMap.put(time, 1L);
            }
        }
    }

    @Override
    public void saveToDB() throws SQLException {

        try (PreparedStatement prepStmt = sqlConnection.prepareStatement(ACTIVITY_COUNT_SQL)) {
            int batchLimit = Tools.SQL_BATCH_LIMIT;
            for (Map.Entry<String, Map<Long, Long>> parentEntry : countMap.entrySet()) {
                String activity = parentEntry.getKey();
                Map<Long, Long> childEntry = parentEntry.getValue();
                for (Map.Entry<Long, Long> entry : childEntry.entrySet()) {
                    Long time = entry.getKey();
                    Long count = entry.getValue();

                    prepStmt.setString(1, activity);
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
    public void recalculate(long startTime, long endTime) throws SQLException {

        for (Map.Entry<String, Set<String>> entry : activityMethodMap.entrySet()) {
            String activity = entry.getKey();
            Set<String> methods = entry.getValue();
            String checkINPart = getINCheckingPart(methods);
            String stmt = String.format(INSERT_ACTIVITY_COUNT, activity, checkINPart);

            try (PreparedStatement insertUpdateStmt = sqlConnection.prepareStatement(stmt)) {
                insertUpdateStmt.setLong(1, startTime);
                insertUpdateStmt.setLong(2, endTime);
                insertUpdateStmt.execute();
                insertUpdateStmt.clearParameters();
            }
        }
    }

    @Override
    public void deleteFromDB(long startTime, long endTime) throws SQLException {
        try (PreparedStatement delStmt = sqlConnection.prepareStatement(DELETE_ACTIVITY_COUNT)) {
            delStmt.setLong(1, startTime);
            delStmt.setLong(2, endTime);
            delStmt.execute();
            delStmt.clearParameters();
        }
    }

    private String getINCheckingPart(Set<String> activities) {
        StringBuilder sb = new StringBuilder("(");
        if (!activities.isEmpty()) {
            boolean first = true;
            for (String activity : activities) {
                if (!first) {
                    sb.append(", ");
                }
                sb.append("\"").append(activity).append("\"");
                first = false;
            }
        }
        sb.append(")");
        return sb.toString();
    }
}
