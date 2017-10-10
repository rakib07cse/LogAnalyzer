package log.analyzers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import log.util.Tools;

/**
 *
 * @author sazzad
 */
public class ErrorMessageCount implements Analyzer {

    private final Pattern errorPattern = Pattern.compile("^(\\d{17})\\s+(FATAL|ERROR|WARN)\\s+(.*)$");
    private static final String requestIdPattern = " ?" + Tools.UUID_PATTERN;

    private final HashMap<MessageWithType, HashMap<Long, Long>> countMap
            = new HashMap<MessageWithType, HashMap<Long, Long>>();

    private static final String ERR_MSG_COUNT_SQL
            = "INSERT INTO analytics_error_message_count (type, hashcode, message, time, count) "
            + "VALUES (?, ?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE count = count + VALUES (count)";

    private static final String DELETE_ERROR_MESSAGE_COUNT = "DELETE FROM analytics_error_message_count WHERE time >= ? and time < ?";

    private final Connection sqlConnection;

    public ErrorMessageCount(Connection sqlConnection) {
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
        Matcher m = errorPattern.matcher(log);
        if (m.matches()) {
            Long time = Long.parseLong(m.group(1).substring(0, 10));
            String type = m.group(2);
            String text = m.group(3);
            text = text.replaceAll(requestIdPattern, "");
            MessageWithType message = new MessageWithType(type, text);

            HashMap<Long, Long> hm;
            if (countMap.containsKey(message)) {
                hm = countMap.get(message);
            } else {
                hm = new HashMap<Long, Long>();
                countMap.put(message, hm);
            }

            if (hm.containsKey(time)) {
                hm.put(time, hm.get(time) + 1L);
            } else {
                hm.put(time, 1L);
            }
            return true;
        }
        return false;
    }

    @Override
    public void saveToDB() throws SQLException {

        try (PreparedStatement prepStmt = sqlConnection.prepareStatement(ERR_MSG_COUNT_SQL)) {
            int batchLimit = Tools.SQL_BATCH_LIMIT;
            for (Map.Entry<MessageWithType, HashMap<Long, Long>> parentEntry : countMap.entrySet()) {
                MessageWithType message = parentEntry.getKey();

                for (Map.Entry<Long, Long> childEntry : parentEntry.getValue().entrySet()) {
                    Long time = childEntry.getKey();
                    Long count = childEntry.getValue();

                    String type = message.getType();
                    prepStmt.setString(1, type);

                    String text = message.getMessage();
                    int hashcode = text.hashCode();
                    prepStmt.setInt(2, hashcode);
                    prepStmt.setString(3, text);

                    prepStmt.setLong(4, time);
                    prepStmt.setLong(5, count);

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
        try (PreparedStatement deleStmt = sqlConnection.prepareCall(DELETE_ERROR_MESSAGE_COUNT)) {
            deleStmt.setLong(1, startTime);
            deleStmt.setLong(2, endTime);
            deleStmt.execute();
        }
    }

    private class MessageWithType extends Object {

        private final String type;
        private final String message;

        public MessageWithType(String type, String message) {
            this.type = type;
            this.message = message;
        }

        public String getType() {
            return type;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public int hashCode() {
            return this.message.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (null == obj) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }

            final MessageWithType other = (MessageWithType) obj;
            if (!Objects.equals(this.type, other.type)) {
                return false;
            }
            return Objects.equals(this.message, other.message);
        }
    }
}
