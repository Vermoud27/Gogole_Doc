import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Comparator;

public class TextEditorSwing extends JFrame {
    private JTabbedPane tabbedPane;
    private CopyOnWriteArrayList<TextOperation> operationLog = new CopyOnWriteArrayList<>();
    private PeerDiscovery peerDiscovery;
    private PeerCommunication peerCommunication;
    private PriorityQueue<TextOperation> operationQueue;
    
    // Liste pour les utilisateurs connectés et leurs adresses IP
    private DefaultListModel<String> connectedUsersListModel = new DefaultListModel<>();
    private JList<String> connectedUsersList = new JList<>(connectedUsersListModel);

    public TextEditorSwing(PeerDiscovery peerDiscovery, PeerCommunication peerCommunication) {
        this.peerDiscovery = peerDiscovery;
        this.peerCommunication = peerCommunication;
        this.operationQueue = new PriorityQueue<>(Comparator.comparingLong(TextOperation::getTimestamp));

        // Initialisation de l'interface Swing
        setTitle("Éditeur de Texte P2P");
        setSize(1000, 600); // Augmenté la largeur pour accueillir la nouvelle section
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        tabbedPane = new JTabbedPane();

        // Ajout d'un premier onglet par défaut
        addNewTab("Nouveau fichier");

        // Création des boutons
        JButton newTabButton = new JButton("Nouvel onglet");
        JButton saveButton = new JButton("Exporter");
        newTabButton.setPreferredSize(new Dimension(150, 30));
        saveButton.setPreferredSize(new Dimension(150, 30));

        // Panel pour les boutons
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 10)); // Centrer avec espace
        buttonPanel.add(newTabButton);
        buttonPanel.add(saveButton);

        // Ajout des actions aux boutons
        newTabButton.addActionListener(e -> addNewTab("Nouveau fichier"));
        exporter(saveButton);

        // Panel gauche pour afficher les utilisateurs connectés
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(200, getHeight()));

        JLabel userListLabel = new JLabel("Utilisateurs Connectés:");
        leftPanel.add(userListLabel, BorderLayout.NORTH);

        JScrollPane userScrollPane = new JScrollPane(connectedUsersList);
        leftPanel.add(userScrollPane, BorderLayout.CENTER);

        // Traitement des opérations des pairs
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

        // Traitement de la découverte de nouveaux pairs
        new Thread(() -> {
            while (true) {
                Set<String> peersSet = peerDiscovery.getPeers();  // Cela renvoie un Set<String>
                List<String> peersList = new ArrayList<>(peersSet);  // Convertir en List<String>
                updateConnectedUsers(peersList);  // Passer la liste au lieu du Set
                try {
                    Thread.sleep(1000); // Pause de 1 seconde entre chaque mise à jour
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Thread.currentThread().interrupt(); // Restaurer l'état d'interruption si nécessaire
                }
            }
        }).start();

        // Ajout des composants principaux
        add(leftPanel, BorderLayout.WEST); // Ajouter le panel des utilisateurs connectés à gauche
        add(tabbedPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        setVisible(true);
    }

    // Met à jour la liste des utilisateurs connectés
    private void updateConnectedUsers(List<String> peers) {
        SwingUtilities.invokeLater(() -> {
            connectedUsersListModel.clear();
            for (String peer : peers) {
                connectedUsersListModel.addElement(peer);
            }
        });
    }

    private void addNewTab(String title) {
        JTextArea textArea = new JTextArea();
        JScrollPane scrollPane = new JScrollPane(textArea);

        // Ajout d'un DocumentListener pour chaque onglet
        DocumentListener listener = new DocumentListener() {
            private String previousText = "";

            public void insertUpdate(DocumentEvent e) {
                handleTextChange();
            }

            public void removeUpdate(DocumentEvent e) {
                handleTextChange();
            }

            public void changedUpdate(DocumentEvent e) {}

            private void handleTextChange() {
                String currentText = textArea.getText();
                int changePosition = findChangePosition(previousText, currentText);
                String changeContent = findChangeContent(previousText, currentText);

                if (changeContent != null) {
                    String operationType = currentText.length() > previousText.length() ? "INSERT" : "DELETE";
                    TextOperation operation = new TextOperation(operationType, textArea.getCaretPosition(), textArea.getText(),
                            System.currentTimeMillis(), "Node-" + peerDiscovery.hashCode());
                    operationLog.add(operation);

                    // Diffuser l'opération aux autres pairs
                    for (String peer : peerDiscovery.getPeers()) {
                        peerCommunication.sendMessage(operation.toString(), peer, 5000);
                    }
                }

                previousText = currentText;
            }
        };

        textArea.getDocument().addDocumentListener(listener);
        tabbedPane.addTab(title, scrollPane);

        // Menu contextuel pour l'onglet
        JPopupMenu contextMenu = new JPopupMenu();

        JMenuItem renameTabItem = new JMenuItem("Renommer l'onglet");
        renameTabItem.addActionListener(e -> renameTab(tabbedPane.getSelectedIndex()));

        JMenuItem changeColorItem = new JMenuItem("Changer la couleur");
        changeColorItem.addActionListener(e -> changeTabBackground(tabbedPane.getSelectedIndex()));

        contextMenu.add(renameTabItem);
        contextMenu.add(changeColorItem);

        // Ajout du menu contextuel et gestion du clic droit/double clic
        tabbedPane.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent evt) {
                if (SwingUtilities.isRightMouseButton(evt) || evt.getClickCount() == 2) {
                    int tabIndex = tabbedPane.indexAtLocation(evt.getX(), evt.getY());
                    if (tabIndex != -1) {
                        tabbedPane.setSelectedIndex(tabIndex); // Sélectionner l'onglet cliqué
                        contextMenu.show(tabbedPane, evt.getX(), evt.getY());
                    }
                }
            }
        });
    }

    private void renameTab(int tabIndex) {
        if (tabIndex != -1) {
            String newName = JOptionPane.showInputDialog(this, "Entrez un nouveau nom pour l'onglet:", 
                    tabbedPane.getTitleAt(tabIndex));
            if (newName != null && !newName.trim().isEmpty()) {
                tabbedPane.setTitleAt(tabIndex, newName);
            }
        }
    }

    private void changeTabBackground(int tabIndex) {
        if (tabIndex != -1) {
            // Afficher un sélecteur de couleur pour l'utilisateur
            Color newColor = JColorChooser.showDialog(this, "Choisissez une couleur pour l'onglet:", Color.WHITE);
            if (newColor != null) {
                // Changer la couleur de l'onglet spécifié
                tabbedPane.setBackgroundAt(tabIndex, newColor);
            }
        }
    }    

    private void exporter(JButton saveButton) {
        saveButton.addActionListener(e -> {
            int selectedTabIndex = tabbedPane.getSelectedIndex();
            if (selectedTabIndex != -1) {
                JScrollPane selectedScrollPane = (JScrollPane) tabbedPane.getComponentAt(selectedTabIndex);
                JTextArea textArea = (JTextArea) selectedScrollPane.getViewport().getView();

                JFileChooser fileChooser = new JFileChooser();
                int choixUser = fileChooser.showSaveDialog(TextEditorSwing.this);

                if (choixUser == JFileChooser.APPROVE_OPTION) {
                    File fileToSave = fileChooser.getSelectedFile();
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileToSave))) {
                        writer.write(textArea.getText());
                        JOptionPane.showMessageDialog(TextEditorSwing.this, "Fichier sauvegardé");
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(TextEditorSwing.this, "Erreur lors de la sauvegarde du fichier", "Erreur", JOptionPane.ERROR_MESSAGE);
                    }
                }
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

    private void processOperations() {
        SwingUtilities.invokeLater(() -> {
            while (!operationQueue.isEmpty()) {
                TextOperation operation = operationQueue.poll();
                applyOperation(operation);
            }
        });
    }

    private void applyOperation(TextOperation operation) {
        SwingUtilities.invokeLater(() -> {
            int selectedTabIndex = tabbedPane.getSelectedIndex();
            if (selectedTabIndex != -1) {
                JScrollPane selectedScrollPane = (JScrollPane) tabbedPane.getComponentAt(selectedTabIndex);
                JTextArea textArea = (JTextArea) selectedScrollPane.getViewport().getView();

                try {
                    String oldText = textArea.getText();
                    int currentCaretPosition = textArea.getCaretPosition();

                    if (operation.getOperationType().equals("INSERT") || operation.getOperationType().equals("DELETE")) {
                        textArea.setText(operation.getContent());
                    }

                    String newText = textArea.getText();

                    if (currentCaretPosition > operation.getPosition()) {
                        int lengthDifference = newText.length() - oldText.length();
                        currentCaretPosition += lengthDifference;
                    }

                    if (currentCaretPosition > textArea.getText().length()) {
                        currentCaretPosition = textArea.getText().length();
                    }

                    if (currentCaretPosition < 0) {
                        currentCaretPosition = 0;
                    }

                    textArea.setCaretPosition(currentCaretPosition);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
