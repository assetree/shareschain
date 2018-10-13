
package shareschain.settings;

import shareschain.ServerStatus;

import java.io.File;
import java.net.URI;

public interface RuntimeMode {

    void init();

    void setServerStatus(ServerStatus status, URI wallet, File logFileDir);

    void launchDesktopApplication();

    void shutdown();

    void alert(String message);
}
