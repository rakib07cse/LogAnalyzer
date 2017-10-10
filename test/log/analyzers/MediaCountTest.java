package log.analyzers;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author sazzad
 */
public class MediaCountTest {

    private static final Pattern logPattern = Pattern.compile("^(\\d{17})\\s+([A-Z]{4,5})\\s+");

    public static void main(String[] args) throws IOException, SQLException {
        MediaCount mc = new MediaCount(null);

        String filename = "/home/sazzad/Temp/sample/1478508482007-2392";

        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new FileInputStream(filename), StandardCharsets.UTF_8))) {
            String line;

            while ((line = br.readLine()) != null) {
                Matcher matcher = logPattern.matcher(line);
                if (matcher.find()) {
                    String time = matcher.group(1);
                    String type = matcher.group(2);

                    if ("INFO".equalsIgnoreCase(type)) {
                        mc.processLog(line);
                    }
                }
            }
        }

        System.out.println(mc.getCountMap());
    }
}
