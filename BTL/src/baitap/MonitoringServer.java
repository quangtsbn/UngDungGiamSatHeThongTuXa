package baitap;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;
import javax.swing.Timer;

import java.awt.*;
import javax.swing.table.*;

public class MonitoringServer extends JFrame {
    private ServerSocket serverSocket;
    private Map<String, AgentData> agents = new ConcurrentHashMap<>();
    private PrintWriter csvWriter;
    private boolean serverRunning = false;
    private int serverPort = 8888;
    
    private JTabbedPane tabbedPane;
    private JTable agentTable;
    private DefaultTableModel tableModel;
    private JTextArea logArea;
    private JLabel serverStatusLabel;
    private JLabel totalAgentsLabel, onlineAgentsLabel, zonesLabel;
    
    private ModernChartPanel cpuChartPanel, ramChartPanel, diskChartPanel;
    private LinkedList<Double> cpuHistory = new LinkedList<>();
    private LinkedList<Double> ramHistory = new LinkedList<>();
    private LinkedList<Double> diskHistory = new LinkedList<>();
    private static final int MAX_HISTORY = 60;
    
    public MonitoringServer() {
        setupUI();
        setupCSVWriter();
        startServer();
    }
    
    private void setupUI() {
        setTitle("🖥️ Monitoring Server v3.0");
        setSize(1450, 850);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        
        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 13));
        
        tabbedPane.addTab("📊 Dashboard", createDashboardPanel());
        tabbedPane.addTab("📈 Biểu đồ Tổng hợp", createChartPanel());
        tabbedPane.addTab("📋 Server Log", createLogPanel());
        
        add(tabbedPane, BorderLayout.CENTER);
        
        Timer uiTimer = new Timer(1000, e -> {
            updateTable();
            updateCharts();
        });
        uiTimer.start();
        
        setLocationRelativeTo(null);
    }
    
    private JPanel createDashboardPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.setBackground(new Color(245, 245, 245));
        
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controlPanel.setBackground(Color.WHITE);
        controlPanel.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220)));
        
        serverStatusLabel = new JLabel("● SERVER: ĐANG KHỞI ĐỘNG...");
        serverStatusLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        serverStatusLabel.setForeground(new Color(243, 156, 18));
        serverStatusLabel.setOpaque(true);
        serverStatusLabel.setBackground(Color.WHITE);
        serverStatusLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(243, 156, 18), 2),
            BorderFactory.createEmptyBorder(5, 15, 5, 15)));
        
        JLabel portLabel = new JLabel("Port: " + serverPort);
        portLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        
        JButton stopBtn = new JButton("⏹ DỪNG SERVER");
        stopBtn.setBackground(new Color(231, 76, 60));
        stopBtn.setForeground(Color.WHITE);
        stopBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        stopBtn.setPreferredSize(new Dimension(150, 35));
        stopBtn.addActionListener(e -> stopServer());
        
        controlPanel.add(serverStatusLabel);
        controlPanel.add(Box.createHorizontalStrut(20));
        controlPanel.add(portLabel);
        controlPanel.add(Box.createHorizontalStrut(20));
        controlPanel.add(stopBtn);
        
        JPanel statsPanel = new JPanel(new GridLayout(1, 3, 15, 10));
        statsPanel.setBackground(new Color(245, 245, 245));
        statsPanel.setPreferredSize(new Dimension(1430, 90));
        
        totalAgentsLabel = createStatLabel("Tổng Agents", "0", new Color(52, 152, 219));
        onlineAgentsLabel = createStatLabel("Đang Online", "0", new Color(46, 204, 113));
        zonesLabel = createStatLabel("Số Phân khu", "0", new Color(155, 89, 182));
        
        statsPanel.add(totalAgentsLabel);
        statsPanel.add(onlineAgentsLabel);
        statsPanel.add(zonesLabel);
        
        String[] columns = {"Agent ID", "Phân khu", "CPU (%)", "RAM (%)", "RAM (GB)", 
                           "Disk (%)", "Disk (GB)", "Trạng thái", "Cập nhật"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        agentTable = new JTable(tableModel);
        agentTable.setRowHeight(32);
        agentTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        agentTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        agentTable.getTableHeader().setBackground(new Color(52, 152, 219));
        agentTable.getTableHeader().setForeground(Color.WHITE);
        agentTable.setSelectionBackground(new Color(52, 152, 219, 100));
        agentTable.setGridColor(new Color(230, 230, 230));
        
        agentTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, 
                        isSelected, hasFocus, row, column);
                
                if (!isSelected) {
                    if (row % 2 == 0) c.setBackground(Color.WHITE);
                    else c.setBackground(new Color(248, 248, 248));
                    
                    String status = (String) table.getValueAt(row, 7);
                    if ("🟢 Online".equals(status)) {
                        c.setBackground(new Color(232, 245, 233));
                    } else {
                        c.setBackground(new Color(255, 235, 238));
                    }
                }
                
                if (column >= 2 && column <= 5 && !isSelected) {
                    try {
                        double percent = Double.parseDouble(value.toString());
                        if (percent > 75) {
                            setForeground(new Color(231, 76, 60));
                            setFont(getFont().deriveFont(Font.BOLD));
                        } else if (percent > 50) {
                            setForeground(new Color(243, 156, 18));
                        } else {
                            setForeground(new Color(46, 204, 113));
                        }
                    } catch (Exception ex) {}
                } else if (!isSelected) {
                    setForeground(Color.BLACK);
                    setFont(getFont().deriveFont(Font.PLAIN));
                }
                
                return c;
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(agentTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220)));
        
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBackground(new Color(245, 245, 245));
        topPanel.add(controlPanel, BorderLayout.NORTH);
        topPanel.add(statsPanel, BorderLayout.CENTER);
        
        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createChartPanel() {
        JPanel panel = new JPanel(new GridLayout(3, 1, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.setBackground(new Color(245, 245, 245));
        
        cpuChartPanel = new ModernChartPanel("CPU Usage - Trung bình tất cả Agents", new Color(231, 76, 60));
        ramChartPanel = new ModernChartPanel("RAM Usage - Trung bình tất cả Agents", new Color(52, 152, 219));
        diskChartPanel = new ModernChartPanel("Disk Usage - Trung bình tất cả Agents", new Color(46, 204, 113));
        
        panel.add(cpuChartPanel);
        panel.add(ramChartPanel);
        panel.add(diskChartPanel);
        
        return panel;
    }
    
    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(245, 245, 245));
        
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBackground(new Color(30, 30, 30));
        logArea.setForeground(new Color(0, 255, 0));
        logArea.setMargin(new Insets(10, 10, 10, 10));
        
        JScrollPane scrollPane = new JScrollPane(logArea);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.setBackground(Color.WHITE);
        
        JButton clearBtn = new JButton("🗑️ XÓA LOG");
        JButton exportBtn = new JButton("📊 XUẤT BÁO CÁO CSV");
        
        clearBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        exportBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        
        clearBtn.setPreferredSize(new Dimension(150, 40));
        exportBtn.setPreferredSize(new Dimension(200, 40));
        
        clearBtn.setBackground(new Color(149, 165, 166));
        clearBtn.setForeground(Color.WHITE);
        exportBtn.setBackground(new Color(52, 152, 219));
        exportBtn.setForeground(Color.WHITE);
        
        clearBtn.addActionListener(e -> logArea.setText(""));
        exportBtn.addActionListener(e -> exportReport());
        
        buttonPanel.add(clearBtn);
        buttonPanel.add(exportBtn);
        
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JLabel createStatLabel(String title, String value, Color color) {
        JLabel label = new JLabel("<html><center><b style='font-size:14px'>" + title + 
                                   "</b><br><span style='font-size:32px'>" + 
                                   value + "</span></center></html>");
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setOpaque(true);
        label.setBackground(color);
        label.setForeground(Color.WHITE);
        label.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(color.darker(), 2),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        return label;
    }
    
    private void updateStatLabel(JLabel label, String title, String value) {
        label.setText("<html><center><b style='font-size:14px'>" + title + 
                     "</b><br><span style='font-size:32px'>" + 
                     value + "</span></center></html>");
    }
    
    private void setupCSVWriter() {
        try {
            String filename = "server_log_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".csv";
            csvWriter = new PrintWriter(new FileWriter(filename, true));
            csvWriter.println("Timestamp,DateTime,AgentID,Zone,CPU(%),RAM(%),TotalRAM(GB),Disk(%),TotalDisk(GB),FreeDisk(GB)");
            csvWriter.flush();
            addLog("✓ CSV file created: " + filename);
        } catch (IOException e) {
            addLog("✗ CSV error: " + e.getMessage());
        }
    }
    
    private void startServer() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(serverPort);
                serverRunning = true;
                
                SwingUtilities.invokeLater(() -> {
                    serverStatusLabel.setText("● SERVER: ONLINE");
                    serverStatusLabel.setForeground(new Color(46, 204, 113));
                    serverStatusLabel.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(46, 204, 113), 2),
                        BorderFactory.createEmptyBorder(5, 15, 5, 15)));
                });
                
                addLog("✓ Server started on port " + serverPort);
                addLog("✓ Waiting for agents...");
                
                while (serverRunning) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(new ClientHandler(clientSocket)).start();
                }
            } catch (IOException e) {
                if (serverRunning) {
                    addLog("✗ Server error: " + e.getMessage());
                }
            }
        }).start();
    }
    
    private void stopServer() {
        serverRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (csvWriter != null) {
                csvWriter.close();
            }
            
            SwingUtilities.invokeLater(() -> {
                serverStatusLabel.setText("● SERVER: ĐÃ DỪNG");
                serverStatusLabel.setForeground(Color.RED);
                serverStatusLabel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color.RED, 2),
                    BorderFactory.createEmptyBorder(5, 15, 5, 15)));
            });
            
            addLog("✓ Server stopped");
        } catch (IOException e) {
            addLog("✗ Stop error: " + e.getMessage());
        }
    }
    
    private void updateTable() {
        tableModel.setRowCount(0);
        
        Map<String, List<AgentData>> zoneGroups = new TreeMap<>();
        for (AgentData agent : agents.values()) {
            zoneGroups.computeIfAbsent(agent.zone, k -> new ArrayList<>()).add(agent);
        }
        
        int total = 0, online = 0;
        Set<String> zones = new HashSet<>();
        double totalCpu = 0, totalRam = 0, totalDisk = 0;
        int activeAgents = 0;
        
        for (Map.Entry<String, List<AgentData>> entry : zoneGroups.entrySet()) {
            zones.add(entry.getKey());
            for (AgentData agent : entry.getValue()) {
                total++;
                long timeDiff = System.currentTimeMillis() - agent.lastUpdate;
                boolean isOnline = timeDiff < 5000;
                if (isOnline) {
                    online++;
                    totalCpu += agent.cpuUsage;
                    totalRam += agent.ramUsage;
                    totalDisk += agent.diskUsage;
                    activeAgents++;
                }
                
                String status = isOnline ? "🟢 Online" : "🔴 Offline";
                String time = new SimpleDateFormat("HH:mm:ss").format(new Date(agent.lastUpdate));
                
                tableModel.addRow(new Object[]{
                    agent.agentId, agent.zone,
                    String.format("%.2f", agent.cpuUsage),
                    String.format("%.2f", agent.ramUsage),
                    String.format("%.2f", agent.totalRam),
                    String.format("%.2f", agent.diskUsage),
                    String.format("%.2f", agent.totalDisk),
                    status, time
                });
            }
        }
        
        if (activeAgents > 0) {
            cpuHistory.add(totalCpu / activeAgents);
            ramHistory.add(totalRam / activeAgents);
            diskHistory.add(totalDisk / activeAgents);
            
            if (cpuHistory.size() > MAX_HISTORY) cpuHistory.removeFirst();
            if (ramHistory.size() > MAX_HISTORY) ramHistory.removeFirst();
            if (diskHistory.size() > MAX_HISTORY) diskHistory.removeFirst();
        }
        
        updateStatLabel(totalAgentsLabel, "Tổng Agents", String.valueOf(total));
        updateStatLabel(onlineAgentsLabel, "Đang Online", String.valueOf(online));
        updateStatLabel(zonesLabel, "Số Phân khu", String.valueOf(zones.size()));
    }
    
    private void updateCharts() {
        cpuChartPanel.updateData(cpuHistory);
        ramChartPanel.updateData(ramHistory);
        diskChartPanel.updateData(diskHistory);
    }
    
    private void saveToCSV(String agentId, String zone, long timestamp, 
                          double cpu, double ram, double totalRam, 
                          double disk, double totalDisk, double freeDisk) {
        if (csvWriter != null) {
            String dateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(timestamp));
            csvWriter.printf("%d,%s,%s,%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f\n",
                timestamp, dateTime, agentId, zone, cpu, ram, totalRam, disk, totalDisk, freeDisk);
            csvWriter.flush();
        }
    }
    
    private void exportReport() {
        try {
            String filename = "report_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".csv";
            PrintWriter writer = new PrintWriter(new FileWriter(filename));
            writer.println("AgentID,Zone,CPU(%),RAM(%),RAM(GB),Disk(%),Disk(GB),Status,Time");
            
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                for (int j = 0; j < tableModel.getColumnCount(); j++) {
                    writer.print(tableModel.getValueAt(i, j));
                    if (j < tableModel.getColumnCount() - 1) writer.print(",");
                }
                writer.println();
            }
            writer.close();
            addLog("✓ Report exported: " + filename);
            JOptionPane.showMessageDialog(this, "✅ Exported: " + filename, "Success", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            addLog("✗ Export error: " + e.getMessage());
        }
    }
    
    private void addLog(String message) {
        SwingUtilities.invokeLater(() -> {
            String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
            logArea.append("[" + time + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
    
    class ClientHandler implements Runnable {
        private Socket socket;
        
        public ClientHandler(Socket socket) {
            this.socket = socket;
        }
        
        @Override
        public void run() {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String message;
                
                while ((message = in.readLine()) != null) {
                    String[] parts = message.split("\\|");
                    
                    if (parts[0].equals("REGISTER")) {
                        String agentId = parts[1];
                        String zone = parts[2];
                        agents.put(agentId, new AgentData(agentId, zone));
                        addLog("✓ Agent registered: " + agentId + " (" + zone + ")");
                        
                    } else if (parts[0].equals("DATA")) {
                        String agentId = parts[1];
                        String zone = parts[2];
                        long timestamp = Long.parseLong(parts[3]);
                        double cpu = Double.parseDouble(parts[4]);
                        double ram = Double.parseDouble(parts[5]);
                        double totalRam = Double.parseDouble(parts[6]);
                        double disk = Double.parseDouble(parts[7]);
                        double totalDisk = Double.parseDouble(parts[8]);
                        double freeDisk = Double.parseDouble(parts[9]);
                        
                        AgentData agent = agents.get(agentId);
                        if (agent != null) {
                            agent.update(cpu, ram, totalRam, disk, totalDisk, timestamp);
                        }
                        saveToCSV(agentId, zone, timestamp, cpu, ram, totalRam, disk, totalDisk, freeDisk);
                    }
                }
            } catch (IOException e) {
            } finally {
                try { socket.close(); } catch (IOException e) {}
            }
        }
    }
    
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {}
        
        SwingUtilities.invokeLater(() -> {
            new MonitoringServer().setVisible(true);
        });
    }
}

class AgentData {
    String agentId, zone;
    double cpuUsage, ramUsage, totalRam, diskUsage, totalDisk;
    long lastUpdate;
    
    public AgentData(String agentId, String zone) {
        this.agentId = agentId;
        this.zone = zone;
        this.lastUpdate = System.currentTimeMillis();
    }
    
    public void update(double cpu, double ram, double totalRam, double disk, double totalDisk, long timestamp) {
        this.cpuUsage = cpu;
        this.ramUsage = ram;
        this.totalRam = totalRam;
        this.diskUsage = disk;
        this.totalDisk = totalDisk;
        this.lastUpdate = timestamp;
    }
}

class ModernChartPanel extends JPanel {
    private String title;
    private Color primaryColor;
    private LinkedList<Double> data;
    
    public ModernChartPanel(String title, Color primaryColor) {
        this.title = title;
        this.primaryColor = primaryColor;
        setPreferredSize(new Dimension(1430, 220));
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
        g2.setFont(new Font("Segoe UI", Font.BOLD, 16));
        g2.drawString(title, x0, y0 - 20);
        
        if (data == null || data.isEmpty()) {
            g2.setColor(Color.GRAY);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            g2.drawString("Đang chờ dữ liệu từ Agents...", x0 + width/2 - 90, y0 + height/2);
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
        
        int boxX = x0 + width - 180, boxY = y0, boxW = 170, boxH = 80;
        g2.setColor(new Color(255, 255, 255, 240));
        g2.fillRoundRect(boxX, boxY, boxW, boxH, 10, 10);
        g2.setColor(new Color(220, 220, 220));
        g2.drawRoundRect(boxX, boxY, boxW, boxH, 10, 10);
        
        g2.setColor(primaryColor);
        g2.setFont(new Font("Segoe UI", Font.BOLD, 24));
        g2.drawString(String.format("%.1f%%", currentValue), boxX + 15, boxY + 35);
        
        g2.setColor(new Color(100, 100, 100));
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        g2.drawString(String.format("Avg: %.1f%%", avg), boxX + 10, boxY + 55);
        g2.drawString(String.format("Max: %.1f%%", max), boxX + 10, boxY + 70);
        
        // Axes
        g2.setColor(new Color(200, 200, 200));
        g2.setStroke(new BasicStroke(2));
        g2.drawLine(x0, y0, x0, y0 + height);
        g2.drawLine(x0, y0 + height, x0 + width, y0 + height);
    }
}