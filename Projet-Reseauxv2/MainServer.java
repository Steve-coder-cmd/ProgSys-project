import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MainServer {
    private static final int PORT = ServerConfigLoader.getMainServerPort();
    private static final String SERVER_DIRECTORY = ServerConfigLoader.getServerDirectory();
    private static final int CHUNK_SIZE = ServerConfigLoader.getChunkSize();
    private static final List<ServerConfigLoader.SubServerInfo> SUB_SERVERS = ServerConfigLoader.getSubServers();

    public static void main(String[] args) {
        File serverDir = new File(SERVER_DIRECTORY);
        if (!serverDir.exists() && !serverDir.mkdirs()) {
            System.err.println("Erreur : Impossible de créer le répertoire du serveur.");
            return;
        }

        startSubServers();

        ExecutorService threadPool = Executors.newCachedThreadPool();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Serveur principal en écoute sur le port " + PORT + "...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connecté : " + clientSocket.getInetAddress());

                threadPool.execute(() -> handleClient(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("Erreur lors de l'écoute du port : " + e.getMessage());
        } finally {
            threadPool.shutdown();
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (Socket socket = clientSocket;
             DataInputStream dis = new DataInputStream(socket.getInputStream());
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {
    
            String command = dis.readUTF();
            switch (command.toUpperCase()) {
                case "ENVOYER":
                    String fileName = dis.readUTF();
                    long fileSize = dis.readLong();
                    distributeFile(dis, fileName, fileSize);
                    dos.writeUTF("OK");
                    System.out.println("Fichier reçu et distribué : " + fileName);
                    break;
                case "RECEVOIR":
                    fileName = dis.readUTF();
                    File assembledFile = assembleFile(fileName);
                    if (assembledFile != null) {
                        sendFileInChunks(assembledFile, dos);
                        if (!assembledFile.delete()) {
                            System.err.println("Erreur lors de la suppression du fichier : " + fileName);
                        }
                    } else {
                        dos.writeUTF("Fichier introuvable");
                    }
                    break;
                case "LISTER":
                    listFiles(dos);
                    break;
                case "SUPPRIMER":
                    deleteFile(dis, dos);
                    break;
                default:
                    dos.writeUTF("COMMANDE INCONNUE");
            }
        } catch (IOException e) {
            System.err.println("Erreur avec un client : " + e.getMessage());
        }
    }
    

    private static void startSubServers() {
        for (ServerConfigLoader.SubServerInfo subServerInfo : SUB_SERVERS) {
            new Thread(() -> {
                try {
                    startSubServer(subServerInfo.port);
                } catch (IOException e) {
                    System.err.println("Erreur lors du démarrage du sous-serveur sur le port " + subServerInfo.port);
                    e.printStackTrace();
                }
            }).start();
        }
    }

    private static void startSubServer(int port) throws IOException {
        File subServerDir = new File("sub_server_directory_" + port);
        if (!subServerDir.exists() && !subServerDir.mkdirs()) {
            System.err.println("Erreur : Impossible de créer le répertoire du sous-serveur sur le port " + port);
            return;
        }

        try (ServerSocket subServerSocket = new ServerSocket(port)) {
            System.out.println("Sous-serveur démarré sur le port " + port + "...");

            while (true) {
                try (Socket clientSocket = subServerSocket.accept()) {
                    DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
                    DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream());

                    String command = dis.readUTF();
                    if ("STORE".equalsIgnoreCase(command)) {
                        String fileName = dis.readUTF();
                        long fileSize = dis.readLong();
                        receiveFile(dis, new File(subServerDir, fileName), fileSize);
                        System.out.println("Fragment reçu et stocké : " + fileName);
                    } else if ("RETRIEVE".equalsIgnoreCase(command)) {
                        String fileName = dis.readUTF();
                        File file = new File(subServerDir, fileName);
                        if (file.exists()) {
                            sendFile(dos, file);
                            System.out.println("Fragment envoyé : " + fileName);
                        } else {
                            dos.writeUTF("Fichier introuvable");
                        }
                    }
                }
            }
        }
    }

    private static void receiveFile(DataInputStream dis, File file, long fileSize) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            byte[] buffer = new byte[CHUNK_SIZE];
            long totalRead = 0;
            int bytesRead;
            while (totalRead < fileSize) {
                bytesRead = dis.read(buffer, 0, (int) Math.min(CHUNK_SIZE, fileSize - totalRead));
                fos.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
            }
        }
    }

    private static void sendFile(DataOutputStream dos, File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            dos.writeUTF("OK");
            dos.writeLong(file.length());
            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                dos.write(buffer, 0, bytesRead);
            }
        }
    }

    private static void distributeFile(DataInputStream dis, String fileName, long fileSize) throws IOException {
        long fragmentSize = fileSize / SUB_SERVERS.size();
        byte[] buffer = new byte[CHUNK_SIZE];

        for (int i = 0; i < SUB_SERVERS.size(); i++) {
            long bytesToSend = (i == SUB_SERVERS.size() - 1) ? (fileSize - i * fragmentSize) : fragmentSize;

            try (Socket subServerSocket = new Socket(SUB_SERVERS.get(i).host, SUB_SERVERS.get(i).port);
                 DataOutputStream subDos = new DataOutputStream(subServerSocket.getOutputStream())) {

                subDos.writeUTF("STORE");
                subDos.writeUTF(fileName + ".part" + i);
                subDos.writeLong(bytesToSend);

                long bytesSent = 0;
                while (bytesSent < bytesToSend) {
                    int bytesRead = dis.read(buffer, 0, (int) Math.min(CHUNK_SIZE, bytesToSend - bytesSent));
                    subDos.write(buffer, 0, bytesRead);
                    bytesSent += bytesRead;
                }
            }
        }
    }

    private static File assembleFile(String fileName) throws IOException {
        File assembledFile = new File(SERVER_DIRECTORY, fileName);

        try (FileOutputStream fos = new FileOutputStream(assembledFile)) {
            byte[] buffer = new byte[CHUNK_SIZE];

            for (int i = 0; i < SUB_SERVERS.size(); i++) {
                try (Socket subServerSocket = new Socket(SUB_SERVERS.get(i).host, SUB_SERVERS.get(i).port);
                     DataOutputStream subDos = new DataOutputStream(subServerSocket.getOutputStream());
                     DataInputStream subDis = new DataInputStream(subServerSocket.getInputStream())) {

                    subDos.writeUTF("RETRIEVE");
                    subDos.writeUTF(fileName + ".part" + i);

                    long fragmentSize = subDis.readLong();
                    long bytesReceived = 0;
                    while (bytesReceived < fragmentSize) {
                        int bytesRead = subDis.read(buffer, 0, (int) Math.min(CHUNK_SIZE, fragmentSize - bytesReceived));
                        fos.write(buffer, 0, bytesRead);
                        bytesReceived += bytesRead;
                    }
                }
            }
        }

        return assembledFile;
    }

    private static void sendFileInChunks(File file, DataOutputStream dos) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            dos.writeUTF("OK");
            dos.writeLong(file.length());
            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                dos.write(buffer, 0, bytesRead);
            }
        }
    }

    private static void listFiles(DataOutputStream dos) throws IOException {
        File serverDir = new File(SERVER_DIRECTORY);
        File[] files = serverDir.listFiles();
        if (files != null && files.length > 0) {
            dos.writeUTF("OK");
            for (File file : files) {
                dos.writeUTF(file.getName());
            }
        } else {
            dos.writeUTF("Aucun fichier trouvé.");
        }
    }

    private static void deleteFile(DataInputStream dis, DataOutputStream dos) throws IOException {
        String fileName = dis.readUTF();
        File file = new File(SERVER_DIRECTORY, fileName);
        if (file.exists() && file.delete()) {
            dos.writeUTF("Fichier supprimé avec succès.");
            System.out.println("Fichier supprimé : " + fileName);
        } else {
            dos.writeUTF("Erreur lors de la suppression du fichier.");
        }
    }
    
}
