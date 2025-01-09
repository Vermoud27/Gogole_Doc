import java.io.*;
import java.util.*;
import java.io.BufferedReader;

public class DiffMerger {

    public static void mergeFiles(String file1Path, String file2Path, String outputPath) throws IOException {
        // Lire les fichiers
        List<String> file1Lines = readFile(file1Path);
        List<String> file2Lines = readFile(file2Path);

        // Calculer la fusion basée sur LCS
        List<String> mergedContent = mergeWithLCS(file1Lines, file2Lines);

        // Écrire le contenu fusionné
        writeFile(outputPath, String.join("\n", mergedContent));

        System.out.println("Fichiers fusionnés avec succès. Résultat écrit dans : " + outputPath);
    }

    private static List<String> readFile(String filePath) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line.stripTrailing()); // Supprimer les espaces inutiles
            }
        }
        return lines;
    }

    private static void writeFile(String filePath, String content) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write(content);
        }
    }

    private static List<String> mergeWithLCS(List<String> file1Lines, List<String> file2Lines) {
        int n = file1Lines.size();
        int m = file2Lines.size();

        // Calculer la LCS entre les deux fichiers
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                if (file1Lines.get(i - 1).equals(file2Lines.get(j - 1))) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }

        // Reconstruire la fusion
        List<String> mergedContent = new ArrayList<>();
        int i = n, j = m;

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && file1Lines.get(i - 1).equals(file2Lines.get(j - 1))) {
                // Ligne commune
                mergedContent.add(file1Lines.get(i - 1));
                i--;
                j--;
            } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                // Ligne ajoutée dans le fichier 2
                mergedContent.add("==== AJOUT DE L'IP ====\n" + file2Lines.get(j - 1)+"\n=======================\n");
                j--;
            } else {
                // Ligne supprimée dans le fichier 1
                mergedContent.add("==== SUPPRIMÉ ====\n" + file1Lines.get(i - 1)+"\n==================\n");
                i--;
            }
        }

        // Inverser la liste pour remettre dans l'ordre original
        Collections.reverse(mergedContent);

        return mergedContent;
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: java EnhancedDiffMerger <file1Path> <file2Path> <outputPath>");
            return;
        }

        String file1Path = args[0];
        String file2Path = args[1];
        String outputPath = args[2];

        try {
            mergeFiles(file1Path, file2Path, outputPath);
        } catch (IOException e) {
            System.err.println("Erreur lors de la fusion des fichiers : " + e.getMessage());
        }
    }
}