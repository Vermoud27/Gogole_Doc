import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TextEditorSwing extends JFrame {
    private JTabbedPane tabbedPane;
    private CopyOnWriteArrayList<TextOperation> operationLog = new CopyOnWriteArrayList<>();
    private PeerDiscovery peerDiscovery;
    private PeerCommunication peerCommunication;
    private PriorityQueue<TextOperation> operationQueue;

    // Liste pour les utilisateurs connectés et leurs adresses IP
    private DefaultListModel<String> connectedUsersListModel = new DefaultListModel<>();
    private JList<String> connectedUsersList = new JList<>(connectedUsersListModel);

    private DocumentListener listener;

    public TextEditorSwing(PeerDiscovery peerDiscovery, PeerCommunication peerCommunication) 
    {
        this.peerDiscovery = peerDiscovery;
        this.peerCommunication = peerCommunication;
        this.operationQueue = new PriorityQueue<>(Comparator.comparingLong(TextOperation::getTimestamp));

        this.peerCommunication.setIHM(this);

        // Initialisation de l'interface Swing
        setTitle("Éditeur de Texte P2P");
        setSize(1000, 600); // Augmenté la largeur pour accueillir la nouvelle section
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        tabbedPane = new JTabbedPane();

        // Ajout d'un DocumentListener pour détecter les modifications
        this.listener = new DocumentListener() {
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
                JScrollPane selectedScrollPane = (JScrollPane) tabbedPane.getComponentAt(tabbedPane.getSelectedIndex());
                JTextArea textArea = (JTextArea) selectedScrollPane.getViewport().getView();

                String currentText = textArea.getText();
                String changeContent = findChangeContent(previousText, currentText);
                String saveFilePath = "file/" + getSelectedTabTitle() + ".txt";

                if (changeContent != null) {
                    TextOperation operation = new TextOperation( "MODIFIER", saveFilePath, textArea.getCaretPosition(), textArea.getText(),//changeContent,
                            System.currentTimeMillis(), "Node-" + peerDiscovery.hashCode());

                    envoyerMessage(operation);
                }

                previousText = currentText;

                try {
                    FileManager.saveToFile(currentText, saveFilePath);
                } catch (IOException e) {
                    System.err.println("Erreur lors de la sauvegarde : " + e.getMessage());
                }
            
                previousText = currentText;
            }
        };

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


        tabbedPane.addChangeListener(e -> {
            int selectedIndex = tabbedPane.getSelectedIndex();
            if (selectedIndex != -1) {

                System.out.println("CHANGEMENT ONGLET");

                String tabTitle = tabbedPane.getTitleAt(selectedIndex);

                JScrollPane selectedScrollPane = (JScrollPane) tabbedPane.getComponentAt(selectedIndex);
                JTextArea textArea = (JTextArea) selectedScrollPane.getViewport().getView();

                textArea.getDocument().removeDocumentListener(this.listener);
                try {
                    textArea.setText(FileManager.loadFromFile( "file/" + tabTitle + ".txt"));
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
                textArea.getDocument().addDocumentListener(this.listener);
            }
        });

        //Attendre que les connexions se trouvent
        try {
            Thread.sleep(3000);
        } catch (Exception e) {}

        String fichiers = "";
        
        //FUSION des différents fichiers
        File folder = new File("file");
        if (folder.exists() && folder.isDirectory()) {
            // Récupérer tous les fichiers dans le dossier
            File[] files = folder.listFiles((dir, name) -> name.endsWith(".txt")); // Filtrer pour ne prendre que les fichiers .txt
            
            if (files != null) {
                for (File file : files) {
                    String saveFilePath = "file/" + file.getName();
                    fichiers += file.getName() + "|";
                    String fileContent = readFile(file); // Lire le contenu du fichier
                    TextOperation operation = new TextOperation("FUSION", saveFilePath, 0, fileContent, System.currentTimeMillis(), "Node-" + peerDiscovery.hashCode());
                    
                    for (String peer : peerDiscovery.getPeers()) {
                        peerCommunication.sendMessage(operation.toString(), peer, 5000);
                        break; //envoie seulement au 1er
                    }
                }
            }
        } else {
            System.out.println("Le dossier 'file' est introuvable ou n'est pas un répertoire.");
        }

        //Envoyer la liste des fichiers pour qu'il envoie les nouveaux fichiers
        TextOperation operation = new TextOperation("LISTE", fichiers, 0, "", System.currentTimeMillis(), "Node-" + peerDiscovery.hashCode());
                    
        for (String peer : peerDiscovery.getPeers()) {
            peerCommunication.sendMessage(operation.toString(), peer, 5000);
            break; //envoie seulement au 1er
        }

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

    public void addTabWithContent(String title, String content) {
        JTextArea textArea = new JTextArea(content);
        JScrollPane scrollPane = new JScrollPane(textArea);

        // Ajouter l'onglet avec le titre et le contenu
        tabbedPane.addTab(title, scrollPane);

        textArea.getDocument().addDocumentListener(this.listener);

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
        //add(scrollPane, BorderLayout.CENTER);

        //permet la reception des messages
        

        
        

        setVisible(true);
    }

    public void envoyerMessage(TextOperation operation)
    {
        // Diffuser l'opération aux autres pairs
        for (String peer : peerDiscovery.getPeers()) {
            peerCommunication.sendMessage(operation.toString(), peer, 5000);
        }
    }

    public void recevoirMessage(TextOperation operation)
    {
        if(operation != null)
        {
            if(operation.getFichier().equals("file/" + getSelectedTabTitle() + ".txt"))
            {
                //modifier le textarea
                operationQueue.add(operation);
                processOperations();
            }
            
            //Sauvegarder dans le fichier
            try {
                FileManager.saveToFile(operation.getContent(), operation.getFichier());
            } catch (IOException e) {
                System.err.println("Erreur lors de la sauvegarde : " + e.getMessage());
            }

            String nom = FileManager.extractFileNameWithoutExtension(operation.getFichier());
            if (!isTabOpen(nom)) {
                addTabWithContent(nom, operation.getContent());
            }
        }
    }

    private boolean isTabOpen(String filePath) {
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            String tabTitle = tabbedPane.getTitleAt(i);
            if (tabTitle.equals(new File(filePath).getName())) { // Comparer avec le nom du fichier
                return true;
            }
        }
        return false;
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

            textArea.getDocument().addDocumentListener(this.listener);

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

            //Envoyer aux autres la suppression d'un fichier
            TextOperation operation = new TextOperation( "SUPPRIMER", fileToDelete.getName(), 0, "", System.currentTimeMillis(), "Node-" + peerDiscovery.hashCode());
            envoyerMessage(operation);
        }
    }

    public void supprimerFichier(String fichier) 
    {
        String filePath = fichier;
    
        // Supprimer le fichier du système de fichiers
        File file = new File(filePath);
        if (file.exists()) {
            if (file.delete()) {
                System.out.println("Fichier supprimé : " + filePath);
            } else {
                System.err.println("Impossible de supprimer le fichier : " + filePath);
                return; // Arrêter si la suppression échoue
            }
        } else {
            System.err.println("Le fichier n'existe pas : " + filePath);
            return;
        }
    
        // Supprimer l'onglet associé
        int tabIndex = findTabIndexByTitle(fichier); // Trouver l'index de l'onglet correspondant
        if (tabIndex != -1) {
            tabbedPane.remove(tabIndex); // Supprimer l'onglet
            System.out.println("Onglet supprimé : " + fichier);
        } else {
            System.err.println("Aucun onglet correspondant au fichier : " + fichier);
        }
    }
    
    // Méthode utilitaire pour trouver l'index d'un onglet par son titre
    private int findTabIndexByTitle(String title) 
    {
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            if (tabbedPane.getTitleAt(i).equals(title)) {
                return i;
            }
        }
        return -1; // Retourne -1 si aucun onglet ne correspond
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
    }

    private void applyOperation(TextOperation operation) {
        SwingUtilities.invokeLater(() -> {
            int selectedTabIndex = tabbedPane.getSelectedIndex();
            if (selectedTabIndex != -1) {
                JScrollPane selectedScrollPane = (JScrollPane) tabbedPane.getComponentAt(selectedTabIndex);
                JTextArea textArea = (JTextArea) selectedScrollPane.getViewport().getView();

            try {
                textArea.getDocument().removeDocumentListener(this.listener);

                // Récupérer l'ancien texte avant modification
                String oldText = textArea.getText();

                // Enregistrer la position actuelle du curseur
                int currentCaretPosition = textArea.getCaretPosition();
    
                // Appliquer l'opération reçue
                textArea.setText(operation.getContent());

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

                textArea.getDocument().addDocumentListener(this.listener);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        });
    }
    
    // Récupère le titre de l'onglet sélectionné
    public String getSelectedTabTitle() {
        int selectedTabIndex = tabbedPane.getSelectedIndex();
        if (selectedTabIndex != -1) {
            return tabbedPane.getTitleAt(selectedTabIndex);
        }
        return null;
    }

    // Récupère le texte de l'onglet sélectionné
    public String getSelectedTabContent() {
        int selectedTabIndex = tabbedPane.getSelectedIndex();
        if (selectedTabIndex != -1) {
            JScrollPane selectedScrollPane = (JScrollPane) tabbedPane.getComponentAt(selectedTabIndex);
            JTextArea textArea = (JTextArea) selectedScrollPane.getViewport().getView();
            return textArea.getText();
        }
        return null;
    }

    public int getSelectedTabPosition() {
        int selectedTabIndex = tabbedPane.getSelectedIndex();
        if (selectedTabIndex != -1) {
            JScrollPane selectedScrollPane = (JScrollPane) tabbedPane.getComponentAt(selectedTabIndex);
            JTextArea textArea = (JTextArea) selectedScrollPane.getViewport().getView();
            return textArea.getCaretPosition();
        }
        return 0;
    }

    
}