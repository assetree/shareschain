
package shareschain;

import shareschain.account.Account;
import shareschain.account.AccountLedger;
import shareschain.blockchain.*;
import shareschain.blockchain.SmcTransaction;
import shareschain.util.crypto.Crypto;
import shareschain.database.DB;
import shareschain.settings.DirProvider;
import shareschain.settings.RuntimeEnvironment;
import shareschain.settings.RuntimeMode;
import shareschain.network.API;
import shareschain.network.APIProxy;
import shareschain.node.NetworkHandler;
import shareschain.node.Nodes;
import shareschain.util.Convert;
import shareschain.util.Logger;
import shareschain.util.ThreadPool;
import shareschain.util.Time;
import org.json.simple.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public final class Shareschain {
    // 定义版本
    public static final String VERSION = "0.1.0";

    // 定义程序名称
    public static final String APPLICATION = "SharesChain";

    // 获取新纪元时间
    private static volatile Time time = new Time.EpochTime();

    // shareschain.properties中的值会覆盖shareschain-default.properties中的值
    public static final String SHARESCHAIN_DEFAULT_PROPERTIES = "shareschain-default.properties";
    public static final String SHARESCHAIN_PROPERTIES = "shareschain.properties";
    public static final String SHARESCHAIN_INSTALLER_PROPERTIES = "shareschain-installer.properties";
    public static final String CONFIG_DIR = "conf";

    private static final RuntimeMode runtimeMode;
    private static final DirProvider dirProvider;

    // 存放默认参数
    private static final Properties defaultProperties = new Properties();

    // 重定向系统标准输入和输出；打印命令行参数；获取运行模式；获取目录；加载默认配置；比较系统版本。
    static {
        redirectSystemStreams("out");
        redirectSystemStreams("err");
        System.out.println("Initializing Shareschain server version " + Shareschain.VERSION);
        printCommandLineArguments();
        runtimeMode = RuntimeEnvironment.getRuntimeMode();
        System.out.printf("Runtime mode %s\n", runtimeMode.getClass().getName());
        dirProvider = RuntimeEnvironment.getDirProvider();
        System.out.println("User home folder " + dirProvider.getUserHomeDir());
        loadProperties(defaultProperties, SHARESCHAIN_DEFAULT_PROPERTIES, true);
        if (!VERSION.equals(Shareschain.defaultProperties.getProperty("shareschain.version"))) {
            throw new RuntimeException("Using an shareschain-default.properties file from a version other than " + VERSION + " is not supported!!!");
        }
    }

    // 输出重定向
    private static void redirectSystemStreams(String streamName) {
        String isStandardRedirect = System.getProperty("shareschain.redirect.system." + streamName);
        Path path = null;
        if (isStandardRedirect != null) {
            try {
                path = Files.createTempFile("shareschain.system." + streamName + ".", ".log");
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        } else {
            String explicitFileName = System.getProperty("shareschain.system." + streamName);
            if (explicitFileName != null) {
                path = Paths.get(explicitFileName);
            }
        }
        if (path != null) {
            try {
                PrintStream stream = new PrintStream(Files.newOutputStream(path));
                if (streamName.equals("out")) {
                    System.setOut(new PrintStream(stream));
                } else {
                    System.setErr(new PrintStream(stream));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // 存放用户自定义参数，使用默认参数初始化。
    private static final Properties properties = new Properties(defaultProperties);

    // 加载用户自定义参数
    static {
        loadProperties(properties, SHARESCHAIN_INSTALLER_PROPERTIES, true);
        loadProperties(properties, SHARESCHAIN_PROPERTIES, false);
    }

    // 加载配置文件
    public static void loadProperties(Properties properties, String propertiesFile, boolean isDefault) {
        try {
            // Load properties from location specified as command line parameter
            String configFile = System.getProperty(propertiesFile);
            if (configFile != null) {
                System.out.printf("Loading %s from %s\n", propertiesFile, configFile);
                try (InputStream fis = new FileInputStream(configFile)) {
                    properties.load(fis);
                } catch (IOException e) {
                    throw new IllegalArgumentException(String.format("Error loading %s from %s", propertiesFile, configFile));
                }
            } else {
                try (InputStream is = ClassLoader.getSystemResourceAsStream(propertiesFile)) {
                    // When running shareschain.exe from a Windows installation we always have shareschain.properties in the classpath but this is not the smc properties file
                    // Therefore we first load it from the classpath and then look for the real shareschain.properties in the user folder.
                    if (is != null) {
                        System.out.printf("Loading %s from classpath\n", propertiesFile);
                        properties.load(is);
                        if (isDefault) {
                            return;
                        }
                    }
                    // load non-default properties files from the user folder
                    if (!dirProvider.isLoadPropertyFileFromUserDir()) {
                        return;
                    }
                    String homeDir = dirProvider.getUserHomeDir();
                    if (!Files.isReadable(Paths.get(homeDir))) {
                        System.out.printf("Creating dir %s\n", homeDir);
                        try {
                            Files.createDirectory(Paths.get(homeDir));
                        } catch(Exception e) {
                            if (!(e instanceof NoSuchFileException)) {
                                throw e;
                            }
                            // Fix for WinXP and 2003 which does have a roaming sub folder
                            Files.createDirectory(Paths.get(homeDir).getParent());
                            Files.createDirectory(Paths.get(homeDir));
                        }
                    }
                    Path confDir = Paths.get(homeDir, CONFIG_DIR);
                    if (!Files.isReadable(confDir)) {
                        System.out.printf("Creating dir %s\n", confDir);
                        Files.createDirectory(confDir);
                    }
                    Path propPath = Paths.get(confDir.toString()).resolve(Paths.get(propertiesFile));
                    if (Files.isReadable(propPath)) {
                        System.out.printf("Loading %s from dir %s\n", propertiesFile, confDir);
                        properties.load(Files.newInputStream(propPath));
                    } else {
                        System.out.printf("Creating property file %s\n", propPath);
                        Files.createFile(propPath);
                        Files.write(propPath, Convert.toBytes("# use this file for workstation specific " + propertiesFile));
                    }
                } catch (IOException e) {
                    throw new IllegalArgumentException("Error loading " + propertiesFile, e);
                }
            }
        } catch(IllegalArgumentException e) {
            e.printStackTrace(); // make sure we log this exception
            throw e;
        }
    }

    // 打印命令行参数
    private static void printCommandLineArguments() {
        try {
            List<String> inputArgumentList = ManagementFactory.getRuntimeMXBean().getInputArguments();
            if (inputArgumentList != null && inputArgumentList.size() > 0) {
                System.out.println("Command line arguments");
            } else {
                return;
            }
            inputArgumentList.forEach(System.out::println);
        } catch (AccessControlException e) {
            System.out.println("Cannot read input arguments " + e.getMessage());
        }
    }

    // 定义了一系列工具函数
    public static int getIntProperty(String name) {
        return getIntProperty(name, 0);
    }

    public static int getIntProperty(String name, int defaultValue) {
        try {
            int result = Integer.parseInt(properties.getProperty(name));
            Logger.logMessage(name + " = \"" + result + "\"");
            return result;
        } catch (NumberFormatException e) {
            Logger.logMessage(name + " not defined or not numeric, using default value " + defaultValue);
            return defaultValue;
        }
    }


    public static String getStringProperty(String name) {
        return getStringProperty(name, null, false);
    }

    public static String getStringProperty(String name, String defaultValue) {
        return getStringProperty(name, defaultValue, false);
    }

    public static String getStringProperty(String name, String defaultValue, boolean doNotLog) {
        return getStringProperty(name, defaultValue, doNotLog, null);
    }

    public static String getStringProperty(String name, String defaultValue, boolean doNotLog, String encoding) {
        String value = properties.getProperty(name);
        if (value != null && ! "".equals(value)) {
            Logger.logMessage(name + " = \"" + (doNotLog ? "{not logged}" : value) + "\"");
        } else {
            Logger.logMessage(name + " not defined");
            value = defaultValue;
        }
        if (encoding == null || value == null) {
            return value;
        }
        try {
            return new String(value.getBytes("ISO-8859-1"), encoding);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    // 读取哪些使用分号分割的属性值
    public static List<String> getStringListProperty(String name) {
        String value = getStringProperty(name);
        if (value == null || value.length() == 0) {
            return Collections.emptyList();
        }
        List<String> resultList = new ArrayList<>();
        for (String s : value.split(";")) {
            s = s.trim();
            if (s.length() > 0) {
                resultList.add(s);
            }
        }
        return resultList;
    }

    public static boolean getBooleanProperty(String name) {
        return getBooleanProperty(name, false);
    }

    public static boolean getBooleanProperty(String name, boolean defaultValue) {
        String value = properties.getProperty(name);
        if (Boolean.TRUE.toString().equals(value)) {
            Logger.logMessage(name + " = \"true\"");
            return true;
        } else if (Boolean.FALSE.toString().equals(value)) {
            Logger.logMessage(name + " = \"false\"");
            return false;
        }
        Logger.logMessage(name + " not defined, using default " + defaultValue);
        return defaultValue;
    }

    // 获取区块链对象
    public static Blockchain getBlockchain() {
        return BlockchainImpl.getInstance();
    }

    // 获取区块链处理器对象
    public static BlockchainProcessor getBlockchainProcessor() {
        return BlockchainProcessorImpl.getInstance();
    }

    // 获取交易处理器对象
    public static TransactionProcessor getTransactionProcessor() {
        return TransactionProcessorImpl.getInstance();
    }

    // 分析区块
    public static Block parseBlock(byte[] blockBytes, List<? extends SmcTransaction> blockTransactions) throws ShareschainException.NotValidException {
        return BlockImpl.parseBlock(blockBytes, blockTransactions);
    }

    // 分析交易
    public static Transaction parseTransaction(byte[] transactionBytes) throws ShareschainException.NotValidException {
        return TransactionImpl.parseTransaction(transactionBytes);
    }


    // 创建一个交易构造器
    public static Transaction.Builder newTransactionBuilder(byte[] transactionBytes) throws ShareschainException.NotValidException {
        return TransactionImpl.newTransactionBuilder(transactionBytes);
    }

    // 创建一个交易构造器（重载）
    public static Transaction.Builder newTransactionBuilder(JSONObject transactionJSON) throws ShareschainException.NotValidException {
        return TransactionImpl.newTransactionBuilder(transactionJSON);
    }

    // 创建一个交易构造器（重载）
    public static Transaction.Builder newTransactionBuilder(byte[] transactionBytes, JSONObject prunableAttachments) throws ShareschainException.NotValidException {
        return TransactionImpl.newTransactionBuilder(transactionBytes, prunableAttachments);
    }

    public static int getEpochTime() {
        return time.getTime();
    }

    static void setTime(Time time) {
        Shareschain.time = time;
    }

    public static void main(String[] args) {
        try {

            // 添加一个钩子，在java程序退出时调用
            Runtime.getRuntime().addShutdownHook(new Thread(Shareschain::shutdown));
            init();
        } catch (Throwable t) {
            System.out.println("Fatal error: " + t.toString());
            t.printStackTrace();
        }
    }

    public static void init(Properties customProperties) {
        properties.putAll(customProperties);
        init();
    }

    // 初始化
    public static void init() {
        Init.init();
    }

    // java程序退出时执行该函数
    public static void shutdown() {
        Logger.logShutdownMessage("Shutting down...");
        API.shutdown();
        ThreadPool.shutdown();
        BlockchainProcessorImpl.getInstance().shutdown();
        Nodes.shutdown();
        NetworkHandler.shutdown();
        DB.shutdown();
        Logger.logShutdownMessage("Shareschain server " + VERSION + " stopped.");
        Logger.shutdown();
        runtimeMode.shutdown();
    }

    private static class Init {

        private static volatile boolean initialized = false;

        static {
            try {
                // 获取初始化启动时的时间
                long startTime = System.currentTimeMillis();

                // 日志初始化
                Logger.init();

                // 记录系统参数
                logSystemProperties();

                // 运行方式初始化
                runtimeMode.init();

                // 初始化一个线程用来提供随机值
                Thread secureRandomInitThread = initSecureRandom();

                setServerStatus(ServerStatus.BEFORE_DATABASE, null);

                // 数据库初始化
                DB.init();
                setServerStatus(ServerStatus.AFTER_DATABASE, null);

                // 交易处理器初始化
                TransactionProcessorImpl.getInstance();

                // 区块链处理器初始化
                BlockchainProcessorImpl.getInstance();

                Mainchain.init();


                // 账户体系相关
                Account.init();

                // 账户相关的账本
                AccountLedger.init();

                // 网络处理，manage inbound and outbound connections
                NetworkHandler.init();

                // 处理与节点相关的事情
                Nodes.init();

                // 启动API的代理
                APIProxy.init();
                Generator.init();

                // 启动API
                API.init();
                int timeMultiplier = (Constants.isTestnet && Constants.isOffline) ? Math.max(Shareschain.getIntProperty("shareschain.timeMultiplier"), 1) : 1;
                ThreadPool.start(timeMultiplier);
                if (timeMultiplier > 1) {
                    setTime(new Time.FasterTime(Math.max(getEpochTime(), Shareschain.getBlockchain().getLastBlock().getTimestamp()), timeMultiplier));
                    Logger.logMessage("TIME WILL FLOW " + timeMultiplier + " TIMES FASTER!");
                }
                //阻塞线程，等待子线程处理完成。
                try {
                    secureRandomInitThread.join(10000);
                } catch (InterruptedException ignore) {}
                testSecureRandom();

                long currentTime = System.currentTimeMillis();
                Logger.logMessage("Initialization took " + (currentTime - startTime) / 1000 + " seconds");
                setServerStatus(ServerStatus.STARTED, API.getWelcomePageUri());
                if (isDesktopApplicationEnabled()) {
                    launchDesktopApplication();
                }
                if (Constants.isTestnet) {
                    Logger.logMessage("RUNNING ON TESTNET - DO NOT USE REAL ACCOUNTS!");
                }
            } catch (Exception e) {
                Logger.logErrorMessage(e.getMessage(), e);
                runtimeMode.alert(e.getMessage() + "\n" +
                        "See additional information in " + dirProvider.getLogFileDir() + System.getProperty("file.separator") + "shareschain.log");
                System.exit(1);
            }
        }

        private static void init() {
            if (initialized) {
                throw new RuntimeException("Shareschain.init has already been called");
            }
            initialized = true;
        }

        private Init() {} // never

    }

    private static void logSystemProperties() {
        String[] loggedProperties = new String[] {
                "java.version",
                "java.vm.version",
                "java.vm.name",
                "java.vendor",
                "java.vm.vendor",
                "java.home",
                "java.library.path",
                "java.class.path",
                "os.arch",
                "sun.arch.data.model",
                "os.name",
                "file.encoding",
                "java.security.policy",
                "java.security.manager",
                RuntimeEnvironment.RUNTIME_MODE_ARG,
                RuntimeEnvironment.DIRPROVIDER_ARG
        };
        for (String property : loggedProperties) {
            Logger.logDebugMessage(String.format("%s = %s", property, System.getProperty(property)));
        }
        Logger.logDebugMessage(String.format("availableProcessors = %s", Runtime.getRuntime().availableProcessors()));
        Logger.logDebugMessage(String.format("maxMemory = %s", Runtime.getRuntime().maxMemory()));
        Logger.logDebugMessage(String.format("processId = %s", getProcessId()));
    }

    private static Thread initSecureRandom() {
        Thread secureRandomInitThread = new Thread(() -> Crypto.getSecureRandom().nextBytes(new byte[1024]));
        secureRandomInitThread.setDaemon(true);
        secureRandomInitThread.start();
        return secureRandomInitThread;
    }

    private static void testSecureRandom() {
        Thread thread = new Thread(() -> Crypto.getSecureRandom().nextBytes(new byte[1024]));
        thread.setDaemon(true);
        thread.start();
        try {
            thread.join(2000);
            if (thread.isAlive()) {
                throw new RuntimeException("SecureRandom implementation too slow!!! " +
                        "Install haveged if on linux, or set shareschain.useStrongSecureRandom=false.");
            }
        } catch (InterruptedException ignore) {}
    }

    public static String getProcessId() {
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        if (runtimeName == null) {
            return "";
        }
        String[] tokens = runtimeName.split("@");
        if (tokens.length == 2) {
            return tokens[0];
        }
        return "";
    }

    public static String getDBDir(String dbDir) {
        return dirProvider.getDBDir(dbDir);
    }

    public static void updateLogFileHandler(Properties loggingProperties) {
        dirProvider.updateLogFileHandler(loggingProperties);
    }

    public static String getUserHomeDir() {
        return dirProvider.getUserHomeDir();
    }

    public static File getConfDir() {
        return dirProvider.getConfDir();
    }

    private static void setServerStatus(ServerStatus status, URI wallet) {
        runtimeMode.setServerStatus(status, wallet, dirProvider.getLogFileDir());
    }

    public static boolean isDesktopApplicationEnabled() {
        return RuntimeEnvironment.isDesktopApplicationEnabled() && Shareschain.getBooleanProperty("shareschain.launchDesktopApplication");
    }

    private static void launchDesktopApplication() {
        runtimeMode.launchDesktopApplication();
    }

    private Shareschain() {} // never

}
