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
                    byte[] buffer = new byte[1024];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String senderAddress = packet.getAddress().getHostAddress();
                    String message = new String(packet.getData(), 0, packet.getLength());

                    // Ignorer les messages provenant des adresses locales
                    if (!localAddresses.contains(senderAddress)) 
                    {
                        TextOperation operation = TextOperation.fromString(message);

                        if(operation.getOperationType().equals("MODIFIER"))
                        {
                            //Modifier le fichier avce le nouveau message
                            //Modifier le textArea si on est dessus

                            //envoi dy message à l'ihm
                            this.ihm.recevoirMessage(message);
                        }

                        if(operation.getOperationType().equals("FUSION"))
                        {
                            //fusion des fichiers

                            //Envoi du fichiers fusion aux autres
                        }
                        
                        
                        System.out.println("Received message: " + message + " from " + senderAddress);
                        
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public String receiveMessage() {
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
