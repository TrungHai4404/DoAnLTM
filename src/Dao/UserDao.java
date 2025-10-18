package dao;

import Server.MyConnection;
import Utils.PasswordUtils;
import java.sql.*;
import java.util.HashSet;
import model.User;

public class UserDao{
// ✅ Kiểm tra trùng tên đăng nhập
    public boolean checkUsernameExists(String username) {
        String sql = "SELECT COUNT(*) FROM Users WHERE Username = ?";
        try (Connection conn = MyConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            rs.next();
            return rs.getInt(1) > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // ✅ Đăng ký người dùng mới
    public boolean registerUser(User user) {
        String sql = "INSERT INTO Users (Username, PasswordHash, FullName, Email) VALUES (?, ?, ?, ?)";
        try (Connection conn = MyConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user.getUsername());
            stmt.setString(2, user.getPasswordHash());
            stmt.setString(3, user.getFullName());
            stmt.setString(4, user.getEmail());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // ✅ Đăng nhập (kiểm tra username & password)
    public User login(String username, String passwordHash) {
        String sql = "SELECT * FROM Users WHERE Username = ? AND PasswordHash = ?";
        try (Connection conn = MyConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, passwordHash);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                User user = new User();
                user.setId(rs.getString("UserID"));
                user.setUsername(rs.getString("Username"));
                user.setPasswordHash(rs.getString("PasswordHash"));
                user.setFullName(rs.getString("FullName"));
                user.setEmail(rs.getString("Email"));
                return user;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
    // Lấy UserID theo Username
    public String getUserId(String username) {
        try (Connection conn = MyConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT UserID FROM Users WHERE Username = ?")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("UserID");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    // Đổi mật khẩu
    public boolean changePassword(String username, String oldPass, String newPass) {
        String selectSQL = "SELECT PasswordHash FROM Users WHERE Username = ?";
        String updateSQL = "UPDATE Users SET PasswordHash = ? WHERE Username = ?";

        try (Connection conn = MyConnection.getConnection();
             PreparedStatement psSelect = conn.prepareStatement(selectSQL);
             PreparedStatement psUpdate = conn.prepareStatement(updateSQL)) {

            // 🔹 Băm mật khẩu cũ & mới
            String oldHash = PasswordUtils.hashPassword(oldPass);
            String newHash = PasswordUtils.hashPassword(newPass);

            // 🔹 Lấy mật khẩu hiện tại trong DB
            psSelect.setString(1, username);
            ResultSet rs = psSelect.executeQuery();

            if (rs.next()) {
                String currentHash = rs.getString("PasswordHash");

                // So sánh mật khẩu cũ
                if (!currentHash.equals(oldHash)) {
                    System.out.println("❌ Mật khẩu cũ không chính xác!");
                    return false;
                }

                // Cập nhật mật khẩu mới
                psUpdate.setString(1, newHash);
                psUpdate.setString(2, username);
                int rows = psUpdate.executeUpdate();

                if (rows > 0) {
                    System.out.println("✅ Đổi mật khẩu thành công cho user: " + username);
                    return true;
                } else {
                    System.out.println("⚠️ Không có bản ghi nào được cập nhật.");
                }
            } else {
                System.out.println("⚠️ Không tìm thấy tài khoản: " + username);
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi khi đổi mật khẩu: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }
}
