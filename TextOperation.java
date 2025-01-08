import java.io.Serializable;

public class TextOperation implements Serializable {
    private String operationType; // "INSERT" ou "DELETE"
    private int position;
    private String content;
    private long timestamp; // Horodatage pour ordonner les opérations
    private String nodeId;  // Identifiant unique du nœud

    public TextOperation(String operationType, int position, String content, long timestamp, String nodeId) {
        this.operationType = operationType;
        this.position = position;
        this.content = content;
        this.timestamp = timestamp;
        this.nodeId = nodeId;
    }

    public String getOperationType() {
        return operationType;
    }

    public int getPosition() {
        return position;
    }

    public String getContent() {
        return content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getNodeId() {
        return nodeId;
    }

    @Override
    public String toString() {
        return operationType + ":" + position + ":" + content + ":" + timestamp + ":" + nodeId;
    }

    public static TextOperation fromString(String operationString) {
        String[] parts = operationString.split(":");
        return new TextOperation(parts[0], Integer.parseInt(parts[1]), parts[2],
                Long.parseLong(parts[3]), parts[4]);
    }
}