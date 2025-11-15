package baitap; 

import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;
import javax.swing.Timer;

import java.awt.*;
import com.sun.management.OperatingSystemMXBean;
import java.lang.management.*;

public class HardwareAgent extends JFrame {
    private JTextArea statusArea;
    private JLabel cpuLabel, ramLabel, diskLabel, statusLabel;
    private JProgressBar cpuBar, ramBar, diskBar;
    private Timer updateTimer;
    private Socket socket;
    private PrintWriter out;
    private String serverHost = "localhost";
    private int serverPort = 8888;
    private String agentId;
    private String zone;
    private boolean isConnected = false;
    
    private long previousCpuTime = 0;
    private long previousUpTime = 0;
    private int processorCount;
    
    private ModernChartPanel cpuChart, ramChart, diskChart;
    private LinkedList<Double> cpuHistory = new LinkedList<>();
    private LinkedList<Double> ramHistory = new LinkedList<>();
    private LinkedList<Double> diskHistory = new LinkedList<>();
    private static final int MAX_HISTORY = 60;
    
    public HardwareAgent() {
        processorCount = Runtime.getRuntime().availableProcessors();
        setupUI();
    }
    
    private void setupUI() {
        setTitle("🖥️ Hardware Agent v3.0");
        setSize(1000, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 12));
        
        tabbedPane.addTab("📊 Metrics", createMetricsPanel());
        tabbedPane.addTab("📈 Biểu đồ", createChartPanel());
        tabbedPane.addTab("📋 Log", createLogPanel());
        
        add(tabbedPane, BorderLayout.CENTER);
        
        setLocationRelativeTo(null);
    }
    
    private JPanel createMetricsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.setBackground(new Color(245, 245, 245));
        
        // Config panel
        JPanel configPanel = new JPanel(new GridLayout(4, 2, 10, 10));
        configPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(52, 152, 219), 2),
            "Cấu hình kết nối Server",
            0, 0, new Font("Segoe UI", Font.BOLD, 13), new Color(52, 152, 219)));
        configPanel.setBackground(Color.WHITE);
        
        JTextField hostField = new JTextField(serverHost);
        JTextField portField = new JTextField(String.valueOf(serverPort));
        JTextField zoneField = new JTextField("Lab-301");
        statusLabel = new JLabel("⚫ CHƯA KẾT NỐI");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        statusLabel.setForeground(Color.RED);
        statusLabel.setOpaque(true);
        statusLabel.setBackground(Color.WHITE);
        statusLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.RED, 2),
            BorderFactory.createEmptyBorder(5, 15, 5, 15)));
        
        configPanel.add(new JLabel("Server Host:"));
        configPanel.add(hostField);
        configPanel.add(new JLabel("Server Port:"));
        configPanel.add(portField);
        configPanel.add(new JLabel("Phân khu:"));
        configPanel.add(zoneField);
        configPanel.add(new JLabel("Trạng thái:"));
        configPanel.add(statusLabel);
        
        // Metrics panel
        JPanel metricsPanel = new JPanel(new GridLayout(3, 1, 10, 10));
        metricsPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(155, 89, 182), 2),
            "Thông số phần cứng (Cập nhật mỗi giây)",
            0, 0, new Font("Segoe UI", Font.BOLD, 13), new Color(155, 89, 182)));
        metricsPanel.setBackground(Color.WHITE);
        
        // CPU
        JPanel cpuPanel = new JPanel(new BorderLayout(5, 5));
        cpuPanel.setBackground(Color.WHITE);
        cpuLabel = new JLabel("CPU: ---%");
        cpuLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        cpuBar = new JProgressBar(0, 100);
        cpuBar.setStringPainted(true);
        cpuBar.setPreferredSize(new Dimension(800, 35));
        cpuBar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        cpuPanel.add(cpuLabel, BorderLayout.NORTH);
        cpuPanel.add(cpuBar, BorderLayout.CENTER);
        
        // RAM
        JPanel ramPanel = new JPanel(new BorderLayout(5, 5));
        ramPanel.setBackground(Color.WHITE);
        ramLabel = new JLabel("RAM: ---%");
        ramLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        ramBar = new JProgressBar(0, 100);
        ramBar.setStringPainted(true);
        ramBar.setPreferredSize(new Dimension(800, 35));
        ramBar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        ramPanel.add(ramLabel, BorderLayout.NORTH);
        ramPanel.add(ramBar, BorderLayout.CENTER);
        
        // Disk
        JPanel diskPanel = new JPanel(new BorderLayout(5, 5));
        diskPanel.setBackground(Color.WHITE);
        diskLabel = new JLabel("Disk: ---%");
        diskLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        diskBar = new JProgressBar(0, 100);
        diskBar.setStringPainted(true);
        diskBar.setPreferredSize(new Dimension(800, 35));
        diskBar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        diskPanel.add(diskLabel, BorderLayout.NORTH);
        diskPanel.add(diskBar, BorderLayout.CENTER);
        
        metricsPanel.add(cpuPanel);
        metricsPanel.add(ramPanel);
        metricsPanel.add(diskPanel);
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.setBackground(new Color(245, 245, 245));
        
        JButton connectBtn = new JButton("🔗 KẾT NỐI SERVER");
        JButton disconnectBtn = new JButton("🔌 NGẮT KẾT NỐI");
        JButton clearBtn = new JButton("🗑️ XÓA LOG");
        
        connectBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        disconnectBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        clearBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        
        connectBtn.setPreferredSize(new Dimension(200, 40));
        disconnectBtn.setPreferredSize(new Dimension(200, 40));
        clearBtn.setPreferredSize(new Dimension(150, 40));
        
        connectBtn.setBackground(new Color(46, 204, 113));
        connectBtn.setForeground(Color.WHITE);
        disconnectBtn.setBackground(new Color(231, 76, 60));
        disconnectBtn.setForeground(Color.WHITE);
        clearBtn.setBackground(new Color(149, 165, 166));
        clearBtn.setForeground(Color.WHITE);
        
        connectBtn.addActionListener(e -> {
            serverHost = hostField.getText();
            serverPort = Integer.parseInt(portField.getText());
            zone = zoneField.getText();
            setupConnection();
            statusLabel.setText("🟡 ĐANG KẾT NỐI...");
            statusLabel.setForeground(new Color(243, 156, 18));
            statusLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(243, 156, 18), 2),
                BorderFactory.createEmptyBorder(5, 15, 5, 15)));
        });
        
        disconnectBtn.addActionListener(e -> {
            disconnect();
            statusLabel.setText("⚫ ĐÃ NGẮT KẾT NỐI");
            statusLabel.setForeground(Color.RED);
            statusLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.RED, 2),
                BorderFactory.createEmptyBorder(5, 15, 5, 15)));
        });
        
        clearBtn.addActionListener(e -> statusArea.setText(""));
        
        buttonPanel.add(connectBtn);
        buttonPanel.add(disconnectBtn);
        buttonPanel.add(clearBtn);
        
        // Layout
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBackground(new Color(245, 245, 245));
        topPanel.add(configPanel, BorderLayout.NORTH);
        topPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(metricsPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createChartPanel() {
        JPanel panel = new JPanel(new GridLayout(3, 1, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.setBackground(new Color(245, 245, 245));
        
        cpuChart = new ModernChartPanel("CPU Usage - Máy này", new Color(231, 76, 60));
        ramChart = new ModernChartPanel("RAM Usage - Máy này", new Color(52, 152, 219));
        diskChart = new ModernChartPanel("Disk Usage - Máy này", new Color(46, 204, 113));
        
        panel.add(cpuChart);
        panel.add(ramChart);
        panel.add(diskChart);
        
        Timer chartTimer = new Timer(1000, e -> updateCharts());
        chartTimer.start();
        
        return panel;
    }
    
    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(245, 245, 245));
        
        statusArea = new JTextArea();
        statusArea.setEditable(false);
        statusArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        statusArea.setBackground(new Color(30, 30, 30));
        statusArea.setForeground(new Color(0, 255, 0));
        statusArea.setMargin(new Insets(10, 10, 10, 10));
        
        JScrollPane logScroll = new JScrollPane(statusArea);
        panel.add(logScroll, BorderLayout.CENTER);
        
        return panel;
    }
    
    private void setupConnection() {
        try {
            socket = new Socket(serverHost, serverPort);
            out = new PrintWriter(socket.getOutputStream(), true);
            agentId = InetAddress.getLocalHost().getHostName();
            isConnected = true;
            
            out.println("REGISTER|" + agentId + "|" + zone);
            addStatus("✓ Connected: " + serverHost + ":" + serverPort);
            addStatus("✓ Agent ID: " + agentId + " | Zone: " + zone);
            
            statusLabel.setText("🟢 ĐÃ KẾT NỐI");
            statusLabel.setForeground(new Color(46, 204, 113));
            statusLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(46, 204, 113), 2),
                BorderFactory.createEmptyBorder(5, 15, 5, 15)));
            
            startMonitoring();
        } catch (IOException e) {
            addStatus("✗ Connection error: " + e.getMessage());
            statusLabel.setText("🔴 LỖI KẾT NỐI");
            statusLabel.setForeground(Color.RED);
            statusLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.RED, 2),
                BorderFactory.createEmptyBorder(5, 15, 5, 15)));
            JOptionPane.showMessageDialog(this, 
                "Không thể kết nối Server!\nKiểm tra:\n1. Server đã chạy chưa?\n2. IP và Port đúng chưa?\n3. Firewall có chặn không?",
                "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void startMonitoring() {
        initializeCpuMetrics();
        
        updateTimer = new Timer(1000, e -> {
            HardwareInfo info = collectHardwareInfo();
            updateUI(info);
            sendToServer(info);
            updateHistory(info);
        });
        updateTimer.setInitialDelay(0);
        updateTimer.start();
        
        addStatus("✓ Monitoring started (1 second interval)");
    }
    
    private void initializeCpuMetrics() {
        try {
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
            
            previousCpuTime = 0;
            long[] threadIds = threadBean.getAllThreadIds();
            for (long id : threadIds) {
                long time = threadBean.getThreadCpuTime(id);
                if (time >= 0) previousCpuTime += time;
            }
            previousUpTime = runtimeBean.getUptime();
        } catch (Exception e) {}
    }
    
    private HardwareInfo collectHardwareInfo() {
        HardwareInfo info = new HardwareInfo();
        
        try {
            info.cpuUsage = getCpuUsage();
            
            OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
            long totalMem = osBean.getTotalPhysicalMemorySize();
            long freeMem = osBean.getFreePhysicalMemorySize();
            
            info.ramUsage = ((totalMem - freeMem) * 100.0) / totalMem;
            info.totalRam = totalMem / (1024.0 * 1024.0 * 1024.0);
            info.usedRam = (totalMem - freeMem) / (1024.0 * 1024.0 * 1024.0);
            
            File[] roots = File.listRoots();
            double totalSpace = 0, freeSpace = 0;
            for (File root : roots) {
                long total = root.getTotalSpace();
                long free = root.getFreeSpace();
                if (total > 0) {
                    totalSpace += total;
                    freeSpace += free;
                }
            }
            
            info.totalDisk = totalSpace / (1024.0 * 1024.0 * 1024.0);
            info.freeDisk = freeSpace / (1024.0 * 1024.0 * 1024.0);
            info.diskUsage = totalSpace > 0 ? ((totalSpace - freeSpace) / totalSpace) * 100 : 0;
            info.timestamp = System.currentTimeMillis();
            
        } catch (Exception e) {
            addStatus("✗ Collection error: " + e.getMessage());
        }
        
        return info;
    }
    
    private double getCpuUsage() {
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
            double systemCpuLoad = osBean.getSystemCpuLoad();
            if (systemCpuLoad >= 0 && systemCpuLoad <= 1) return systemCpuLoad * 100;
            
            double processCpuLoad = osBean.getProcessCpuLoad();
            if (processCpuLoad >= 0 && processCpuLoad <= 1) return processCpuLoad * 100;
            
            return calculateCpuFromThreads();
        } catch (Exception e) {
            return 0;
        }
    }
    
    private double calculateCpuFromThreads() {
        try {
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
            
            long currentCpuTime = 0;
            long[] threadIds = threadBean.getAllThreadIds();
            for (long id : threadIds) {
                long time = threadBean.getThreadCpuTime(id);
                if (time >= 0) currentCpuTime += time;
            }
            
            long currentUpTime = runtimeBean.getUptime();
            
            if (previousUpTime > 0 && currentUpTime > previousUpTime) {
                long uptimeDiff = (currentUpTime - previousUpTime) * 1000000;
                long cpuTimeDiff = currentCpuTime - previousCpuTime;
                double cpuUsage = (cpuTimeDiff * 100.0) / (uptimeDiff * processorCount);
                
                previousCpuTime = currentCpuTime;
                previousUpTime = currentUpTime;
                
                return Math.min(Math.max(cpuUsage, 0), 100);
            }
            
            previousCpuTime = currentCpuTime;
            previousUpTime = currentUpTime;
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }
    
    private void updateUI(HardwareInfo info) {
        double cpu = Math.min(Math.max(info.cpuUsage, 0), 100);
        double ram = Math.min(Math.max(info.ramUsage, 0), 100);
        double disk = Math.min(Math.max(info.diskUsage, 0), 100);
        
        cpuLabel.setText(String.format("CPU: %.2f%% (%d cores)", cpu, processorCount));
        cpuLabel.setForeground(getColorForUsage(cpu));
        cpuBar.setValue((int) cpu);
        cpuBar.setForeground(getColorForUsage(cpu));
        
        ramLabel.setText(String.format("RAM: %.2f%% (%.2f/%.2f GB)", ram, info.usedRam, info.totalRam));
        ramLabel.setForeground(getColorForUsage(ram));
        ramBar.setValue((int) ram);
        ramBar.setForeground(getColorForUsage(ram));
        
        diskLabel.setText(String.format("Disk: %.2f%% (%.2f/%.2f GB)", disk, info.totalDisk - info.freeDisk, info.totalDisk));
        diskLabel.setForeground(getColorForUsage(disk));
        diskBar.setValue((int) disk);
        diskBar.setForeground(getColorForUsage(disk));
    }
    
    private Color getColorForUsage(double usage) {
        if (usage < 50) return new Color(46, 204, 113);
        if (usage < 75) return new Color(243, 156, 18);
        return new Color(231, 76, 60);
    }
    
    private void updateHistory(HardwareInfo info) {
        cpuHistory.add(info.cpuUsage);
        ramHistory.add(info.ramUsage);
        diskHistory.add(info.diskUsage);
        
        if (cpuHistory.size() > MAX_HISTORY) cpuHistory.removeFirst();
        if (ramHistory.size() > MAX_HISTORY) ramHistory.removeFirst();
        if (diskHistory.size() > MAX_HISTORY) diskHistory.removeFirst();
    }
    
    private void updateCharts() {
        cpuChart.updateData(cpuHistory);
        ramChart.updateData(ramHistory);
        diskChart.updateData(diskHistory);
    }
    
    private void sendToServer(HardwareInfo info) {
        if (out != null && socket != null && socket.isConnected()) {
            String data = String.format("DATA|%s|%s|%d|%.2f|%.2f|%.2f|%.2f|%.2f|%.2f",
                agentId, zone, info.timestamp, 
                info.cpuUsage, info.ramUsage, info.totalRam, 
                info.diskUsage, info.totalDisk, info.freeDisk);
            out.println(data);
        }
    }
    
    private void disconnect() {
        if (updateTimer != null) {
            updateTimer.stop();
            addStatus("✓ Monitoring stopped");
        }
        try {
            if (out != null) {
                out.println("DISCONNECT|" + agentId);
                out.close();
            }
            if (socket != null) socket.close();
            isConnected = false;
            addStatus("✓ Disconnected from server");
        } catch (IOException e) {
            addStatus("✗ Disconnect error: " + e.getMessage());
        }
    }
    
    private void addStatus(String message) {
        SwingUtilities.invokeLater(() -> {
            String time = String.format("[%tH:%tM:%tS] ", System.currentTimeMillis());
            statusArea.append(time + message + "\n");
            statusArea.setCaretPosition(statusArea.getDocument().getLength());
        });
    }
    
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {}
        
        SwingUtilities.invokeLater(() -> {
            new HardwareAgent().setVisible(true);
        });
    }
}

class HardwareInfo {
    long timestamp;
    double cpuUsage, ramUsage, totalRam, usedRam;
    double diskUsage, totalDisk, freeDisk;
}

class ModernChartPanel extends JPanel {
    private String title;
    private Color primaryColor;
    private LinkedList<Double> data;
    
    public ModernChartPanel(String title, Color primaryColor) {
        this.title = title;
        this.primaryColor = primaryColor;
        setPreferredSize(new Dimension(980, 200));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)));
    }
    
    public void updateData(LinkedList<Double> newData) {
        this.data = new LinkedList<>(newData);
        repaint();
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        int width = getWidth() - 80;
        int height = getHeight() - 80;
        int x0 = 60, y0 = 40;
        
        g2.setColor(new Color(50, 50, 50));
        g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
        g2.drawString(title, x0, y0 - 20);
        
        if (data == null || data.isEmpty()) {
            g2.setColor(Color.GRAY);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            g2.drawString("Đang thu thập dữ liệu...", x0 + width/2 - 70, y0 + height/2);
            return;
        }
        
        // Grid
        g2.setColor(new Color(240, 240, 240));
        for (int i = 0; i <= 10; i++) {
            int y = y0 + (height * i / 10);
            g2.drawLine(x0, y, x0 + width, y);
            g2.setColor(new Color(120, 120, 120));
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            g2.drawString((100 - i * 10) + "%", x0 - 35, y + 4);
            g2.setColor(new Color(240, 240, 240));
        }
        
        // Area gradient
        if (data.size() > 1) {
            int[] xPoints = new int[data.size() + 2];
            int[] yPoints = new int[data.size() + 2];
            int pointGap = width / Math.max(data.size() - 1, 1);
            
            xPoints[0] = x0;
            yPoints[0] = y0 + height;
            
            for (int i = 0; i < data.size(); i++) {
                xPoints[i + 1] = x0 + i * pointGap;
                yPoints[i + 1] = y0 + height - (int)(data.get(i) * height / 100);
            }
            
            xPoints[data.size() + 1] = x0 + (data.size() - 1) * pointGap;
            yPoints[data.size() + 1] = y0 + height;
            
            GradientPaint gradient = new GradientPaint(0, y0, 
                new Color(primaryColor.getRed(), primaryColor.getGreen(), primaryColor.getBlue(), 100),
                0, y0 + height, 
                new Color(primaryColor.getRed(), primaryColor.getGreen(), primaryColor.getBlue(), 10));
            g2.setPaint(gradient);
            g2.fillPolygon(xPoints, yPoints, data.size() + 2);
        }
        
        // Line
        g2.setColor(primaryColor);
        g2.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int pointGap = width / Math.max(data.size() - 1, 1);
        for (int i = 0; i < data.size() - 1; i++) {
            int x1 = x0 + i * pointGap;
            int y1 = y0 + height - (int)(data.get(i) * height / 100);
            int x2 = x0 + (i + 1) * pointGap;
            int y2 = y0 + height - (int)(data.get(i + 1) * height / 100);
            g2.drawLine(x1, y1, x2, y2);
        }
        
        // Dots
        for (int i = 0; i < data.size(); i++) {
            int x = x0 + i * pointGap;
            int y = y0 + height - (int)(data.get(i) * height / 100);
            g2.setColor(Color.WHITE);
            g2.fillOval(x - 4, y - 4, 8, 8);
            g2.setColor(primaryColor);
            g2.fillOval(x - 3, y - 3, 6, 6);
        }
        
        // Value box
        double currentValue = data.getLast();
        double avg = data.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double max = data.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        
        int boxX = x0 + width - 160, boxY = y0, boxW = 150, boxH = 75;
        g2.setColor(new Color(255, 255, 255, 240));
        g2.fillRoundRect(boxX, boxY, boxW, boxH, 10, 10);
        g2.setColor(new Color(220, 220, 220));
        g2.drawRoundRect(boxX, boxY, boxW, boxH, 10, 10);
        
        g2.setColor(primaryColor);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 20));
        g2.drawString(String.format("%.1f%%", currentValue), boxX + 15, boxY + 32);
        
        g2.setColor(new Color(100, 100, 100));
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        g2.drawString(String.format("Avg: %.1f%%", avg), boxX + 10, boxY + 52);
        g2.drawString(String.format("Max: %.1f%%", max), boxX + 10, boxY + 65);
        
        // Axes
        g2.setColor(new Color(200, 200, 200));
        g2.setStroke(new BasicStroke(2));
        g2.drawLine(x0, y0, x0, y0 + height);
        g2.drawLine(x0, y0 + height, x0 + width, y0 + height);
    }
}