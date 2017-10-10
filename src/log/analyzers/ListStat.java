package log.analyzers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author sazzad
 */
public class ListStat implements Analyzer {

    private final Pattern pattern = Pattern.compile("^\\d{17}\\s+INFO - R \\S+ (\\w+) - \\{.*\"(\\w+)\":\\[(.*)].*}$");

    private final TreeMap<String, LinkedHashMap<String, Long>> usage = new TreeMap<String, LinkedHashMap<String, Long>>();

    private static final String TOTAL_REQUEST = "total-request";
    private static final String ZERO = "zero";
    private static final String _1_10 = "1-10";
    private static final String _11_50 = "11-50";
    private static final String _51_100 = "51-100";
    private static final String TOO_MANY = "too-many";
    private static final String SUM = "sum";
    private static final String MAX = "maximum";
    private static final String AVG = "average";

    public ListStat() {
    }

    @Override
    public void clear() {
        this.usage.clear();
    }

    @Override
    public void close() {
        clear();
    }

    @Override
    public boolean processLog(String log) {
        Matcher m = pattern.matcher(log);
        if (m.matches()) {
            String method = m.group(1);
            String key = m.group(2);
            String fullkey = method + " (" + key + ")";

            String listStr = m.group(3).trim();
            int count = 0;
            if (!listStr.isEmpty()) {
                count = listStr.split(",").length;
            }

            if (!usage.containsKey(fullkey)) {
                usage.put(fullkey, new LinkedHashMap<String, Long>());
            }
            updateUsage(usage.get(fullkey), count);

            return true;
        }
        return false;
    }

    private void updateUsage(Map<String, Long> map, int count) {
        assert count >= 0;
        if (map.isEmpty()) {
            map.put(TOTAL_REQUEST, 0L);
            map.put(ZERO, 0L);
            map.put(_1_10, 0L);
            map.put(_11_50, 0L);
            map.put(_51_100, 0L);
            map.put(TOO_MANY, 0L);
            map.put(SUM, 0L);
            map.put(MAX, -1L);
        }

        map.put(TOTAL_REQUEST, map.get(TOTAL_REQUEST) + 1);
        if (count == 0L) map.put(ZERO, map.get(ZERO) + 1);
        else if (count <= 10) map.put(_1_10, map.get(_1_10) + 1);
        else if (count <= 50) map.put(_11_50, map.get(_11_50) + 1);
        else if (count <= 100) map.put(_51_100, map.get(_51_100) + 1);
        else map.put(TOO_MANY, map.get(TOO_MANY) + 1);
        map.put(SUM, map.get(SUM) + count);
        map.put(MAX, Math.max(map.get(MAX), count));
    }

    @Override
    public void saveToDB() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void recalculate(long startTime, long endTime) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void deleteFromDB(long startTime, long endTime) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
