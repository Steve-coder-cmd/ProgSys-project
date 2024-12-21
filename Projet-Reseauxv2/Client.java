import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.Socket;

public class Client {
    private static final String SERVER_HOST = ServerConfigLoader.getMainServerHost();
    private static final int SERVER_PORT = ServerConfigLoader.getMainServerPort();
    private static final int CHUNK_SIZE = ServerConfigLoader.getChunkSize();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Client::createAndShowGUI);
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("Client - Gestion de Fichiers");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 700);
        frame.setLocationRelativeTo(null); // Centrer la fenêtre

        // Panel principal avec un BorderLayout
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout(10, 10));

        // Panneau pour les boutons
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 20, 10)); // Centrer les boutons

        JButton sendFileButton = new JButton("Envoyer un fichier");
        JButton receiveFileButton = new JButton("Télécharger un fichier");
        JButton listFilesButton = new JButton("Lister les fichiers");
        JButton deleteFileButton = new JButton("Supprimer un fichier");

        buttonPanel.add(sendFileButton);
        buttonPanel.add(receiveFileButton);
        buttonPanel.add(listFilesButton);
        buttonPanel.add(deleteFileButton);

        // Zone de texte pour les logs
        JTextArea logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Arial", Font.PLAIN, 12));
        logArea.setBackground(Color.LIGHT_GRAY);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setPreferredSize(new Dimension(550, 150)); // Ajuster la taille du panneau de scroll

        // Ajout des actions aux boutons
        sendFileButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            int returnValue = fileChooser.showOpenDialog(frame);
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                if (selectedFile != null) {
                    try {
                        sendFileToServer(selectedFile, logArea);
                    } catch (IOException ex) {
                        logArea.append("Erreur lors de l'envoi : " + ex.getMessage() + "\n");
                    }
                }
            }
        });

        receiveFileButton.addActionListener(e -> {
            String fileName = JOptionPane.showInputDialog(frame, "Entrez le nom du fichier à télécharger :");
            if (fileName != null && !fileName.isEmpty()) {
                try {
                    receiveFileFromServer(fileName, logArea);
                } catch (IOException ex) {
                    logArea.append("Erreur lors du téléchargement : " + ex.getMessage() + "\n");
                }
            }
        });

        listFilesButton.addActionListener(e -> {
            try {
                listFilesFromServer(logArea);
            } catch (IOException ex) {
                logArea.append("Erreur lors de la récupération de la liste des fichiers : " + ex.getMessage() + "\n");
            }
        });

        deleteFileButton.addActionListener(e -> {
            String fileName = JOptionPane.showInputDialog(frame, "Entrez le nom du fichier à supprimer :");
            if (fileName != null && !fileName.isEmpty()) {
                try {
                    deleteFileOnServer(fileName, logArea);
                } catch (IOException ex) {
                    logArea.append("Erreur lors de la suppression du fichier : " + ex.getMessage() + "\n");
                }
            }
        });

        // Ajout des panels au frame
        mainPanel.add(buttonPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        frame.getContentPane().add(mainPanel);
        frame.setVisible(true);
    }

    private static void sendFileToServer(File file, JTextArea logArea) throws IOException {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             FileInputStream fis = new FileInputStream(file)) {

            dos.writeUTF("ENVOYER");
            dos.writeUTF(file.getName());
            dos.writeLong(file.length());

            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;
            long totalSent = 0;
            while ((bytesRead = fis.read(buffer)) != -1) {
                dos.write(buffer, 0, bytesRead);
                totalSent += bytesRead;
            }
            logArea.append("Fichier envoyé : " + file.getName() + " (" + totalSent + " octets)\n");
        }
    }

    private static void receiveFileFromServer(String fileName, JTextArea logArea) throws IOException {
        File downloadDir = new File("downloads");
        if (!downloadDir.exists() && !downloadDir.mkdirs()) {
            logArea.append("Erreur : impossible de créer le répertoire de téléchargement.\n");
            return;
        }

        File file = new File(downloadDir, "downloaded_" + fileName);
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {

            dos.writeUTF("RECEVOIR");
            dos.writeUTF(fileName);

            String response = dis.readUTF();
            if ("OK".equals(response)) {
                long fileSize = dis.readLong();

                try (FileOutputStream fos = new FileOutputStream(file)) {
                    byte[] buffer = new byte[CHUNK_SIZE];
                    long totalRead = 0;
                    int bytesRead;

                    while (totalRead < fileSize) {
                        bytesRead = dis.read(buffer);
                        fos.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                    }
                }
                logArea.append("Fichier téléchargé : " + file.getName() + " (" + fileSize + " octets)\n");
            } else {
                logArea.append("Erreur : " + response + "\n");
            }
        }
    }

    private static void listFilesFromServer(JTextArea logArea) throws IOException {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {
    
            dos.writeUTF("LISTER");
    
            String response = dis.readUTF();
            if ("OK".equals(response)) {
                String fileName;
                while (!(fileName = dis.readUTF()).isEmpty()) {
                    logArea.append(fileName + "\n");
                }
            } else {
                logArea.append(response + "\n");
            }
        }
    }
    
    private static void deleteFileOnServer(String fileName, JTextArea logArea) throws IOException {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {
    
            dos.writeUTF("SUPPRIMER");
            dos.writeUTF(fileName);
    
            String response = dis.readUTF();
            logArea.append(response + "\n");
        }
    }
}
