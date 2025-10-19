/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Utils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

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
    public boolean checkAllServers(String serverIP) {
        boolean chatOK = false, videoOK = false, audioOK = false;

        // ✅ Kiểm tra Chat TCP Server (port 6000)
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(serverIP, 6000), 2000);
            chatOK = true;
            System.out.println("✅ Chat server OK");
        } catch (IOException e) {
            System.err.println("❌ Chat server không phản hồi");
        }

        // ✅ Kiểm tra Video UDP Server (port 5000)
        try {
            DatagramSocket udp = new DatagramSocket();
            udp.setSoTimeout(1000);

            byte[] testData = "PING_VIDEO".getBytes();
            DatagramPacket send = new DatagramPacket(testData, testData.length,
                    InetAddress.getByName(serverIP), 5000);
            udp.send(send);

            // Chờ phản hồi (nếu server phản hồi “PONG” hoặc echo lại)
            byte[] buf = new byte[64];
            DatagramPacket recv = new DatagramPacket(buf, buf.length);
            udp.receive(recv);
            String response = new String(recv.getData(), 0, recv.getLength());
            if (response.contains("PONG") || response.contains("PING")) {
                videoOK = true;
            }
            udp.close();
            System.out.println("✅ Video server OK");
        } catch (Exception e) {
            System.err.println("❌ Video server không phản hồi");
        }

        // ✅ Kiểm tra Audio UDP Server (port 5001)
        try {
            DatagramSocket udp = new DatagramSocket();
            udp.setSoTimeout(1000);

            byte[] testData = "PING_AUDIO".getBytes();
            DatagramPacket send = new DatagramPacket(testData, testData.length,
                    InetAddress.getByName(serverIP), 5001);
            udp.send(send);

            byte[] buf = new byte[64];
            DatagramPacket recv = new DatagramPacket(buf, buf.length);
            udp.receive(recv);
            String response = new String(recv.getData(), 0, recv.getLength());
            if (response.contains("PONG") || response.contains("PING")) {
                audioOK = true;
            }
            udp.close();
            System.out.println("✅ Audio server OK");
        } catch (Exception e) {
            System.err.println("❌ Audio server không phản hồi");
        }

        return chatOK && videoOK && audioOK;
    }

}
