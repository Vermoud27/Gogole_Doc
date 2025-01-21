import java.util.*;

public class DiffMerger {

    public static String mergeStrings(String str1, String str2, String ip1, String ip2) {
        // Diviser les chaînes en lignes
        List<String> lines1 = Arrays.asList(str1.split("\n"));
        List<String> lines2 = Arrays.asList(str2.split("\n"));

        // Calculer la fusion basée sur LCS
        List<String> mergedLines = mergeWithLCS(lines1, lines2, ip1, ip2);

        // Joindre les lignes fusionnées en une seule chaîne
        return String.join("\n", mergedLines);
    }

    private static List<String> mergeWithLCS(List<String> lines1, List<String> lines2, String ip1, String ip2) {
        int n = lines1.size();
        int m = lines2.size();

        // Calculer la LCS entre les deux listes de lignes
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                if (lines1.get(i - 1).equals(lines2.get(j - 1))) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }

        // Reconstruire la fusion
        List<String> mergedLines = new ArrayList<>();
        int i = n, j = m;

        StringBuilder removedBlock = new StringBuilder();
        StringBuilder addedBlock = new StringBuilder();

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && lines1.get(i - 1).equals(lines2.get(j - 1))) {
                // Ajouter les blocs précédents avant de continuer
                if (removedBlock.length() > 0) {
                    if (!mergedLines.contains("======================================")) {
                        mergedLines.add("======================================");
                    }
                    mergedLines.add(removedBlock.toString().stripTrailing());
                    if (!mergedLines.contains("==== TEXTE DE L'IP : " + ip1 + " ====")) {
                        mergedLines.add("==== TEXTE DE L'IP : " + ip1 + " ====");
                    }
                    removedBlock.setLength(0); // Réinitialiser le bloc
                }
                if (addedBlock.length() > 0) {
                    if (!mergedLines.contains("======================================")) {
                        mergedLines.add("======================================");
                    }
                    mergedLines.add(addedBlock.toString().stripTrailing());
                    if (!mergedLines.contains("==== TEXTE DE L'IP : " + ip2 + " ====")) {
                        mergedLines.add("==== TEXTE DE L'IP : " + ip2 + " ====");
                    }
                    addedBlock.setLength(0); // Réinitialiser le bloc
                }

                // Ligne commune
                mergedLines.add(lines1.get(i - 1));
                i--;
                j--;
            } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                // Ligne ajoutée dans la deuxième chaîne
                addedBlock.insert(0, lines2.get(j - 1) + "\n");
                j--;
            } else {
                // Ligne supprimée de la première chaîne
                removedBlock.insert(0, lines1.get(i - 1) + "\n");
                i--;
            }
        }

        // Ajouter les derniers blocs (s'il y en a)
        if (removedBlock.length() > 0) {
            mergedLines.add("======================================");
            mergedLines.add(removedBlock.toString().stripTrailing());
            mergedLines.add("==== TEXTE DE L'IP : " + ip1 + " ====");
        }
        if (addedBlock.length() > 0) {
            mergedLines.add("======================================");
            mergedLines.add(addedBlock.toString().stripTrailing());
            mergedLines.add("==== TEXTE DE L'IP : " + ip2 + " ====");
        }

        // Inverser la liste pour remettre dans l'ordre original
        Collections.reverse(mergedLines);

        // Supprimer les doublons d'annotations
        List<String> uniqueLines = new ArrayList<>();
        for (String line : mergedLines) {
            if (uniqueLines.isEmpty() || !uniqueLines.get(uniqueLines.size() - 1).equals(line)) {
                uniqueLines.add(line);
            }
        }
        return uniqueLines;
    }

    public static void main(String[] args) {
        // Exemple d'utilisation
        String str1 = """
                Paragraphe 1
                Paragraphe 1
                Paragraphe 1

                Paragraphe 2
                Paragraphe 2
                Paragraphe 2

                Paragraphe 3
                Paragraphe 3
                Paragraphe 3
                """;

        String str2 = """
                Paragraphe 1
                Paragraphe 1
                Paragraphe 1

                Nouveau paragraphe 22
                Nouveau paragraphe 2
                Nouveau paragraphe 2

                Paragraphe 3
                Paragraphe 3
                Paragraphe 3
                """;

        String result = mergeStrings(str1, str2, "172.16.97.45", "172.16.97.38");

        System.out.println("Text fusionné :\n");
        System.out.println(result);
    }
}