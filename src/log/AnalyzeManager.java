package log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import log.analyzers.*;
import log.util.Settings;
import log.util.Tools;
import org.apache.log4j.Logger;

/**
 *
 * @author sazzad
 */
public class AnalyzeManager implements AutoCloseable {

    private static final Logger logger = Logger.getLogger(AnalyzeManager.class);

    private String currentPath;
    private String archivePath;

    private final Connection sqlConnection;

    private final Pattern logPattern = Pattern.compile("^(\\d{17})\\s+([A-Z]{4,5})\\s+");
    private final String DATE_FORMAT = "yyyyMMddHH";

    public AnalyzeManager(String configFilepath) throws Exception {
        Properties properties = loadProperties(configFilepath);
        getDir(properties);

        sqlConnection = createSqlConnection(properties);
        sqlConnection.setAutoCommit(false);
        sqlConnection.rollback();
    }

    public void manageAnalyzer() throws Exception {
        processRevisitFeatures();
        processCurrent();
    }

    private void processCurrent() throws Exception {
        Collection<Analyzer> allAnalyzers = new ArrayList<Analyzer>() {
            {
                add(new MethodCount(sqlConnection));
                add(new ActivityCount(sqlConnection));
                //.add(new ListStat());
                add(new MediaCount(sqlConnection));
                add(new ErrorMessageCount(sqlConnection));
                add(new LiveStreamHistory(sqlConnection));
                add(new UserCount(sqlConnection));
                add(new LiveViewerCount(sqlConnection));
                add(new OnlineUserStatus(sqlConnection));
            }
        };

        File currentDir = new File(this.currentPath);
        Collection<File> files = getLogFiles(currentDir);

        for (File file : files) {
            processFile(allAnalyzers, file);
        }
    }

    private void getDir(Properties properties) throws Exception {
        if (!properties.containsKey(Tools.DIR_KEY)) {
            throw new IllegalArgumentException("\"" + Tools.DIR_KEY + "\" is not found in configuration file.");
        }

        String path = properties.getProperty(Tools.DIR_KEY);
        File folder = new File(path);
        if (!folder.isDirectory()) {
            throw new IllegalArgumentException("\"" + folder.getAbsolutePath() + "\" is not not a directory.");
        }
        if (!folder.canWrite()) {
            throw new IllegalArgumentException("The process does not have permission to write in \"" + folder.getAbsolutePath() + "\" directory.");
        }

        String dir = folder.getAbsolutePath();

        String currStr = null;
        for (File f : new File(dir).listFiles()) {
            if (f.getName().toLowerCase().equalsIgnoreCase(Tools.CURRENT_NAME)) {
                currStr = f.getName();
                break;
            }
        }
        if (null == currStr) {
            throw new IllegalArgumentException(String.format("\"%s\" does not contain a folder named \"%s\".", path, Tools.CURRENT_NAME));
        } else {
            File currentDir = new File(dir + "/" + currStr);
            if (!currentDir.isDirectory()) {
                throw new IllegalArgumentException(String.format("\"%s\" is not a directory.", currentDir.getAbsolutePath()));
            }
            if (!currentDir.canWrite()) {
                throw new IllegalArgumentException("The process does not have permission to write in \"" + currentDir.getAbsolutePath() + "\" directory.");
            }
            this.currentPath = currentDir.getAbsolutePath();
        }

        String archStr = null;
        for (File f : new File(dir).listFiles()) {
            if (f.getName().toLowerCase().equalsIgnoreCase(Tools.ARCHIVE_NAME)) {
                archStr = f.getName();
                break;
            }
        }
        if (null == archStr) {
            File archiveDir = new File(dir + "/" + Tools.ARCHIVE_NAME);
            archiveDir.mkdir();
            this.archivePath = archiveDir.getAbsolutePath();
        } else {
            File archiveDir = new File(dir + "/" + archStr);
            if (!archiveDir.isDirectory()) {
                throw new IllegalArgumentException(String.format("\"%s\" exists, but a directory.", archiveDir.getAbsolutePath()));
            } else if (!archiveDir.canWrite()) {
                throw new IllegalArgumentException("The process does not have permission to write in \"" + archiveDir.getAbsolutePath() + "\" directory.");
            }
            this.archivePath = archiveDir.getAbsolutePath();
        }
    }

    private Collection<File> getLogFiles(File dir) {
        long maxTime = -1L, maxCount = -1L;
        for (File file : dir.listFiles()) {
            try {
                if (!isValidFile(file)) {
                    continue;
                }

                String str = file.getName();
                if (!str.matches("\\d+-\\d+")) {
                    continue;
                }

                String[] arr = str.split("-");
                long time = Long.parseLong(arr[0]);
                long count = Long.parseLong(arr[1]);

                if (time > maxTime || (time == maxTime && count > maxCount)) {
                    maxTime = time;
                    maxCount = count;
                }
            } catch (Exception ex) {
                logger.error("", ex);
            }
        }

        ArrayList<File> files = new ArrayList<File>();
        for (File file : dir.listFiles()) {
            try {
                if (!isValidFile(file)) {
                    continue;
                }

                String str = file.getName();
                if (!str.matches("\\d+-\\d+")) {
                    continue;
                }

                String[] arr = str.split("-");
                long time = Long.parseLong(arr[0]);
                long count = Long.parseLong(arr[1]);

                if (time < maxTime || (time == maxTime && count < maxCount)) {
                    files.add(file);
                }
            } catch (Exception ex) {
                logger.error("", ex);
            }
        }
        return files;
    }

    private boolean isValidFile(File file) {
        return file.isFile() && file.canRead();
    }

    private void processFile(Collection<Analyzer> allAnalyzers, File file) {

        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {

            sqlConnection.rollback();
            clear(allAnalyzers);
            String line;
            long match = 0L, all = 0L;

            while ((line = br.readLine()) != null) {
                boolean success = processLog(allAnalyzers, line);
                if (success) {
                    ++match;
                }
                ++all;
            }

            saveToDB(allAnalyzers);
            moveFile(file);
            sqlConnection.commit();

            String report = "FILE REPORT:"
                    + " filename=" + file.getName()
                    + " lines_read=" + all
                    + " lines_matched=" + match
                    + ".";
            logger.info(report);
        } catch (Exception ex) {
            try {
                sqlConnection.rollback();
            } catch (SQLException sqlr) {
                //TODO: what to do when even rollback fails?
                logger.fatal("Database rollback failed.", sqlr);
            }
            String msg = String.format("Exception while processing file \"%s\".", file.getAbsolutePath());
            logger.error(msg, ex);
        }
    }

    private boolean processLog(Collection<Analyzer> allAnalyzers, String log) {
        boolean anySuccess = false;

        Matcher matcher = logPattern.matcher(log);
        if (matcher.find()) {
            String time = matcher.group(1);
            String type = matcher.group(2);

            for (Analyzer analyzer : allAnalyzers) {
                boolean success = analyzer.processLog(log);
                anySuccess = anySuccess || success;
            }
        }
        return anySuccess;
    }

    private void moveFile(File file) throws Exception {
        if (file.length() < 1L) {
            file.delete();
        }

        String newFolderName = Tools.getArchiveFolderName(file.lastModified());
        File newFolder = new File(this.archivePath + "/" + newFolderName);
        newFolder.mkdir();
        file.renameTo(new File(newFolder.getAbsolutePath() + "/" + file.getName()));
    }

    private void processRevisitFeatures() throws Exception {
        sqlConnection.rollback();
        Settings settings = new Settings(sqlConnection);
        Map<String, String> settingMap = settings.getSettingMap();

        String thresholdDaysStr = settingMap.get(Tools.SettingType.THRESHOLD_DAYS);
        int thresholdDays = (null == thresholdDaysStr) ? 0 : Integer.parseInt(thresholdDaysStr);

        String revisitTimeStr = settingMap.get(Tools.SettingType.REVISIT_TIME);
        String revisitFeatureStr = settingMap.get(Tools.SettingType.REVISIT_FEATURES);

        if (revisitTimeStr != null && revisitFeatureStr != null) {
            revisitTimeStr = (revisitTimeStr + "0000000000").substring(0, 10);
            long startTime = Long.parseLong(revisitTimeStr);
            long lookbackTime = getLookbackTime(revisitTimeStr, thresholdDays);
            long endTime = getCurrentDayEndTime();

            Collection<String> features = getFeatures(revisitFeatureStr);
            try {
                processArchiveReadFeatures(features, startTime, endTime, lookbackTime);
                processDatabaseReadFeatures(features, startTime, endTime);
                settings.delFeatureSetting(sqlConnection);
                sqlConnection.commit();
            } catch (Exception ex) {
                try {
                    sqlConnection.rollback();
                } catch (SQLException sqlr) {
                    //TODO: what to do when even rollback fails?
                    logger.fatal("Database rollback failed.", sqlr);
                }
                throw ex;
            }
        }
    }

    private long getLookbackTime(String revisitDate, int daysLookback) throws ParseException {
        DateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
        Date date = sdf.parse(revisitDate);
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.DATE, -daysLookback);
        return Long.parseLong(sdf.format(cal.getTime()));
    }

    private long getCurrentDayEndTime() {
        DateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DATE, 1);
        return Long.parseLong(sdf.format(cal.getTime()) + "00");
    }

    private Collection<String> getFeatures(String features) {
        Collection<String> list = new ArrayList<>();
        String[] array = features.split(Tools.FEATURE_SEPARTOR);
        for (String name : array) {
            list.add(name.trim());
        }
        return list;
    }

    private void processArchiveReadFeatures(Collection<String> allFeatures,
            long startTime, long endTime, long lookbackTime) throws Exception {
        Collection<Analyzer> archiveAnalyzers = new ArrayList<Analyzer>();
        for (String feature : allFeatures) {
            if (feature.equalsIgnoreCase(Tools.FeatureType.MEDIA_COUNT)) {
                archiveAnalyzers.add(new MediaCount(sqlConnection));
            } else if (feature.equalsIgnoreCase(Tools.FeatureType.LIVE_STREAM_HISTORY)) {
                archiveAnalyzers.add(new LiveStreamHistory(sqlConnection));
            } else if (feature.equalsIgnoreCase(Tools.FeatureType.METHOD_COUNT)) {
                archiveAnalyzers.add(new MethodCount(sqlConnection));
            } else if (feature.equalsIgnoreCase(Tools.FeatureType.ONLINE_USER_STATUS)) {
                archiveAnalyzers.add(new OnlineUserStatus(sqlConnection));
            } else if (feature.equalsIgnoreCase(Tools.FeatureType.ERROR_MESSAGE_COUNT)) {
                archiveAnalyzers.add(new ErrorMessageCount(sqlConnection));
            } else if (feature.equalsIgnoreCase(Tools.FeatureType.LIVE_VIEWER_COUNT)) {
                archiveAnalyzers.add(new LiveViewerCount(sqlConnection));
            } else if (feature.equalsIgnoreCase(Tools.FeatureType.USER_COUNT)) {
                archiveAnalyzers.add(new UserCount(sqlConnection));
            }
        }

        for (Analyzer analyzer : archiveAnalyzers) {
            analyzer.deleteFromDB(startTime, endTime);
        }

        processArchiveDir(archiveAnalyzers, startTime, endTime, lookbackTime);
    }

    private void processArchiveDir(Collection<Analyzer> analyzers,
            long startTime, long endTime, long lookbackTime) throws ParseException {
        String startTimeStr = Long.toString(startTime);
        List<File> archiveSubDir = getArchiveSubDirectories(lookbackTime);
        for (File logDir : archiveSubDir) {
            for (File file : logDir.listFiles()) {
                if (isProcessableArchiveFile(file, lookbackTime)) {
                    processArchiveFile(analyzers, file, startTimeStr);
                }
            }
        }
    }

    private void processArchiveFile(Collection<Analyzer> analyzers, File file, String startTime) {

        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {

            String line;
            long match = 0L, all = 0L;

            clear(analyzers);
            while ((line = br.readLine()) != null) {
                boolean success = processArchiveLog(analyzers, line, startTime);
                if (success) {
                    ++match;
                }
                ++all;
            }
            saveToDB(analyzers);

            String report = "FILE REPORT:"
                    + " filename=" + file.getName()
                    + " lines_read=" + all
                    + " lines_matched=" + match
                    + ".";
            logger.info(report);
        } catch (Exception ex) {
            String msg = String.format("Exception while processing file \"%s\".", file.getAbsolutePath());
            logger.error(msg, ex);
        }
    }

    private boolean processArchiveLog(Collection<Analyzer> analyzers, String log, String startTime) {
        boolean anySuccess = false;

        Matcher matcher = logPattern.matcher(log);
        if (matcher.find()) {
            String time = matcher.group(1);
            String type = matcher.group(2);

            if (startTime.compareTo(time) <= 0) {
                for (Analyzer analyzer : analyzers) {
                    boolean success = analyzer.processLog(log);
                    anySuccess = anySuccess || success;
                }
            }
        }
        return anySuccess;
    }

    private boolean isProcessableArchiveFile(File file, long lookbackTime) {

        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {

            String line;
            long timeInSeconds = Long.MIN_VALUE;

            while ((line = br.readLine()) != null) {
                Matcher matcher = logPattern.matcher(line);
                if (matcher.find()) {
                    timeInSeconds = Long.parseLong(matcher.group(1).substring(0, 10));
                    break;
                }
            }

            if (timeInSeconds >= lookbackTime) {
                return true;
            }
        } catch (Exception ex) {
            logger.error("Exception while reading file", ex);
        }
        return false;
    }

    private List<File> getArchiveSubDirectories(long startTime) throws ParseException {
        List<File> logDirectories = new ArrayList<>();
        List<String> folderNames = getFolderRenames(startTime);

        File archiveDir = new File(this.archivePath);
        for (File subChild : archiveDir.listFiles()) {
            if (folderNames.contains(subChild.getName())) {
                logDirectories.add(subChild);
            }
        }
        return logDirectories;
    }

    private List<String> getFolderRenames(long startTime) throws ParseException {
        List<String> yearMonths = new ArrayList<>();
        long currentTime = getCurrentDayEndTime();
        long endTime = getNextMonthTime(currentTime);
        DateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
        while (startTime <= endTime) {
            try {
                Date date = sdf.parse(Long.toString(startTime));
                yearMonths.add(Tools.archiveFolderNameFormat.format(date));
                startTime = getNextMonthTime(startTime);
            } catch (Exception ex) {
                logger.error(" The time can not be parsed : ", ex);
            }
        }
        return yearMonths;
    }

    private long getNextMonthTime(long time) throws ParseException {
        DateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
        Date date = sdf.parse(Long.toString(time));
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.MONTH, 1);
        return Long.parseLong(sdf.format(cal.getTime()));
    }

    private void processDatabaseReadFeatures(Collection<String> allFeatures,
            long startTime, long endTime) throws SQLException {
        Collection<Analyzer> databaseAnalyzers = new ArrayList<Analyzer>();
        for (String feature : allFeatures) {
            if (feature.equalsIgnoreCase(Tools.FeatureType.ACTIVITY_COUNT)) {
                databaseAnalyzers.add(new ActivityCount(sqlConnection));
            }
        }

        for (Analyzer analyzer : databaseAnalyzers) {
            analyzer.deleteFromDB(startTime, endTime);
        }

        for (Analyzer analyzer : databaseAnalyzers) {
            analyzer.recalculate(startTime, endTime);
        }
    }

    private Properties loadProperties(String filepath) throws Exception {
        InputStream input = null;
        try {
            File file = new File(filepath);
            if (file.exists()) {
                input = new FileInputStream(file);
            } else {
                input = Thread.currentThread().getContextClassLoader().getResourceAsStream(filepath);
            }

            Properties properties = new Properties();
            properties.load(input);
            return properties;
        } catch (Exception ex) {
            String msg = String.format("Exception occured while loading configuration from file \"%s\"", filepath);
            throw new Exception(msg, ex);
        } finally {
            if (null != input) {
                input.close();
            }
        }
    }

    private Connection createSqlConnection(Properties properties) throws Exception {
        try {
            String mysqlHost = properties.getProperty(Tools.MYSQL_HOST_KEY);
            int port = Integer.parseInt(properties.getProperty(Tools.MYSQL_PORT_KEY));
            String db = properties.getProperty(Tools.MYSQL_DB_KEY);
            String user = properties.getProperty(Tools.MYSQL_USER_KEY);
            String passwd = properties.getProperty(Tools.MYSQL_PASSWD_KEY);

            Class.forName("com.mysql.jdbc.Driver");
            return DriverManager.getConnection(
                    "jdbc:mysql://" + mysqlHost + ":" + port + "/" + db,
                    user, passwd
            );
        } catch (Exception ex) {
            String msg = "MySQL connection is not created. The dburl, username , password is not correct";
            throw new Exception(msg, ex);
        }
    }

    private void clear(Collection<Analyzer> analyzers) {
        for (Analyzer analyzer : analyzers) {
            analyzer.clear();
        }
    }

    private void saveToDB(Collection<Analyzer> analyzers) throws SQLException {
        for (Analyzer analyzer : analyzers) {
            analyzer.saveToDB();
        }
    }

    @Override
    public void close() throws Exception {
        sqlConnection.close();
    }
}
