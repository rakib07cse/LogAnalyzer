package log.analyzers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
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
import org.ringid.newsfeeds.CassAlbumDTO;
import org.ringid.newsfeeds.FeedDTO;
import org.ringid.utilities.AppConstants;

/**
 *
 * @author sazzad
 */
public class MediaCount implements Analyzer {

    private static final Logger logger = Logger.getLogger(MediaCount.class);

    private static final String IMAGE = "IMAGE";
    private static final String AUDIO = "AUDIO";
    private static final String VIDEO = "VIDEO";

    private static final Gson gson = new GsonBuilder().serializeNulls().create();

    private final Pattern pattern = Pattern.compile("^(\\d{17})\\s+INFO - R \\S+ (\\w+) - (.*)$");

    private final Type mapType = new TypeToken<Map<String, Object>>() {
    }.getType();

    private final Map<String, Map<Long, Long>> countMap = new HashMap<String, Map<Long, Long>>();

    private static final String MEDIA_COUNT_SQL
            = "INSERT INTO analytics_media_count (type, time, count) VALUES (?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE count = count + VALUES (count)";

    private final String DELETE_MEDIA_COUNT = "DELETE FROM analytics_media_count WHERE time >= ? and time < ?";

    private final Connection sqlConnection;

    public MediaCount(Connection sqlConnection) throws SQLException {
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

            String type = null;
            int count = 0;

            if (method.equalsIgnoreCase("addStatus")) {
                try {
                    Map<String, Object> map = gson.fromJson(m.group(3), mapType);
                    Object feedDtoValue = map.get("feedDTO");
                    FeedDTO dto = gson.fromJson(gson.toJsonTree(feedDtoValue), FeedDTO.class);

                    CassAlbumDTO albumDTO = dto.getAlbumDTO();
                    if (null != albumDTO) {
                        switch (dto.getContentType()) {

                        case AppConstants.SINGLE_IMAGE:
                        case AppConstants.SINGLE_IMAGE_WITH_ALBUM:
                        case AppConstants.MULTIPLE_IMAGE_WITH_ALBUM:
                            if (null != albumDTO.getImgDTOs()) {
                                type = IMAGE;
                                count = albumDTO.getImgDTOs().size();
                            }
                            break;

                        case AppConstants.SINGLE_AUDIO:
                        case AppConstants.SINGLE_AUDIO_WITH_ALBUM:
                        case AppConstants.MULTIPLE_AUDIO_WITH_ALBUM:
                            if (null != albumDTO.getMultiMediaDTOs()) {
                                type = AUDIO;
                                count = albumDTO.getMultiMediaDTOs().size();
                            }
                            break;

                        case AppConstants.SINGLE_VIDEO:
                        case AppConstants.SINGLE_VIDEO_WITH_ALBUM:
                        case AppConstants.MULTIPLE_VIDEO_WITH_ALBUM:
                            if (null != albumDTO.getMultiMediaDTOs()) {
                                type = VIDEO;
                                count = albumDTO.getMultiMediaDTOs().size();
                            }
                            break;
                        }
                    }
                }
                catch (Exception ex) {
                    logger.error("", ex);
                }
            }
            else if (method.equalsIgnoreCase("addProfileOrCoverImage")) {
                type = IMAGE;
                count = 1;
            }

            if (null != type && 0 < count) {
                Map<Long, Long> hm;
                if (countMap.containsKey(type)) {
                    hm = countMap.get(type);
                }
                else {
                    hm = new HashMap<Long, Long>();
                    countMap.put(type, hm);
                }

                if (hm.containsKey(time)) {
                    hm.put(time, hm.get(time) + count);
                }
                else {
                    hm.put(time, (long) count);
                }

                return true;
            }
        }
        return false;
    }

    @Override
    public void saveToDB() throws SQLException {

        try (PreparedStatement prepStmt = sqlConnection.prepareStatement(MEDIA_COUNT_SQL)) {
            int batchLimit = Tools.SQL_BATCH_LIMIT;
            for (Map.Entry<String, Map<Long, Long>> parentEntry : countMap.entrySet()) {
                String type = parentEntry.getKey();
                for (Map.Entry<Long, Long> childEntry : parentEntry.getValue().entrySet()) {
                    Long time = childEntry.getKey();
                    Long count = childEntry.getValue();

                    prepStmt.setString(1, type);
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
        try (PreparedStatement deleStmt = sqlConnection.prepareCall(DELETE_MEDIA_COUNT)) {
            deleStmt.setLong(1, startTime);
            deleStmt.setLong(2, endTime);
            deleStmt.execute();
        }
    }

    Map getCountMap() {
        return countMap;
    }
}
