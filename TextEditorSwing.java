import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Comparator;
import java.io.BufferedReader;

public class TextEditorSwing extends JFrame {
    private JTabbedPane tabbedPane;
    private CopyOnWriteArrayList<TextOperation> operationLog = new CopyOnWriteArrayList<>();
    private PeerDiscovery peerDiscovery;
    private PeerCommunication peerCommunication;
    private PriorityQueue<TextOperation> operationQueue;

    // Liste pour les utilisateurs connectés et leurs adresses IP
    private DefaultListModel<String> connectedUsersListModel = new DefaultListModel<>();
    private JList<String> connectedUsersList = new JList<>(connectedUsersListModel);


    private Map<String, CursorInfo> userCursors = new HashMap<>();
    private Map<String, Color> userColors = new HashMap<>();
    private Map<JTextArea, Integer> caretPositions = new HashMap<>();

    private static class CursorInfo {
        int position;
        Color color;

        CursorInfo(int position, Color color) {
            this.position = position;
            this.color = color;
        }
    }




    public TextEditorSwing(PeerDiscovery peerDiscovery, PeerCommunication peerCommunication) {
        this.peerDiscovery = peerDiscovery;
        this.peerCommunication = peerCommunication;
        this.operationQueue = new PriorityQueue<>(Comparator.comparingLong(TextOperation::getTimestamp));

        // Initialisation de l'interface Swing
        setTitle("Éditeur de Texte P2P");
        setSize(1000, 600); // Augmenté la largeur pour accueillir la nouvelle section
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        tabbedPane = new JTabbedPane();

        openExistingFiles();

        // Ajout d'un premier onglet par défaut
        if (tabbedPane.getTabCount() == 0) {
            addNewTab("Nouveau fichier");
        }

        // Création des boutons
        JButton newTabButton = new JButton("Nouvel onglet");
        JButton saveButton = new JButton("Sauvegarder");
        newTabButton.setPreferredSize(new Dimension(150, 30));
        saveButton.setPreferredSize(new Dimension(150, 30));

        // Panel pour les boutons
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 10)); // Centrer avec espace
        buttonPanel.add(newTabButton);
        buttonPanel.add(saveButton);

        // Ajout des actions aux boutons
        newTabButton.addActionListener(e -> addNewTab("Nouveau fichier"));
        sauvegarder(saveButton);

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
                Set<String> peersSet = peerDiscovery.getPeers(); // Cela renvoie un Set<String>
                List<String> peersList = new ArrayList<>(peersSet); // Convertir en List<String>
                updateConnectedUsers(peersList); // Passer la liste au lieu du Set
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

    private Color generateRandomColor() {
        Random random = new Random();
        return new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256));
    }


    private Color getUserColor(String userId) {
        return userColors.computeIfAbsent(userId, key -> {
            Random rand = new Random();
            return new Color(rand.nextInt(256), rand.nextInt(256), rand.nextInt(256));
        });
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

    // Méthode qui met à jour la position du curseur
    private void updateUserCaret(String nodeId, int caretPosition, JTextArea textArea) {
        if (!userCursors.containsKey(nodeId)) {
            userCursors.put(nodeId, new CursorInfo(caretPosition, Color.BLUE)); // Par défaut, bleu pour l'utilisateur
        } else {
            userCursors.get(nodeId).position = caretPosition; // Met à jour la position du curseur existant
        }

        // Repeindre les curseurs après la mise à jour
        repaintCursors(textArea);
    }

    // Méthode pour ajouter un écouteur de mouvement de curseur
    private void addCaretListener(JTextArea textArea) {
        textArea.addCaretListener(e -> {
            int caretPosition = e.getDot(); // Récupère la position du curseur actuel

            // Met à jour la position du curseur pour ce JTextArea dans la map
            caretPositions.put(textArea, caretPosition);

            // Repeindre tous les curseurs (afficher les autres curseurs si nécessaire)
            repaintCursors(textArea);
        });
    }


    private void openExistingFiles() {
        try {
            // Création du dossier "file" s'il n'existe pas
            File currentDirectory = new File(".");
            File folder = new File(currentDirectory.getCanonicalFile(), "file");
            if (!folder.exists()) {
                folder.mkdir();
            }

            // Lister tous les fichiers ".txt" dans le dossier "file"
            File[] files = folder.listFiles((dir, name) -> name.endsWith(".txt"));

            if (files != null) {
                for (File file : files) {
                    // Lire le contenu du fichier
                    String fileContent = readFile(file);

                    // Générer le nom de l'onglet à partir du nom du fichier
                    String fileName = file.getName().replace(".txt", "");

                    // Créer un nouvel onglet avec le contenu du fichier
                    addTabWithContent(fileName, fileContent);
                }
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Erreur lors de l'ouverture des fichiers.", "Erreur",
                    JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private String readFile(File file) {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Erreur lors de la lecture du fichier : " + file.getName(), "Erreur",
                    JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
        return content.toString();
    }

    private void addTabWithContent(String title, String content) {
        JTextArea textArea = new JTextArea(content);
        JScrollPane scrollPane = new JScrollPane(textArea);

        // Ajouter l'onglet avec le titre et le contenu
        tabbedPane.addTab(title, scrollPane);

        addCaretListener(textArea); // Remplacez "userNodeId" par l'identifiant de l'utilisateur

        // Ajout d'un DocumentListener pour détecter les modifications
        DocumentListener listener = new DocumentListener() {
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
                int changePosition = findChangePosition(previousText, currentText);
                String changeContent = findChangeContent(previousText, currentText);

                if (changeContent != null) {
                    String operationType = currentText.length() > previousText.length() ? "INSERT" : "DELETE";
                    TextOperation operation = new TextOperation(operationType, textArea.getCaretPosition(),
                            textArea.getText(),
                            System.currentTimeMillis(), "Node-" + peerDiscovery.hashCode());
                    operationLog.add(operation);

                    // Diffuser l'opération aux autres pairs
                    for (String peer : peerDiscovery.getPeers()) {
                        peerCommunication.sendMessage(operation.toString(), peer, 5000);
                    }
                }

                previousText = currentText;

                // Diffuser la position du chariot
                int localCaretPosition = textArea.getCaretPosition();
                for (String peer : peerDiscovery.getPeers()) {
                    String caretMessage = "CARET_POSITION:" + peerDiscovery.hashCode() + ":" + localCaretPosition;
                    peerCommunication.sendMessage(caretMessage, peer, 5000);
                }
            }
        };

        textArea.getDocument().addDocumentListener(listener);

        // Ajout du menu contextuel et gestion du clic droit/double clic
        tabbedPane.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent evt) {
                if (SwingUtilities.isRightMouseButton(evt) || evt.getClickCount() == 2) {
                    int tabIndex = tabbedPane.indexAtLocation(evt.getX(), evt.getY());
                    if (tabIndex != -1) {
                        tabbedPane.setSelectedIndex(tabIndex); // Sélectionner l'onglet cliqué
                        JPopupMenu contextMenu = createContextMenu(tabIndex); // Créer le menu contextuel
                        contextMenu.show(tabbedPane, evt.getX(), evt.getY());
                    }
                }
            }
        });
    }

    private int fileCounter = 1; // Compteur global pour les fichiers

    // Méthode pour créer dynamiquement le menu contextuel
    private JPopupMenu createContextMenu(int tabIndex) 
    {
        JPopupMenu contextMenu = new JPopupMenu();

        JMenuItem renameTabItem = new JMenuItem("Renommer l'onglet");
        renameTabItem.addActionListener(e -> renameTab(tabIndex));

        JMenuItem changeColorItem = new JMenuItem("Changer la couleur");
        changeColorItem.addActionListener(e -> changeTabBackground(tabIndex));

        JMenuItem deleteTabItem = new JMenuItem("Supprimer l'onglet");
        deleteTabItem.addActionListener(e -> deleteTab(tabIndex));

        contextMenu.add(renameTabItem);
        contextMenu.add(changeColorItem);
        contextMenu.add(deleteTabItem);

        return contextMenu;
    }




    private void addNewTab(String baseTitle) {
        JTextArea textArea = new JTextArea();
        JScrollPane scrollPane = new JScrollPane(textArea);

        try {
            // Création du dossier "file" s'il n'existe pas
            File currentDirectory = new File(".");
            File folder = new File(currentDirectory.getCanonicalFile(), "file");
            if (!folder.exists()) {
                folder.mkdir();
            }

            // Génération du nom de fichier et de l'onglet
            String fileName;
            do {
                fileName = baseTitle + " (" + fileCounter + ")";
                fileCounter++;
            } while (new File(folder, fileName + ".txt").exists());

            // Créer le fichier
            File newFile = new File(folder, fileName + ".txt");
            if (newFile.createNewFile()) {
                System.out.println("Fichier créé : " + newFile.getAbsolutePath());
            } else {
                System.out.println("Erreur : le fichier n'a pas pu être créé.");
            }

            // Ajout de l'onglet avec le nom
            tabbedPane.addTab(fileName, scrollPane);

            // DocumentListener pour détecter les modifications
            DocumentListener listener = new DocumentListener() {
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
                    int changePosition = findChangePosition(previousText, currentText);
                    String changeContent = findChangeContent(previousText, currentText);

                    if (changeContent != null) {
                        String operationType = currentText.length() > previousText.length() ? "INSERT" : "DELETE";
                        TextOperation operation = new TextOperation(operationType, textArea.getCaretPosition(),
                                textArea.getText(),
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

         // Ajout du menu contextuel et gestion du clic droit/double clic
        tabbedPane.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent evt) {
                if (SwingUtilities.isRightMouseButton(evt) || evt.getClickCount() == 2) {
                    int tabIndex = tabbedPane.indexAtLocation(evt.getX(), evt.getY());
                    if (tabIndex != -1) {
                        tabbedPane.setSelectedIndex(tabIndex); // Sélectionner l'onglet cliqué
                        JPopupMenu contextMenu = createContextMenu(tabIndex); // Créer le menu contextuel
                        contextMenu.show(tabbedPane, evt.getX(), evt.getY());
                    }
                }
            }
        });

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Erreur lors de la création du fichier.", "Erreur",
                    JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    private void renameTab(int tabIndex) {
        if (tabIndex != -1) {
            String oldFileName = tabbedPane.getTitleAt(tabIndex); // Ancien nom de l'onglet
            String newName = JOptionPane.showInputDialog(this, "Entrez un nouveau nom pour l'onglet:", oldFileName);

            if (newName != null && !newName.trim().isEmpty()) {
                // Changer le titre de l'onglet
                tabbedPane.setTitleAt(tabIndex, newName);

                // Récupérer le fichier correspondant à l'ancien nom
                File oldFile = new File("file", oldFileName + ".txt");
                File newFile = new File("file", newName + ".txt");

                // Renommer le fichier si nécessaire
                if (oldFile.exists()) {
                    if (oldFile.renameTo(newFile)) {
                        System.out.println("Le fichier a été renommé avec succès.");
                    } else {
                        JOptionPane.showMessageDialog(this, "Erreur lors du renommage du fichier.", "Erreur",
                                JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }
    }

    private void deleteTab(int tabIndex) {
        if (tabIndex != -1) {
            // Récupérer le nom du fichier correspondant à l'onglet
            String fileName = tabbedPane.getTitleAt(tabIndex);

            // Supprimer l'onglet
            tabbedPane.removeTabAt(tabIndex);

            // Supprimer le fichier associé
            File fileToDelete = new File("file", fileName + ".txt");
            if (fileToDelete.exists()) {
                if (fileToDelete.delete()) {
                    System.out.println("Fichier supprimé : " + fileToDelete.getAbsolutePath());
                } else {
                    JOptionPane.showMessageDialog(this, "Erreur lors de la suppression du fichier.", "Erreur",
                            JOptionPane.ERROR_MESSAGE);
                }
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

    private void sauvegarder(JButton saveButton) {
        saveButton.addActionListener(e -> {
            int selectedTabIndex = tabbedPane.getSelectedIndex();
            if (selectedTabIndex != -1) {
                JScrollPane selectedScrollPane = (JScrollPane) tabbedPane.getComponentAt(selectedTabIndex);
                JTextArea textArea = (JTextArea) selectedScrollPane.getViewport().getView();

                // Récupérer le nom du fichier associé à l'onglet actuel
                String fileName = tabbedPane.getTitleAt(selectedTabIndex);

                // Vérifier si un fichier est déjà associé
                File fileToSave = new File("file", fileName + ".txt");

                // Sauvegarder le contenu du fichier sans ouvrir un JFileChooser
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileToSave))) {
                    writer.write(textArea.getText());
                    JOptionPane.showMessageDialog(TextEditorSwing.this,
                            "Fichier sauvegardé dans " + fileToSave.getAbsolutePath());
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(TextEditorSwing.this, "Erreur lors de la sauvegarde du fichier",
                            "Erreur", JOptionPane.ERROR_MESSAGE);
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

         // Réinitialiser les curseurs après chaque traitement
        int selectedTabIndex = tabbedPane.getSelectedIndex();
        if (selectedTabIndex != -1) {
            JScrollPane selectedScrollPane = (JScrollPane) tabbedPane.getComponentAt(selectedTabIndex);
            JTextArea textArea = (JTextArea) selectedScrollPane.getViewport().getView();
            repaintCursors(textArea);
        }
    }
    
    private void repaintCursors(JTextArea textArea) {
        textArea.getHighlighter().removeAllHighlights(); // Supprimer les anciennes surbrillances

        caretPositions.forEach((otherTextArea, caretPosition) -> {
            if (otherTextArea != textArea) { // Ne pas afficher le curseur de l'utilisateur actuel
                String userId = "Node-" + otherTextArea.hashCode(); // Identifiant unique de l'utilisateur
                Color color = getUserColor(userId); // Récupérer la couleur associée à cet utilisateur
                try {
                    // Ajouter une surbrillance à la position de chaque curseur dans le JTextArea
                    otherTextArea.getHighlighter().addHighlight(
                            caretPosition, 
                            caretPosition + 1, 
                            new DefaultHighlighter.DefaultHighlightPainter(color)
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                }
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

                // Appliquer les modifications de texte (insertion ou suppression)
                if (operation.getOperationType().equals("INSERT") 
                        || operation.getOperationType().equals("DELETE")) {
                    textArea.setText(operation.getContent());
                }

                // Mettre à jour la position du curseur pour cet utilisateur (si mouvement)
                updateUserCaret(operation.getNodeId(), operation.getPosition(), textArea);
                
                // Ajuster la position du chariot local
                String newText = textArea.getText();
                if (currentCaretPosition > operation.getPosition()) {
                    int lengthDifference = newText.length() - oldText.length();
                    currentCaretPosition += lengthDifference;
                }

                // Limiter la position du chariot au texte actuel
                if (currentCaretPosition > textArea.getText().length()) {
                    currentCaretPosition = textArea.getText().length();
                }

                if (currentCaretPosition < 0) {
                    currentCaretPosition = 0;
                }

                // Mise à jour de la position du curseur local
                textArea.setCaretPosition(currentCaretPosition);

                // Mettre à jour l'affichage des curseurs des utilisateurs
                repaintCursors(textArea);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    });
}


}
