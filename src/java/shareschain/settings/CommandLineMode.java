
package shareschain.settings;

import shareschain.ServerStatus;

import java.io.File;
import java.net.URI;

public class CommandLineMode implements RuntimeMode {

    @Override
    public void init() {}

    @Override
    public void setServerStatus(ServerStatus status, URI wallet, File logFileDir) {}

    @Override
    public void launchDesktopApplication() {}

    @Override
    public void shutdown() {}

    @Override
    public void alert(String message) {}
}
