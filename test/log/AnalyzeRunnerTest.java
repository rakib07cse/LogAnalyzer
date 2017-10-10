/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package log;

import org.apache.log4j.PropertyConfigurator;

/**
 *
 * @author sazzad
 */
public class AnalyzeRunnerTest {

    public static void main(String[] args) {
        PropertyConfigurator.configure("log4j.properties");
        AnalyzeRunner runner = new AnalyzeRunner("/var/tmp/analyzer.properties");
        runner.start();
    }
}
