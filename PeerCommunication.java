import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PeerCommunication {
    private final int port;
    private DatagramSocket socket;
    private Set<String> localAddresses;
    private TextEditorSwing ihm;

    public PeerCommunication(int port) {
        this.port = port;
        try {
            this.socket = new DatagramSocket(port);
            this.localAddresses = getLocalIPAddresses(); // Récupérer toutes les adresses IP locales
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Set<String> getLocalIPAddresses() throws SocketException {
        Set<String> addresses = new HashSet<>();
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface iface = interfaces.nextElement();
            if (iface.isLoopback() || !iface.isUp()) {
                continue; // Ignorer les interfaces non actives ou en boucle locale
            }
            Enumeration<InetAddress> inetAddresses = iface.getInetAddresses();
            while (inetAddresses.hasMoreElements()) {
                InetAddress addr = inetAddresses.nextElement();
                addresses.add(addr.getHostAddress());
            }
        }
        return addresses;
    }

    public void startListening() 
    {
        new Thread(() -> 
        {
            System.out.println("Listening for messages on port " + port + "...");
            while (true) 
            {
                try 
                {
                    byte[] buffer = new byte[10_000_000];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String senderAddress = packet.getAddress().getHostAddress();
                    String message = new String(packet.getData(), 0, packet.getLength());

                    // Ignorer les messages provenant des adresses locales
                    if (!localAddresses.contains(senderAddress) && this.ihm != null) 
                    {
                        System.out.println("Received message: " + message + " from " + senderAddress);

                        TextOperation operation = TextOperation.fromString(message);

                        if(operation.getOperationType().equals("MODIFIER"))
                        {
                            //envoi dy message à l'ihm
                            this.ihm.recevoirMessage(operation);
                        }

                        if(operation.getOperationType().equals("SUPPRIMER"))
                        {
                            this.ihm.supprimerFichier(operation.getFichier());
                        }

                        if(operation.getOperationType().equals("RENOMMER"))
                        {
                            this.ihm.renommerFichier( operation.getContent(), operation.getFichier());
                        }

                        if (operation.getOperationType().equals("LISTE")) 
                        {
                            // Liste des fichiers reçus
                            String[] receivedFileNames = operation.getContent().split("|"); // Supposons que les noms sont séparés par des virgules

                            // Liste des fichiers locaux
                            List<String> localFiles = getLocalFileNames();

                            // Identifier les fichiers manquants
                            List<String> extraLocalFiles = new ArrayList<>();
                            for (String localFile : localFiles) {
                                if (!Arrays.asList(receivedFileNames).contains(localFile)) {
                                    extraLocalFiles.add(localFile); // Ajouter les fichiers locaux qui ne sont pas dans la liste reçue
                                }
                            }

                            // Envoyer les fichiers manquants au pair
                            for (String missingFile : extraLocalFiles) {
                                String filePath = "file/" + missingFile;
                                String fileContent = "";

                                try {
                                    fileContent = getFichier(filePath); // Lire le contenu du fichier local manquant
                                } catch (IOException e) {
                                    System.err.println("Erreur lors de la lecture du fichier : " + e.getMessage());
                                }

                                // Créer une opération de type FUSION pour envoyer le fichier manquant
                                TextOperation operation2 = new TextOperation(
                                    "MODIFIER",
                                    filePath,
                                    0,
                                    fileContent,
                                    System.currentTimeMillis(),
                                    "Node-"
                                );

                                // Envoyer au pair
                                this.ihm.envoyerMessage(operation2);
                            }
                        }


                        //Réception des messages Fusion qui n'arrive pas ??
                        if(operation.getOperationType().equals("FUSION"))
                        {
                            String filePath = operation.getFichier();

                            // Vérifier si le fichier existe localement
                            File file = new File(filePath);
                            if (!file.exists()) 
                            {
                                try {
                                    // Si le fichier n'existe pas, le créer et sauvegarder le contenu
                                    FileManager.saveToFile(operation.getContent(), filePath);

                                    //Permettre d'ouvrir l'onglet 
                                    TextOperation operationEnvoi = new TextOperation( "MODIFIER", operation.getFichier(), 0, operation.getContent(), System.currentTimeMillis(), "Node-?" );
                                    this.ihm.recevoirMessage(operationEnvoi);

                                } catch (IOException e) {
                                    System.err.println("Erreur lors de la sauvegarde du fichier : " + e.getMessage());
                                }
                            }
                            else 
                            {
                                String text = this.getFichier(operation.getFichier());

                                String ip = "";
                                for (String addr : this.localAddresses) {
                                    ip = addr;
                                    break;
                                }
                            
                                //fusion des fichiers
                                String fusion = DiffMerger2.mergeStrings(operation.getContent(), text, operation.getNodeId(), ip );
    
                                //Envoi du fichiers fusion aux autres
                                TextOperation operationEnvoi = new TextOperation( "MODIFIER", operation.getFichier(), 0, fusion, System.currentTimeMillis(), "Node-?" );
                            
                                this.ihm.recevoirMessage(operationEnvoi);
                                this.ihm.envoyerMessage(operationEnvoi);
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private List<String> getLocalFileNames() {
        File folder = new File("file");
        List<String> fileNames = new ArrayList<>();
        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles((dir, name) -> name.endsWith(".txt"));
            if (files != null) {
                for (File file : files) {
                    fileNames.add(file.getName()); // Récupère uniquement le nom du fichier
                }
            }
        }
        return fileNames;
    }

    public boolean adresseLocal(String addr)
    {
        return localAddresses.contains(addr);
    }
    

    public String getFichier(String filePath) throws IOException
    {
        String text = "";

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {

            String line; 
            while ((line = reader.readLine()) != null) 
            {
                text += line + "\n";
            }
        }

        return text;
    }

    public void sendMessage(String message, String ipAddress, int targetPort) {
        new Thread(() -> {
            try {
                byte[] buffer = message.getBytes();
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length,
                        InetAddress.getByName(ipAddress), targetPort);

                socket.send(packet);
                System.out.println("Message envoyé : " + message + " vers " + ipAddress + ":" + targetPort);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void setIHM(TextEditorSwing ihm)
    {
        this.ihm = ihm;
    }
}