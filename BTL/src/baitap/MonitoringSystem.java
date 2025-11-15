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
import java.lang.management.*;
import com.sun.management.OperatingSystemMXBean;

public class MonitoringSystem extends JFrame {
    // Server components
    private ServerSocket serverSocket;
    private Map<String, AgentData> agents = new ConcurrentHashMap<>();
    private PrintWriter csvWriter;
    private boolean serverRunning = false;
    private int serverPort = 8888;
    
    // Agent components (local)
    private Timer localAgentTimer;
    private String localAgentId;
    private String localZone = "Local-Machine";
    private int processorCount;
    private long previousCpuTime = 0;
    private long previousUpTime = 0;
    
    // UI components
    private JTabbedPane tabbedPane;
    private JTable agentTable;
    private DefaultTableModel tableModel;
    private JTextArea logArea;
    private JLabel serverStatusLabel;
    private JLabel totalAgentsLabel, onlineAgentsLabel, zonesLabel;
    
    // Chart components
    private ChartPanel cpuChartPanel, ramChartPanel, diskChartPanel;
    private LinkedList<Double> cpuHistory = new LinkedList<>();
    private LinkedList<Double> ramHistory = new LinkedList<>();
    private LinkedList<Double> diskHistory = new LinkedList<>();
    private static final int MAX_HISTORY = 60; // 60 giây
    
    public MonitoringSystem() {
        processorCount = Runtime.getRuntime().availableProcessors();
        try {
            localAgentId = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            localAgentId = "LocalPC";
        }
        
        setupUI();
        setupCSVWriter();
        startLocalAgent();
        startServer();
    }
    
    private void setupUI() {
        setTitle("Hardware Monitoring System - All-in-One v3.0");
        setSize(1400, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        
        // Tab pane
        tabbedPane = new JTabbedPane();
        
        // Tab 1: Dashboard + Server
        JPanel dashboardPanel = createDashboardPanel();
        tabbedPane.addTab("📊 Dashboard", dashboardPanel);
        
        // Tab 2: Biểu đồ thời gian thực
        JPanel chartPanel = createChartPanel();
        tabbedPane.addTab("📈 Biểu đồ Real-time", chartPanel);
        
        // Tab 3: Local Agent
        JPanel localAgentPanel = createLocalAgentPanel();
        tabbedPane.addTab("💻 Local Agent", localAgentPanel);
        
        // Tab 4: Log
        JPanel logPanel = createLogPanel();
        tabbedPane.addTab("📋 System Log", logPanel);
        
        add(tabbedPane, BorderLayout.CENTER);
        
        // Timer cập nhật UI
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
        
        // Control panel
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        serverStatusLabel = new JLabel("● Server: Đang khởi động...");
        serverStatusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        serverStatusLabel.setForeground(new Color(243, 156, 18));
        
        JLabel portLabel = new JLabel("Port: " + serverPort);
        portLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        
        controlPanel.add(serverStatusLabel);
        controlPanel.add(Box.createHorizontalStrut(20));
        controlPanel.add(portLabel);
        
        // Stats panel
        JPanel statsPanel = new JPanel(new GridLayout(1, 3, 15, 10));
        statsPanel.setPreferredSize(new Dimension(1380, 80));
        
        totalAgentsLabel = createStatLabel("Tổng Agents", "0", new Color(52, 152, 219));
        onlineAgentsLabel = createStatLabel("Đang Online", "0", new Color(46, 204, 113));
        zonesLabel = createStatLabel("Số Phân khu", "0", new Color(155, 89, 182));
        
        statsPanel.add(totalAgentsLabel);
        statsPanel.add(onlineAgentsLabel);
        statsPanel.add(zonesLabel);
        
        // Table
        String[] columns = {"Agent ID", "Phân khu", "CPU (%)", "RAM (%)", "RAM (GB)", 
                           "Disk (%)", "Disk (GB)", "Trạng thái", "Cập nhật"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        agentTable = new JTable(tableModel);
        agentTable.setRowHeight(30);
        agentTable.setFont(new Font("Arial", Font.PLAIN, 12));
        agentTable.getTableHeader().setFont(new Font("Arial", Font.BOLD, 12));
        
        agentTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, 
                        isSelected, hasFocus, row, column);
                
                if (!isSelected) {
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
                        if (percent > 75) setForeground(new Color(231, 76, 60));
                        else if (percent > 50) setForeground(new Color(243, 156, 18));
                        else setForeground(new Color(46, 204, 113));
                    } catch (Exception ex) {}
                }
                
                return c;
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(agentTable);
        
        // Layout
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.add(controlPanel, BorderLayout.NORTH);
        topPanel.add(statsPanel, BorderLayout.CENTER);
        
        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createChartPanel() {
        JPanel panel = new JPanel(new GridLayout(3, 1, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        cpuChartPanel = new ChartPanel("CPU Usage (%)", Color.RED);
        ramChartPanel = new ChartPanel("RAM Usage (%)", Color.BLUE);
        diskChartPanel = new ChartPanel("Disk Usage (%)", Color.GREEN);
        
        panel.add(cpuChartPanel);
        panel.add(ramChartPanel);
        panel.add(diskChartPanel);
        
        return panel;
    }
    
    private JPanel createLocalAgentPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JPanel metricsPanel = new JPanel(new GridLayout(3, 1, 10, 10));
        metricsPanel.setBorder(BorderFactory.createTitledBorder("Local Machine Metrics"));
        
        JLabel cpuLabel = new JLabel("CPU: ---%");
        JLabel ramLabel = new JLabel("RAM: ---%");
        JLabel diskLabel = new JLabel("Disk: ---%");
        
        cpuLabel.setFont(new Font("Arial", Font.BOLD, 18));
        ramLabel.setFont(new Font("Arial", Font.BOLD, 18));
        diskLabel.setFont(new Font("Arial", Font.BOLD, 18));
        
        metricsPanel.add(cpuLabel);
        metricsPanel.add(ramLabel);
        metricsPanel.add(diskLabel);
        
        panel.add(metricsPanel, BorderLayout.NORTH);
        
        return panel;
    }
    
    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        logArea.setBackground(new Color(43, 43, 43));
        logArea.setForeground(new Color(230, 230, 230));
        
        JScrollPane scrollPane = new JScrollPane(logArea);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton clearBtn = new JButton("Xóa Log");
        JButton exportBtn = new JButton("Xuất báo cáo CSV");
        
        clearBtn.addActionListener(e -> logArea.setText(""));
        exportBtn.addActionListener(e -> exportReport());
        
        buttonPanel.add(clearBtn);
        buttonPanel.add(exportBtn);
        
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JLabel createStatLabel(String title, String value, Color color) {
        JLabel label = new JLabel("<html><center><b>" + title + "</b><br><font size='6'>" + 
                                   value + "</font></center></html>");
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setOpaque(true);
        label.setBackground(color);
        label.setForeground(Color.WHITE);
        label.setBorder(BorderFactory.createLineBorder(color.darker(), 2));
        return label;
    }
    
    private void updateStatLabel(JLabel label, String title, String value) {
        label.setText("<html><center><b>" + title + "</b><br><font size='6'>" + 
                     value + "</font></center></html>");
    }
    
    private void setupCSVWriter() {
        try {
            String filename = "hardware_log_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".csv";
            csvWriter = new PrintWriter(new FileWriter(filename, true));
            csvWriter.println("Timestamp,DateTime,AgentID,Zone,CPU(%),RAM(%),TotalRAM(GB),Disk(%),TotalDisk(GB),FreeDisk(GB)");
            csvWriter.flush();
            addLog("✓ File CSV: " + filename);
        } catch (IOException e) {
            addLog("✗ Lỗi tạo CSV: " + e.getMessage());
        }
    }
    
    private void startServer() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(serverPort);
                serverRunning = true;
                
                SwingUtilities.invokeLater(() -> {
                    serverStatusLabel.setText("● Server: Online");
                    serverStatusLabel.setForeground(new Color(46, 204, 113));
                });
                
                addLog("✓ Server khởi động: port " + serverPort);
                
                while (serverRunning) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(new ClientHandler(clientSocket)).start();
                }
            } catch (IOException e) {
                if (serverRunning) {
                    addLog("✗ Lỗi server: " + e.getMessage());
                }
            }
        }).start();
    }
    
    private void startLocalAgent() {
        initializeCpuMetrics();
        
        localAgentTimer = new Timer(1000, e -> {
            HardwareInfo info = collectLocalHardwareInfo();
            
            // Thêm vào danh sách agents
            AgentData localAgent = agents.get(localAgentId);
            if (localAgent == null) {
                localAgent = new AgentData(localAgentId, localZone);
                agents.put(localAgentId, localAgent);
            }
            localAgent.update(info.cpuUsage, info.ramUsage, info.totalRam, 
                            info.diskUsage, info.totalDisk, info.timestamp);
            
            // Lưu CSV
            saveToCSV(localAgentId, localZone, info.timestamp, info.cpuUsage, 
                     info.ramUsage, info.totalRam, info.diskUsage, info.totalDisk, info.freeDisk);
            
            // Cập nhật lịch sử cho biểu đồ
            updateHistory(info);
        });
        localAgentTimer.start();
        
        addLog("✓ Local Agent khởi động: " + localAgentId);
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
    
    private HardwareInfo collectLocalHardwareInfo() {
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
            addLog("✗ Lỗi thu thập: " + e.getMessage());
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
            
            double loadAverage = osBean.getSystemLoadAverage();
            if (loadAverage >= 0) return Math.min((loadAverage / processorCount) * 100, 100);
            
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
    
    private void updateHistory(HardwareInfo info) {
        cpuHistory.add(info.cpuUsage);
        ramHistory.add(info.ramUsage);
        diskHistory.add(info.diskUsage);
        
        if (cpuHistory.size() > MAX_HISTORY) cpuHistory.removeFirst();
        if (ramHistory.size() > MAX_HISTORY) ramHistory.removeFirst();
        if (diskHistory.size() > MAX_HISTORY) diskHistory.removeFirst();
    }
    
    private void updateTable() {
        tableModel.setRowCount(0);
        
        Map<String, List<AgentData>> zoneGroups = new TreeMap<>();
        for (AgentData agent : agents.values()) {
            zoneGroups.computeIfAbsent(agent.zone, k -> new ArrayList<>()).add(agent);
        }
        
        int total = 0, online = 0;
        Set<String> zones = new HashSet<>();
        
        for (Map.Entry<String, List<AgentData>> entry : zoneGroups.entrySet()) {
            zones.add(entry.getKey());
            for (AgentData agent : entry.getValue()) {
                total++;
                long timeDiff = System.currentTimeMillis() - agent.lastUpdate;
                boolean isOnline = timeDiff < 5000;
                if (isOnline) online++;
                
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
            addLog("✓ Xuất báo cáo: " + filename);
            JOptionPane.showMessageDialog(this, "Xuất thành công: " + filename);
        } catch (IOException e) {
            addLog("✗ Lỗi xuất: " + e.getMessage());
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
                        addLog("✓ Agent: " + agentId + " (" + zone + ")");
                        
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
            new MonitoringSystem().setVisible(true);
        });
    }
}

class HardwareInfo {
    long timestamp;
    double cpuUsage, ramUsage, totalRam, usedRam;
    double diskUsage, totalDisk, freeDisk;
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

class ChartPanel extends JPanel {
    private String title;
    private Color color;
    private LinkedList<Double> data;
    
    public ChartPanel(String title, Color color) {
        this.title = title;
        this.color = color;
        setPreferredSize(new Dimension(1380, 200));
        setBorder(BorderFactory.createTitledBorder(title));
        setBackground(Color.WHITE);
    }
    
    public void updateData(LinkedList<Double> newData) {
        this.data = new LinkedList<>(newData);
        repaint();
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        if (data == null || data.isEmpty()) return;
        
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        int width = getWidth() - 40;
        int height = getHeight() - 60;
        int x0 = 20, y0 = 20;
        
        // Draw axes
        g2.setColor(Color.GRAY);
        g2.drawLine(x0, y0, x0, y0 + height);
        g2.drawLine(x0, y0 + height, x0 + width, y0 + height);
        
        // Draw grid
        g2.setColor(new Color(230, 230, 230));
        for (int i = 0; i <= 10; i++) {
            int y = y0 + (height * i / 10);
            g2.drawLine(x0, y, x0 + width, y);
            g2.setColor(Color.BLACK);
            g2.drawString((100 - i * 10) + "%", 5, y + 5);
            g2.setColor(new Color(230, 230, 230));
        }
        
        // Draw data
        if (data.size() > 1) {
            g2.setColor(color);
            g2.setStroke(new BasicStroke(2));
            
            int pointGap = width / Math.max(data.size() - 1, 1);
            
            for (int i = 0; i < data.size() - 1; i++) {
                int x1 = x0 + i * pointGap;
                int y1 = y0 + height - (int) (data.get(i) * height / 100);
                int x2 = x0 + (i + 1) * pointGap;
                int y2 = y0 + height - (int) (data.get(i + 1) * height / 100);
                
                g2.drawLine(x1, y1, x2, y2);
            }
            
            // Draw current value
            if (!data.isEmpty()) {
                g2.setColor(Color.BLACK);
                g2.setFont(new Font("Arial", Font.BOLD, 16));
                String currentValue = String.format("%.2f%%", data.getLast());
                g2.drawString(currentValue, x0 + width - 80, y0 + 20);
            }
        }
    }
}