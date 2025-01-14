import javax.swing.*;
import java.util.Set;

public class Main {
    public static void main(String[] args) {
        // Initialisation des composants réseau
        PeerDiscovery peerDiscovery = new PeerDiscovery();
        PeerCommunication peerCommunication = new PeerCommunication(5000);

        // Démarrer la découverte des pairs et la communication
        peerDiscovery.startListening();
        peerDiscovery.startBroadcastingHello();
        peerCommunication.startListening();

        // Gestion des pairs détectés (éviter le flood)
        new Thread(() -> {
            Set<String> lastKnownPeers = Set.of(); // Liste des pairs connus
            try {
                while (true) {
                    Thread.sleep(5000); // Attendre avant chaque vérification

                    Set<String> currentPeers = peerDiscovery.getPeers();
                    if (!currentPeers.equals(lastKnownPeers)) {
                        System.out.println("Pairs détectés : " + currentPeers);
                        lastKnownPeers = currentPeers; // Mettre à jour les pairs connus
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

        // Lancer l'interface graphique sur le thread Swing (EDT)
        SwingUtilities.invokeLater(() -> {
            new TextEditorSwing(peerDiscovery, peerCommunication); // Passer les objets réseau
        });
    }
}
