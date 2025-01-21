import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;

public class PeerDiscovery {
    private static final String MULTICAST_GROUP = "230.0.0.0";
    private static final int MULTICAST_PORT = 4446;
    private final Set<String> peers = new HashSet<>();
    private boolean running = true;

    public String getLocalIP() throws UnknownHostException
    {
        InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
        return InetAddress.getLocalHost().getHostAddress();
    }

    public void startListening() {
        new Thread(() -> {
            try (MulticastSocket socket = new MulticastSocket(MULTICAST_PORT)) {
                InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
                socket.joinGroup(group);

                String localAddress = InetAddress.getLocalHost().getHostAddress();
                System.out.println("Listening for peers...");
                while (running) {
                    byte[] buffer = new byte[1024];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String message = new String(packet.getData(), 0, packet.getLength());
                    String senderAddress = packet.getAddress().getHostAddress();

                    // Ignore messages from self
                    if (!senderAddress.equals(localAddress) && message.startsWith("HELLO")) {
                        // Ajouter uniquement si c'est une nouvelle IP détectée
                        if (peers.add(senderAddress)) {
                            System.out.println("Discovered peer: " + senderAddress);
                        }
                    }
                }
                socket.leaveGroup(group);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void startBroadcastingHello() {
        new Thread(() -> {
            try (MulticastSocket socket = new MulticastSocket()) {
                InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
                String message = "HELLO";

                while (running) {
                    byte[] buffer = message.getBytes();
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, MULTICAST_PORT);
                    socket.send(packet);
                    Thread.sleep(3000); // Diffuse toutes les 3 secondes
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public Set<String> getPeers() {
        return new HashSet<>(peers); // Retourner une copie immuable de la liste des pairs
    }

    public void stop() {
        running = false;
    }
}
