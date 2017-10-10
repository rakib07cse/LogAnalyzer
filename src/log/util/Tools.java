package log.util;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 *
 * @author sazzad
 */
public class Tools {

    public static final int SQL_BATCH_LIMIT = 300;

    public static final String UUID_PATTERN = "[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}";

    public static final String DIR_KEY = "dir";

    public static final String MYSQL_HOST_KEY = "mysql.host";
    public static final String MYSQL_PORT_KEY = "mysql.port";
    public static final String MYSQL_DB_KEY = "mysql.db";
    public static final String MYSQL_USER_KEY = "mysql.user";
    public static final String MYSQL_PASSWD_KEY = "mysql.passwd";

    public static final String CURRENT_NAME = "current";
    public static final String ARCHIVE_NAME = "archive";

    public static final String FEATURE_SEPARTOR = ",";

    public static final SimpleDateFormat archiveFolderNameFormat = new SimpleDateFormat("yyyy-MM");

    public static String getArchiveFolderName(long time) {
        return archiveFolderNameFormat.format(new Date(time));
    }

    public class SettingType {

        public static final String THRESHOLD_DAYS = "threshold_days";
        public static final String REVISIT_TIME = "revisit_time";
        public static final String REVISIT_FEATURES = "revisit_features";
    }

    public class FeatureType {

        public static final String ACTIVITY_COUNT = "ActivityCount";
        public static final String METHOD_COUNT = "MethodCount";
        public static final String MEDIA_COUNT = "MediaCount";
        public static final String LIVE_STREAM_HISTORY = "LiveStream";
        public static final String ONLINE_USER_STATUS = "OnlineUserStatus";
        public static final String ERROR_MESSAGE_COUNT = "ErrorMessageCount";
        public static final String LIVE_VIEWER_COUNT = "LiveViewerCount";
        public static final String USER_COUNT = "UserCount";

    }

    public static final List<String> archiveRevisitFeatures = Arrays.asList("MediaCount", "OnlineUserStatus", "LiveViewerCount", "ErrorMessageCount", "UserCount");
    public static final List<String> databaseRevisitFeatures = Arrays.asList("ActivityCount", "MethodCount");
}
