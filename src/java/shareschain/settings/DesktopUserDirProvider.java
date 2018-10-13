
package shareschain.settings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

abstract class DesktopUserDirProvider implements DirProvider {

    public static final String LOG_FILE_PATTERN = "java.util.logging.FileHandler.pattern";

    private File logFileDir;

    @Override
    public boolean isLoadPropertyFileFromUserDir() {
        return true;
    }

    @Override
    public void updateLogFileHandler(Properties loggingProperties) {
        if (loggingProperties.getProperty(LOG_FILE_PATTERN) == null) {
            logFileDir = new File(getUserHomeDir(), "log");
            return;
        }
        Path logFilePattern = Paths.get(getUserHomeDir()).resolve(Paths.get(loggingProperties.getProperty(LOG_FILE_PATTERN)));
        loggingProperties.setProperty(LOG_FILE_PATTERN, logFilePattern.toString());

        Path logDirPath = logFilePattern.getParent();
        System.out.printf("Log dir %s\n", logDirPath.toString());
        this.logFileDir = new File(logDirPath.toString());
        if (!Files.isReadable(logDirPath)) {
            System.out.printf("Creating dir %s\n", logDirPath);
            try {
                Files.createDirectory(logDirPath);
            } catch (IOException e) {
                throw new IllegalArgumentException("Cannot create " + logDirPath, e);
            }
        }
    }

    @Override
    public File getLogFileDir() {
        return logFileDir;
    }

    @Override
    public String getDBDir(String dbDir) {
        return Paths.get(getUserHomeDir()).resolve(Paths.get(dbDir)).toString();
    }

    @Override
    public File getConfDir() {
        return new File(getUserHomeDir(), "conf");
    }

    @Override
    public abstract String getUserHomeDir();

}
