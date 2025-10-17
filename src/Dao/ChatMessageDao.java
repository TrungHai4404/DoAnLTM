package dao;

import Server.MyConnection;
import java.sql.*;

public class ChatMessageDao {

    public void saveMessage(String roomID, String userID, String message) {
        String sql = "INSERT INTO ChatMessages (RoomID, UserID, MessageText) VALUES (?, ?, ?)";
        try (Connection conn = MyConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, java.util.UUID.fromString(roomID));
            ps.setObject(2, java.util.UUID.fromString(userID));
            ps.setObject(3, message);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
