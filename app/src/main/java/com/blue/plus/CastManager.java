package com.blue.plus;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CastManager {

    public interface ScanListener {
        void onDeviceFound(String name, String ip);
        void onScanFinished(List<String> devices);
    }

    public interface CastStateListener {
        void onStatusReceived(long position, long duration, boolean isPlaying, String sessionId, String title, String url);
        void onDisconnected();
    }

    private static final int PORT = 8088;
    private static final int UDP_PORT = 8089;
    private static ServerSocket serverSocket;
    private static boolean isServerRunning = false;
    private static final List<Socket> clientSockets = new ArrayList<>();
    private static ExecutorService serverExecutor;
    private static Thread udpBroadcastThread;
    private static Thread statusBroadcasterThread;

    private static Socket persistentSocket;
    private static Thread clientReadThread;
    private static boolean isClientRunning = false;
    private static final Object clientLock = new Object();
    private static ExecutorService clientExecutor = Executors.newSingleThreadExecutor();

    // Active player references (to control player on TV side)
    public static PlayerActivity activePlayer;
    
    // Target device IP & Name (on Phone side)
    public static String connectedDeviceIp = null;
    public static String connectedDeviceName = null;
    public static boolean isCasting = false;

    private static CastStateListener castStateListener;

    public static void setCastStateListener(CastStateListener listener) {
        castStateListener = listener;
    }

    public static void saveCastingState(Context context) {
        try {
            context.getSharedPreferences("CastPrefs", Context.MODE_PRIVATE).edit()
                .putBoolean("isCasting", isCasting)
                .putString("connectedDeviceIp", connectedDeviceIp)
                .putString("connectedDeviceName", connectedDeviceName)
                .apply();
        } catch (Exception e) {}
    }

    public static void loadCastingState(Context context) {
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences("CastPrefs", Context.MODE_PRIVATE);
            isCasting = prefs.getBoolean("isCasting", false);
            connectedDeviceIp = prefs.getString("connectedDeviceIp", null);
            connectedDeviceName = prefs.getString("connectedDeviceName", null);
        } catch (Exception e) {}
    }

    public static void startServer(final Context context) {
        if (isServerRunning) return;
        isServerRunning = true;
        
        serverExecutor = Executors.newSingleThreadExecutor();
        serverExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(PORT);
                    while (isServerRunning) {
                        final Socket socket = serverSocket.accept();
                        synchronized (clientSockets) {
                            clientSockets.add(socket);
                        }
                        Executors.newSingleThreadExecutor().execute(new Runnable() {
                            @Override
                            public void run() {
                                handleClientConnection(context, socket);
                            }
                        });
                    }
                } catch (Exception e) {
                    isServerRunning = false;
                }
            }
        });

        // Start UDP Broadcaster
        startUdpBroadcast(context);

        // Start TV Status Broadcaster
        startStatusBroadcaster();
    }

    public static void stopServer() {
        isServerRunning = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (Exception e) {}
        
        synchronized (clientSockets) {
            for (Socket s : clientSockets) {
                try {
                    s.close();
                } catch (Exception e) {}
            }
            clientSockets.clear();
        }
        
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
        if (udpBroadcastThread != null) {
            udpBroadcastThread.interrupt();
        }
        if (statusBroadcasterThread != null) {
            statusBroadcasterThread.interrupt();
        }
    }

    private static void handleClientConnection(final Context context, final Socket socket) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String line;
            while (isServerRunning && (line = in.readLine()) != null) {
                final String currentLine = line;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        processCommand(context, currentLine);
                    }
                });
            }
        } catch (Exception e) {
            // connection lost
        } finally {
            synchronized (clientSockets) {
                clientSockets.remove(socket);
            }
            try {
                socket.close();
            } catch (Exception e) {}
        }
    }

    private static void processCommand(final Context context, String line) {
        try {
            final String[] parts = line.split("\\|");
            final String command = parts[0];
            
            if ("PING".equals(command)) {
                // Keep-alive or verification, no action needed
            } else if ("PLAY".equals(command)) {
                String url = parts[1];
                String title = parts.length > 2 ? parts[2] : "";
                String userAgent = parts.length > 3 ? parts[3] : "";
                String referer = parts.length > 4 ? parts[4] : "";

                if (activePlayer != null) {
                    activePlayer.finish();
                }
                Intent intent = new Intent(context, PlayerActivity.class);
                intent.putExtra("url", url);
                intent.putExtra("title", title);
                intent.putExtra("user_agent", userAgent);
                intent.putExtra("referer", referer);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } else if (activePlayer != null) {
                if ("PAUSE".equals(command)) {
                    activePlayer.pauseVideo();
                } else if ("RESUME".equals(command)) {
                    activePlayer.resumeVideo();
                } else if ("SEEK".equals(command)) {
                    long pos = Long.parseLong(parts[1]);
                    activePlayer.seekToPosition(pos);
                } else if ("VOL".equals(command)) {
                    int val = Integer.parseInt(parts[1]);
                    activePlayer.setVolume(val);
                } else if ("BRIGHT".equals(command)) {
                    float val = Float.parseFloat(parts[1]);
                    activePlayer.setBrightness(val);
                } else if ("EXIT".equals(command)) {
                    activePlayer.finish();
                }
            }
        } catch (Exception e) {}
    }

    private static void startUdpBroadcast(final Context context) {
        udpBroadcastThread = new Thread(new Runnable() {
            @Override
            public void run() {
                DatagramSocket udpSocket = null;
                try {
                    udpSocket = new DatagramSocket();
                    udpSocket.setBroadcast(true);
                    
                    String deviceName = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL;
                    
                    while (isServerRunning) {
                        String localIp = getLocalIpAddress(context);
                        if (localIp != null) {
                            String message = "DISCOVER_TV|" + deviceName + "|" + localIp;
                            byte[] sendData = message.getBytes("UTF-8");
                            DatagramPacket sendPacket = new DatagramPacket(
                                sendData, sendData.length,
                                InetAddress.getByName("255.255.255.255"), UDP_PORT
                            );
                            udpSocket.send(sendPacket);
                        }
                        Thread.sleep(3000);
                    }
                } catch (Exception e) {
                    // Ignore
                } finally {
                    if (udpSocket != null) {
                        try {
                            udpSocket.close();
                        } catch (Exception e) {}
                    }
                }
            }
        });
        udpBroadcastThread.start();
    }

    private static void startStatusBroadcaster() {
        statusBroadcasterThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (isServerRunning) {
                    try {
                        Thread.sleep(1000);
                        
                        String statusMsg = null;
                        if (activePlayer != null) {
                            long pos = activePlayer.getCurrentPosition();
                            long dur = activePlayer.getDuration();
                            boolean playing = activePlayer.isPlaying();
                            String sessId = activePlayer.playbackSessionId;
                            String title = activePlayer.getVideoTitle();
                            String url = activePlayer.getVideoUrl();
                            
                            statusMsg = "STATUS|" + pos + "|" + dur + "|" + (playing ? "1" : "0") + "|" + (sessId != null ? sessId : "") + "|" + (title != null ? title : "") + "|" + (url != null ? url : "");
                        } else {
                            statusMsg = "STATUS|0|0|0|none||";
                        }
                        
                        synchronized (clientSockets) {
                            List<Socket> closedSockets = new ArrayList<>();
                            for (Socket socket : clientSockets) {
                                if (socket.isClosed()) {
                                    closedSockets.add(socket);
                                    continue;
                                }
                                try {
                                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                                    out.println(statusMsg);
                                } catch (Exception e) {
                                    closedSockets.add(socket);
                                }
                            }
                            clientSockets.removeAll(closedSockets);
                        }
                    } catch (InterruptedException ie) {
                        break;
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        });
        statusBroadcasterThread.start();
    }

    public static void connectToDevice(final Context context, final String ip, final String name) {
        disconnectFromDevice(context);
        isCasting = true;
        connectedDeviceIp = ip;
        connectedDeviceName = name;
        saveCastingState(context);
        startClientConnection(context);
    }

    public static void disconnectFromDevice(final Context context) {
        isCasting = false;
        connectedDeviceIp = null;
        connectedDeviceName = null;
        saveCastingState(context);
        
        synchronized (clientLock) {
            if (persistentSocket != null) {
                try {
                    persistentSocket.close();
                } catch (Exception e) {}
                persistentSocket = null;
            }
        }
        
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (castStateListener != null) {
                    castStateListener.onDisconnected();
                }
            }
        });
    }

    public static void startClientConnection(final Context context) {
        synchronized (clientLock) {
            if (isClientRunning) return;
            isClientRunning = true;
        }

        clientReadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (isCasting && connectedDeviceIp != null) {
                    try {
                        synchronized (clientLock) {
                            if (persistentSocket == null || persistentSocket.isClosed()) {
                                persistentSocket = new Socket();
                                persistentSocket.connect(new InetSocketAddress(connectedDeviceIp, PORT), 3000);
                            }
                        }
                        
                        BufferedReader reader = new BufferedReader(new InputStreamReader(persistentSocket.getInputStream()));
                        String line;
                        while (isCasting && (line = reader.readLine()) != null) {
                            final String currentLine = line;
                            if (currentLine.startsWith("STATUS|")) {
                                new Handler(Looper.getMainLooper()).post(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            String[] parts = currentLine.split("\\|", -1);
                                            long pos = Long.parseLong(parts[1]);
                                            long dur = Long.parseLong(parts[2]);
                                            boolean playing = "1".equals(parts[3]);
                                            String sessId = parts.length > 4 ? parts[4] : "";
                                            String title = parts.length > 5 ? parts[5] : "";
                                            String url = parts.length > 6 ? parts[6] : "";
                                            
                                            if (castStateListener != null) {
                                                castStateListener.onStatusReceived(pos, dur, playing, sessId, title, url);
                                            }
                                        } catch (Exception e) {}
                                    }
                                });
                            }
                        }
                    } catch (Exception e) {
                        // Connection lost, close socket and retry
                        synchronized (clientLock) {
                            if (persistentSocket != null) {
                                try {
                                    persistentSocket.close();
                                } catch (Exception ex) {}
                                persistentSocket = null;
                            }
                        }
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                if (castStateListener != null) {
                                    castStateListener.onDisconnected();
                                }
                            }
                        });
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException ie) {
                            break;
                        }
                    }
                }
                synchronized (clientLock) {
                    isClientRunning = false;
                }
            }
        });
        clientReadThread.start();
    }

    public static void sendCommand(final String command) {
        if (!isCasting || connectedDeviceIp == null) return;
        clientExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (clientLock) {
                        if (persistentSocket != null && !persistentSocket.isClosed()) {
                            PrintWriter out = new PrintWriter(persistentSocket.getOutputStream(), true);
                            out.println(command);
                            return;
                        }
                    }
                    // Fallback to quick short socket if persistent socket is not connected yet
                    Socket socket = new Socket();
                    socket.connect(new InetSocketAddress(connectedDeviceIp, PORT), 1500);
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    out.println(command);
                    socket.close();
                } catch (Exception e) {
                    // Ignore
                }
            }
        });
    }

    public static void scanLocalNetwork(final Context context, final ScanListener listener) {
        final List<String> foundIps = new ArrayList<>();
        final List<String> foundDevices = new ArrayList<>();
        
        final DatagramSocket[] scanSocket = {null};
        final boolean[] isScanning = {true};
        
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    scanSocket[0] = new DatagramSocket(UDP_PORT);
                    scanSocket[0].setSoTimeout(3000); // 3 seconds timeout
                    byte[] buffer = new byte[1024];
                    
                    while (isScanning[0]) {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        try {
                            scanSocket[0].receive(packet);
                            String message = new String(packet.getData(), 0, packet.getLength(), "UTF-8");
                            if (message.startsWith("DISCOVER_TV|")) {
                                String[] parts = message.split("\\|");
                                if (parts.length >= 3) {
                                    final String name = parts[1];
                                    final String ip = parts[2];
                                    
                                    synchronized (foundIps) {
                                        if (!foundIps.contains(ip)) {
                                            foundIps.add(ip);
                                            foundDevices.add(name + "|" + ip);
                                            
                                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    listener.onDeviceFound(name, ip);
                                                }
                                            });
                                        }
                                    }
                                }
                            }
                        } catch (java.net.SocketTimeoutException ste) {
                            break;
                        }
                    }
                } catch (Exception e) {
                    // Ignore
                } finally {
                    isScanning[0] = false;
                    if (scanSocket[0] != null) {
                        try {
                            scanSocket[0].close();
                        } catch (Exception e) {}
                    }
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onScanFinished(foundDevices);
                        }
                    });
                }
            }
        });
        
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isScanning[0]) {
                    isScanning[0] = false;
                    if (scanSocket[0] != null) {
                        try {
                            scanSocket[0].close();
                        } catch (Exception e) {}
                    }
                }
            }
        }, 4000);
    }

    private static String getLocalIpAddress(Context context) {
        try {
            for (java.util.Enumeration<java.net.NetworkInterface> en = java.net.NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                java.net.NetworkInterface intf = en.nextElement();
                for (java.util.Enumeration<java.net.InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    java.net.InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof java.net.Inet4Address) {
                        String ip = inetAddress.getHostAddress();
                        if (ip != null && !ip.equals("0.0.0.0")) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception ex) {}
        
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            int ipAddress = wm.getConnectionInfo().getIpAddress();
            if (ipAddress != 0) {
                return String.format("%d.%d.%d.%d", (ipAddress & 0xff), (ipAddress >> 8 & 0xff), (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
            }
        } catch (Exception e) {}
        
        return null;
    }
}
