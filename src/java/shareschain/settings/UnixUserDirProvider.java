
package shareschain.settings;

import java.nio.file.Paths;

public class UnixUserDirProvider extends DesktopUserDirProvider {

    private static final String SHARESCHAIN_USER_HOME = Paths.get(System.getProperty("user.home"), ".shareschain").toString();

    @Override
    public String getUserHomeDir() {
        return SHARESCHAIN_USER_HOME;
    }
}
