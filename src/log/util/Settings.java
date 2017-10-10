/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package log.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static log.util.Tools.SettingType.REVISIT_FEATURES;
import static log.util.Tools.SettingType.REVISIT_TIME;

/**
 *
 * @author shihab
 */
public class Settings {

    private final String SELECT_SETTING_SQL = "SELECT name, value from analytics_settings";
    private final String DELETE_SETTING_SQL = "DELETE FROM analytics_settings WHERE name = ?";

    private final Map<String, String> settingMap = new HashMap<>();

    private final List<String> deleteCandidates = Arrays.asList(REVISIT_FEATURES, REVISIT_TIME);

    public Settings(Connection sqlConnection) throws SQLException {
        loadSettingFromDB(sqlConnection);
    }

    private void loadSettingFromDB(Connection sqlConnection) throws SQLException {

        try (PreparedStatement statement = sqlConnection.prepareStatement(SELECT_SETTING_SQL)) {
            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                String name = rs.getString("name").trim();
                String value = rs.getString("value").trim();
                settingMap.put(name, value);
            }
        }
    }

    public Map<String, String> getSettingMap() {
        return Collections.unmodifiableMap(settingMap);
    }

    public void delFeatureSetting(Connection sqlConnection) throws SQLException {

        try (PreparedStatement delStmt = sqlConnection.prepareStatement(DELETE_SETTING_SQL)) {
            for (String delCand : deleteCandidates) {
                delStmt.setString(1, delCand);
                delStmt.addBatch();
                delStmt.clearParameters();
            }
            delStmt.executeBatch();
            delStmt.clearBatch();
        }
    }
}
