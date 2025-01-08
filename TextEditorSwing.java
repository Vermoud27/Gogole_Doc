import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.CopyOnWriteArrayList;

public class TextEditorSwing extends JFrame {
    private JTextArea textArea;
    private CopyOnWriteArrayList<TextOperation> operationLog = new CopyOnWriteArrayList<>();
    private PeerDiscovery peerDiscovery;
    private PeerCommunication peerCommunication;
    private PriorityQueue<TextOperation> operationQueue;

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

        // Écoute des modifications locales
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            private String previousText = "";

            public void insertUpdate(DocumentEvent e) {
                handleTextChange();
            }

            public void removeUpdate(DocumentEvent e) {
                handleTextChange();
            }

            public void changedUpdate(DocumentEvent e) {
                handleTextChange();
            }

            private void handleTextChange() {
                String currentText = textArea.getText();
                int changePosition = findChangePosition(previousText, currentText);
                String changeContent = findChangeContent(previousText, currentText);

                if (changeContent != null) {
                    String operationType = currentText.length() > previousText.length() ? "INSERT" : "DELETE";
                    TextOperation operation = new TextOperation( operationType, textArea.getCaretPosition(), textArea.getText(),//changeContent,
                            System.currentTimeMillis(), "Node-" + peerDiscovery.hashCode());
                    operationLog.add(operation);

                    // Diffuser l'opération aux autres pairs
                    for (String peer : peerDiscovery.getPeers()) {
                        peerCommunication.sendMessage(operation.toString(), peer, 5000);
                    }
                }

                previousText = currentText;
            }
        });

        add(scrollPane, BorderLayout.CENTER);

        // Recevoir les modifications des pairs
        new Thread(() -> {
            while (true) {
                String receivedMessage = peerCommunication.receiveMessage();
                if (receivedMessage != null) {
                    TextOperation operation = TextOperation.fromString(receivedMessage);
                    operationQueue.add(operation);
                    processOperations();
                }
            }
        }).start();

        setVisible(true);
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

                // Récupérer l'ancien texte avant modification
                String oldText = textArea.getText();

                // Enregistrer la position actuelle du curseur
                int currentCaretPosition = textArea.getCaretPosition();
    
                // Appliquer l'opération reçue
                if (operation.getOperationType().equals("INSERT") || operation.getOperationType().equals("DELETE")) {
                    textArea.setText(operation.getContent());
                }

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

            } catch (Exception e) {
                e.printStackTrace();
            }

        });
    }
    

     /*if (operation.getOperationType().equals("INSERT")) {
                    textArea.insert(operation.getContent(), operation.getPosition());
                } else if (operation.getOperationType().equals("DELETE")) {
                    int start = operation.getPosition();
                    int end = start + operation.getContent().length();

                    if (start >= 0 && end <= textArea.getDocument().getLength()) {
                        textArea.getDocument().remove(start, operation.getContent().length());
                    } else {
                        System.err.println("Invalid DELETE operation: " + operation);
                    }
                }*/
    
}