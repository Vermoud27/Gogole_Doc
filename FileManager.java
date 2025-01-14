import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.BufferedReader;

public class FileManager {

    public static void saveToFile(String content, String filePath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write(content);
        }
    }

    public static String loadFromFile(String filePath) throws IOException {
        if (!Files.exists(Paths.get(filePath))) {
            return ""; // Retourne une chaÃ®ne vide si le fichier n'existe pas
        }

        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString().trim(); // Supprime les espaces inutiles
    }
}