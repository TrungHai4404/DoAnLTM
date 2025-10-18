/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Utils;

import java.net.InetAddress;

/**
 *
 * @author LENOVO
 */
public class NetworkUtils {
    public static String getLocalIPAddress() {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            return localHost.getHostAddress();
        } catch (Exception e) {
            e.printStackTrace();
            return "127.0.0.1"; // fallback nếu lỗi
        }
    }
}
