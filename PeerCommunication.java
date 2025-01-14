import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.*;
import java.util.Enumeration;
import java.util.HashSet;
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

    public void startListening() {
        new Thread(() -> 
        {
            System.out.println("Listening for messages on port " + port + "...");
            while (true) 
            {
                try {
                    byte[] buffer = new byte[10000000];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String senderAddress = packet.getAddress().getHostAddress();
                    String message = new String(packet.getData(), 0, packet.getLength());

                    // Ignorer les messages provenant des adresses locales
                    if (!localAddresses.contains(senderAddress)) 
                    {
                        TextOperation operation = TextOperation.fromString(message);

                        System.out.println("Received message: " + message + " from " + senderAddress);


                        if(operation.getOperationType().equals("MODIFIER"))
                        {
                            //Modifier le fichier avce le nouveau message
                            //Modifier le textArea si on est dessus

                            //envoi dy message à l'ihm
                            this.ihm.recevoirMessage(operation);
                        }

                        //Réception des messages Fusion qui n'arrive pas ??
                        if(operation.getOperationType().equals("FUSION"))
                        {
                            String text = this.getFichier(operation.getFichier());
                            
                            //fusion des fichiers
                            String fusion = DiffMerger.mergeStrings(operation.getContent(), text);

                            //Envoi du fichiers fusion aux autres
                            TextOperation operationEnvoi = new TextOperation( "MODIFIER", operation.getFichier(), 0, fusion, System.currentTimeMillis(), "Node-?" );
                        
                            this.ihm.recevoirMessage(operation);
                            this.ihm.envoyerMessage(operationEnvoi);
                            

                            //System.out.println(fusion);
                        }

                        //
                        //Récupérer ou envoiyer des fichiers aux autres
                        //
                        
                        
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
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

    /*public String receiveMessage() {
        try {
            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);

            String senderAddress = packet.getAddress().getHostAddress();
            String message = new String(packet.getData(), 0, packet.getLength());
            
            if (!localAddresses.contains(senderAddress)) {
                System.out.println("Received message: " + message + " from " + senderAddress);
                return new String(packet.getData(), 0, packet.getLength());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }*/

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