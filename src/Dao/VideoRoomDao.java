package dao;

import Server.MyConnection;
import java.awt.List;
import java.sql.*;
import java.util.ArrayList;
import java.util.UUID;
import dao.UserDao;
public class VideoRoomDao {
    
    // Tạo phòng mới
    public String createRoom(String createdByUserID) {
        String roomCode = "ROOM-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String sql = "INSERT INTO VideoRooms (RoomCode, CreatedBy) VALUES (?, ?)";
        try (Connection conn = MyConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roomCode);
            ps.setString(2, createdByUserID);
            ps.executeUpdate();
            return roomCode;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Lấy RoomID theo RoomCode
    public String getRoomIdByCode(String roomCode) {
        try (Connection conn = MyConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT RoomID FROM VideoRooms WHERE RoomCode = ?")) {
            ps.setString(1, roomCode);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("RoomID");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // Thêm thành viên vào phòng
    public void addMember(String roomCode, String userID) {
        String getRoomIdSQL = "SELECT RoomID FROM VideoRooms WHERE RoomCode = ?";
        String insertSQL = "INSERT INTO RoomMembers (RoomID, UserID) VALUES (?, ?)";

        try (Connection conn = MyConnection.getConnection();
             PreparedStatement psGet = conn.prepareStatement(getRoomIdSQL)) {

            // Lấy RoomID (UUID)
            psGet.setString(1, roomCode);
            ResultSet rs = psGet.executeQuery();
            if (!rs.next()) {
                System.out.println("❌ Không tìm thấy phòng có mã: " + roomCode);
                return;
            }

            String roomID = rs.getString("RoomID");

            // Chèn thành viên vào bảng RoomMembers
            try (PreparedStatement psInsert = conn.prepareStatement(insertSQL)) {
                psInsert.setObject(1, java.util.UUID.fromString(roomID)); // ✅ đúng kiểu UNIQUEIDENTIFIER
                psInsert.setObject(2, java.util.UUID.fromString(userID)); // ✅ đúng kiểu UNIQUEIDENTIFIER
                psInsert.executeUpdate();
            }

        } catch (IllegalArgumentException ex) {
            System.err.println("❌ roomID hoặc userID không phải UUID hợp lệ!");
            ex.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // Đánh dấu rời phòng
    public void markLeave(String username, String roomCode) {
        String sql = """
                UPDATE RoomMembers
                SET LeaveTime = GETDATE()
                WHERE UserID = (SELECT UserID FROM Users WHERE Username = ?)
                  AND RoomID = (SELECT RoomID FROM VideoRooms WHERE RoomCode = ?)
                  AND LeaveTime IS NULL
            """;

            try (Connection conn = MyConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, roomCode);
                ps.executeUpdate();
                System.out.println("✅ Đã cập nhật thời gian rời phòng cho " + username);
            } catch (Exception e) {
                e.printStackTrace();
            }
    }


    // 🔹 Lấy danh sách tên thành viên theo RoomCode
    public ArrayList<String> getMembersByRoomCode(String roomCode) {
        ArrayList<String> members = new ArrayList<>();
        String sql = """
            SELECT U.FullName, U.Username
            FROM RoomMembers RM
            JOIN VideoRooms VR ON RM.RoomID = VR.RoomID
            JOIN Users U ON RM.UserID = U.UserID
            WHERE VR.RoomCode = ?
            AND RM.LeaveTime IS NULL
        """;
        try (Connection conn = MyConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roomCode);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String fullName = rs.getString("FullName");
                String username = rs.getString("Username");
                members.add(fullName != null ? fullName + " (" + username + ")" : username);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return members;
    }
}
