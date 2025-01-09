import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.IOException;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.CopyOnWriteArrayList;

public class TextEditorSwing extends JFrame {
    private JTextArea textArea;
    private CopyOnWriteArrayList<TextOperation> operationLog = new CopyOnWriteArrayList<>();
    private PeerDiscovery peerDiscovery;
    private PeerCommunication peerCommunication;
    private PriorityQueue<TextOperation> operationQueue;
    private DocumentListener listener;

    private static final String SAVE_FILE_PATH = "document.txt"; // Chemin du fichier sauvegardé

    public TextEditorSwing(PeerDiscovery peerDiscovery, PeerCommunication peerCommunication) {
        this.peerDiscovery = peerDiscovery;
        this.peerCommunication = peerCommunication;
        this.operationQueue = new PriorityQueue<>(Comparator.comparingLong(TextOperation::getTimestamp));

        // Initialisation de l'interface Swing
        setTitle("Éditeur de Texte P2P");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        textArea = new JTextArea();
        JScrollPane scrollPane = new JScrollPane(textArea);

        // Charger le contenu sauvegardé au démarrage
        try {
            String savedContent = FileManager.loadFromFile(SAVE_FILE_PATH);
            textArea.setText(savedContent); // Définir le texte chargé dans l'éditeur
        } catch (IOException e) {
            System.err.println("Erreur lors du chargement du fichier : " + e.getMessage());
        }

        // Écoute des modifications locales
        listener = new DocumentListener() {
            private String previousText = "";

            public void insertUpdate(DocumentEvent e) {
                handleTextChange();
            }

            public void removeUpdate(DocumentEvent e) {
                handleTextChange();
            }

            public void changedUpdate(DocumentEvent e) {
            }

            private void handleTextChange() {
                String currentText = textArea.getText();
                String changeContent = findChangeContent(previousText, currentText);

                if (changeContent != null) {
                    TextOperation operation = new TextOperation( "MODIFIER", "FICHIER", textArea.getCaretPosition(), textArea.getText(),//changeContent,
                            System.currentTimeMillis(), "Node-" + peerDiscovery.hashCode());
                    operationLog.add(operation);

                    envoyerMessage(operation);
                }

                previousText = currentText;

                try {
                    FileManager.saveToFile(currentText, SAVE_FILE_PATH);
                } catch (IOException e) {
                    System.err.println("Erreur lors de la sauvegarde : " + e.getMessage());
                }
            
                previousText = currentText;
            }
        };

        textArea.getDocument().addDocumentListener(listener);

        add(scrollPane, BorderLayout.CENTER);

        //permet la reception des messages
        peerCommunication.setIHM(this);

        
        //Vérifier les fichiers 
        //Demander les fichiers
        //envoi de la demande de fusion des fichiers


        TextOperation operation = new TextOperation("FUSION", SAVE_FILE_PATH, 0, this.textArea.getText(), System.currentTimeMillis(), "Node-" + peerDiscovery.hashCode());
        
        for (String peer : peerDiscovery.getPeers())
        {
            peerCommunication.sendMessage(operation.toString(), peer, 5000);
            break; //envoi seulement au 1er
        }

        setVisible(true);
    }

    public void envoyerMessage(TextOperation operation)
    {
        // Diffuser l'opération aux autres pairs
        for (String peer : peerDiscovery.getPeers()) {
            peerCommunication.sendMessage(operation.toString(), peer, 5000);
        }
    }

    public void recevoirMessage(String message)
    {
        if(message != null)
        {
            TextOperation operation = TextOperation.fromString(message);
            operationQueue.add(operation);
            processOperations();
        }
    }

    private void processOperations() {
        SwingUtilities.invokeLater(() -> {
            while (!operationQueue.isEmpty()) {
                TextOperation operation = operationQueue.poll();
                applyOperation(operation);
            }
        });
    }

    private int findChangePosition(String oldValue, String newValue) {
        int position = 0;
        while (position < oldValue.length() && position < newValue.length() &&
               oldValue.charAt(position) == newValue.charAt(position)) {
            position++;
        }
        return position;
    }

    private String findChangeContent(String oldValue, String newValue) {
        if (newValue.length() > oldValue.length()) {
            return newValue.substring(findChangePosition(oldValue, newValue));
        } else if (newValue.length() < oldValue.length()) {
            return oldValue.substring(findChangePosition(oldValue, newValue));
        }
        return null;
    }

    private void applyOperation(TextOperation operation) {
        SwingUtilities.invokeLater(() -> {

            try {
                textArea.getDocument().removeDocumentListener(listener);

                // Récupérer l'ancien texte avant modification
                String oldText = textArea.getText();

                // Enregistrer la position actuelle du curseur
                int currentCaretPosition = textArea.getCaretPosition();
    
                // Appliquer l'opération reçue
                textArea.setText(operation.getContent());

                // Récupérer le nouveau texte après modification
                String newText = textArea.getText();

                // Ajuster la position du curseur si le texte a été modifié avant sa position actuelle
                if (currentCaretPosition > operation.getPosition()) {
                    int lengthDifference = newText.length() - oldText.length();

                    currentCaretPosition += lengthDifference;
                }

                // Test pos curseur
                if (currentCaretPosition > textArea.getText().length()) 
                {
                    currentCaretPosition = textArea.getText().length();
                }

                if(currentCaretPosition < 0)
                {
                    currentCaretPosition = 0;
                }
                
                textArea.setCaretPosition(currentCaretPosition);

                textArea.getDocument().addDocumentListener(listener);

            } catch (Exception e) {
                e.printStackTrace();
            }

        });
    }
    
}