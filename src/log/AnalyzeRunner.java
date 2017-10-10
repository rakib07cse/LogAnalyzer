package log;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

/**
 * This program will run infinitely.<br>
 * This program will process at 15th minute of each hour with following
 * exceptions:<br>
 * &nbsp;- At the very first time it will start processing immediately.<br>
 * &nbsp;- If a process is already running at 15th minute, new process will
 * start only after the running one. <br>
 *
 * @author sazzad
 */
public class AnalyzeRunner extends Thread {

    private static final Logger logger = Logger.getLogger(AnalyzeRunner.class);

    private final String configFilePath;
    static final long SECOND = 1000L;
    static final long MINUTE = 60 * SECOND;
    static final long HOUR = 60 * MINUTE;

    public AnalyzeRunner(String configFilepath) {
        this.configFilePath = configFilepath;
    }

    @Override
    public void run() {
        while (true) {
            long currentTime = System.currentTimeMillis();
            long nextRunTime = (((currentTime / HOUR) + 1) * HOUR) + (15 * MINUTE);

            //********** PROCESS RUNNING CODE BEGINS HERE **********//
            try (AnalyzeManager processor = new AnalyzeManager(configFilePath)) {
                processor.manageAnalyzer();
            }
            catch (Exception ex) {
                logger.error("Error while processing analyzers.", ex);
            }
            //*********** PROCESS RUNNING CODE ENDS HERE ***********//

            long waitingTime = nextRunTime - System.currentTimeMillis();
            if (waitingTime > 0) {
                try {
                    sleep(waitingTime);
                }
                catch (InterruptedException ex) {
                    logger.error("Exception occured for Thread.sleep()", ex);
                }
            }
        }
    }

    /**
     * DO NOT EDIT main
     *
     * @param args
     */
    public static void main(String[] args) {
        PropertyConfigurator.configure("log4j.properties");
        AnalyzeRunner runner = new AnalyzeRunner("analyzer.properties");
        runner.start();
    }
}
