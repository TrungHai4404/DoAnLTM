package Server;
import java.sql.Connection;
import java.sql.DriverManager;
public class MyConnection {
    private static final String URL = "jdbc:sqlserver://192.168.57.172:1433;databaseName=ChatVideoDB;encrypt=false";
    private static final String USER = "hai"; // thay bằng user SQL Server của bạn
    private static final String PASS = "Hai@123"; // thay bằng mật khẩu thật

    public static Connection getConnection() {
        try {
            Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            return DriverManager.getConnection(URL, USER, PASS);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
