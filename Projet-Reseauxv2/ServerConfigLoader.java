
import java.io.*;
import java.util.*;

public class ServerConfigLoader {
    private static final String CONFIG_FILE = "servers_config.txt";
    private static String mainServerHost;
    private static int mainServerPort;
    private static List<SubServerInfo> subServers;
    private static final String SERVER_DIRECTORY = "server_directory";
    private static final int CHUNK_SIZE = 1024;

    static {
        subServers = new ArrayList<>();
        loadConfiguration();
    }

    private static void loadConfiguration() {
        try (BufferedReader reader = new BufferedReader(new FileReader(CONFIG_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty() && !line.startsWith("#")) {
                    String[] parts = line.trim().split(":");
                    if (parts.length == 3) {
                        String type = parts[0];
                        String host = parts[1];
                        int port = Integer.parseInt(parts[2]);

                        if ("main_server".equals(type)) {
                            mainServerHost = host;
                            mainServerPort = port;
                        } else if ("sub_server".equals(type)) {
                            subServers.add(new SubServerInfo(host, port));
                        }
                    }
                }
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("Erreur lors de la lecture ou du parsing du fichier de configuration : " + e.getMessage());
            setDefaultValues();
        }
    }

    private static void setDefaultValues() {
        System.err.println("Utilisation des valeurs par défaut pour la configuration du serveur.");
        mainServerHost = "127.0.0.1";
        mainServerPort = 12345;
        subServers.clear();
        subServers.add(new SubServerInfo("127.0.0.1", 12346));
        subServers.add(new SubServerInfo("127.0.0.1", 12347));
        subServers.add(new SubServerInfo("127.0.0.1", 12348));
    }

    public static String getMainServerHost() {
        return mainServerHost;
    }

    public static int getMainServerPort() {
        return mainServerPort;
    }

    public static List<SubServerInfo> getSubServers() {
        return Collections.unmodifiableList(subServers);
    }

    public static String getServerDirectory() {
        return SERVER_DIRECTORY;
    }

    public static int getChunkSize() {
        return CHUNK_SIZE;
    }

    public static class SubServerInfo {
        public String host;
        public int port;
    
        public SubServerInfo(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }
    
}
