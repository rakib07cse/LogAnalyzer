package log.analyzers;

import java.io.Closeable;
import java.sql.SQLException;

/**
 *
 * @author sazzad
 */
public interface Analyzer extends Closeable {

    public void clear();

    public boolean processLog(String log);

    public void saveToDB() throws SQLException;

    public void recalculate(long startTime, long endTime) throws SQLException;

    public void deleteFromDB(long startTime, long endTime) throws SQLException;
}
