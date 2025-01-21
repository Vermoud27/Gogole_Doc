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
    
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && lines1.get(i - 1).equals(lines2.get(j - 1))) {
                // Ligne commune
                mergedLines.add(lines1.get(i - 1));
                i--;
                j--;
            } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                // Ligne ajoutée dans la deuxième chaîne
                String lineToAdd = lines2.get(j - 1);
                if (!isAnnotation(lineToAdd) || !mergedLines.contains(lineToAdd)) {
                    mergedLines.add(lineToAdd);
                }
                j--;
            } else {
                // Ligne supprimée de la première chaîne
                String lineToRemove = lines1.get(i - 1);
                if (!isAnnotation(lineToRemove) || !mergedLines.contains(lineToRemove)) {
                    mergedLines.add(lineToRemove);
                }
                i--;
            }
        }
    
        // Inverser la liste pour remettre dans l'ordre original
        Collections.reverse(mergedLines);
    
        return mergedLines;
    }
    
    // Méthode pour vérifier si une ligne est une annotation
    private static boolean isAnnotation(String line) {
        return line.startsWith("==== TEXTE DE L'IP") || line.startsWith("======================================");
    }    

    public static void main(String[] args) {
        // Exemple d'utilisation
        String str1 = """
                Paragraphe 1
                Paragraphe 1

                Nouveau Paragraphe 2
                Nouveau Paragraphe 2
                Nouveau Paragraphe 2

                Paragraphe 3
                Paragraphe 3
                Paragraphe 3
                """;

        String str2 = """
                Paragraphe 1
                Paragraphe 1

                Nouveau Paragraphe 2
                Nouveau Paragraphe 2
                Nouveau Paragraphe 2

                Paragraphe 3
                Paragraphe 3
                Paragraphe 3
                ==== TEXTE DE L'IP : 127.0.0.1 ====
                test
                ======================================
                """;

        String result = mergeStrings(str1, str2, "172.16.97.45", "172.16.97.38");

        System.out.println("Text fusionné :\n");
        System.out.println(result);
    }
}