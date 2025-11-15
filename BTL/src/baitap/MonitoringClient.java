package baitap;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.table.*;
import java.awt.*;

public class MonitoringClient extends JFrame {
    private Socket socket;
    private BufferedReader in;
    private DefaultTableModel tableModel;
    private JTable agentTable;
    private JTextArea logArea;
    private JLabel statusLabel;
    private JLabel totalLabel, onlineLabel, offlineLabel, zonesLabel;
    private String serverHost = "localhost";
    private int serverPort = 8888;
    private Timer refreshTimer;
    private boolean isConnected = false;
    
    public MonitoringClient() {
        setupUI();
    }
    
    private void setupUI() {
        setTitle("🖥️ Monitoring Client Dashboard v3.0");
        setSize(1400, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        
        // Connection panel
        JPanel connectPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        connectPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        connectPanel.setBackground(Color.WHITE);
        
        statusLabel = new JLabel("⚫ CHƯA KẾT NỐI");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        statusLabel.setForeground(Color.RED);
        statusLabel.setOpaque(true);
        statusLabel.setBackground(Color.WHITE);
        statusLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.RED, 2),
            BorderFactory.createEmptyBorder(5, 15, 5, 15)));
        
        JTextField hostField = new JTextField(serverHost, 15);
        JTextField portField = new JTextField(String.valueOf(serverPort), 5);
        JButton connectBtn = new JButton("🔗 KẾT NỐI");
        JButton disconnectBtn = new JButton("🔌 NGẮT KẾT NỐI");
        JButton refreshBtn = new JButton("🔄 LÀM MỚI");
        
        connectBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        disconnectBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        refreshBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        
        connectBtn.setPreferredSize(new Dimension(150, 35));
        disconnectBtn.setPreferredSize(new Dimension(170, 35));
        refreshBtn.setPreferredSize(new Dimension(150, 35));
        
        connectBtn.setBackground(new Color(46, 204, 113));
        connectBtn.setForeground(Color.WHITE);
        disconnectBtn.setBackground(new Color(231, 76, 60));
        disconnectBtn.setForeground(Color.WHITE);
        refreshBtn.setBackground(new Color(52, 152, 219));
        refreshBtn.setForeground(Color.WHITE);
        
        connectBtn.addActionListener(e -> {
            serverHost = hostField.getText();
            serverPort = Integer.parseInt(portField.getText());
            connectToServer();
        });
        
        disconnectBtn.addActionListener(e -> disconnect());
        refreshBtn.addActionListener(e -> updateSampleData());
        
        connectPanel.add(statusLabel);
        connectPanel.add(Box.createHorizontalStrut(20));
        connectPanel.add(new JLabel("Server:"));
        connectPanel.add(hostField);
        connectPanel.add(new JLabel("Port:"));
        connectPanel.add(portField);
        connectPanel.add(connectBtn);
        connectPanel.add(disconnectBtn);
        connectPanel.add(refreshBtn);
        
        // Stats panel
        JPanel statsPanel = new JPanel(new GridLayout(1, 4, 15, 10));
        statsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        statsPanel.setPreferredSize(new Dimension(1400, 100));
        statsPanel.setBackground(new Color(245, 245, 245));
        
        totalLabel = createStatLabel("Tổng Agents", "0", new Color(52, 152, 219));
        onlineLabel = createStatLabel("Online", "0", new Color(46, 204, 113));
        offlineLabel = createStatLabel("Offline", "0", new Color(231, 76, 60));
        zonesLabel = createStatLabel("Phân khu", "0", new Color(155, 89, 182));
        
        statsPanel.add(totalLabel);
        statsPanel.add(onlineLabel);
        statsPanel.add(offlineLabel);
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
        
        // Log area
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 11));
        logArea.setBackground(new Color(30, 30, 30));
        logArea.setForeground(new Color(0, 255, 0));
        logArea.setMargin(new Insets(10, 10, 10, 10));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Activity Log"));
        logScroll.setPreferredSize(new Dimension(1400, 150));
        
        // Menu bar
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenu viewMenu = new JMenu("View");
        
        JMenuItem exportItem = new JMenuItem("📊 XUẤT BÁO CÁO CSV");
        JMenuItem exitItem = new JMenuItem("❌ THOÁT");
        JMenuItem clearLogItem = new JMenuItem("🗑 XÓA LOG");
        JMenuItem aboutItem = new JMenuItem("ℹ VỀ PHẦN MỀM");
        
        exportItem.addActionListener(e -> exportReport());
        exitItem.addActionListener(e -> System.exit(0));
        clearLogItem.addActionListener(e -> logArea.setText(""));
        aboutItem.addActionListener(e -> showAbout());
        
        fileMenu.add(exportItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        
        viewMenu.add(clearLogItem);
        viewMenu.add(aboutItem);
        
        menuBar.add(fileMenu);
        menuBar.add(viewMenu);
        setJMenuBar(menuBar);
        
        // Main panel
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBackground(new Color(245, 245, 245));
        
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBackground(new Color(245, 245, 245));
        topPanel.add(connectPanel, BorderLayout.NORTH);
        topPanel.add(statsPanel, BorderLayout.CENTER);
        
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(logScroll, BorderLayout.SOUTH);
        
        add(mainPanel);
        
        setLocationRelativeTo(null);
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
    
    private void connectToServer() {
        try {
            socket = new Socket(serverHost, serverPort);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            isConnected = true;
            
            statusLabel.setText("🟢 ĐÃ KẾT NỐI");
            statusLabel.setForeground(new Color(46, 204, 113));
            statusLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(46, 204, 113), 2),
                BorderFactory.createEmptyBorder(5, 15, 5, 15)));
            
            addLog("✓ Connected: " + serverHost + ":" + serverPort);
            
            refreshTimer = new Timer(2000, e -> updateSampleData());
            refreshTimer.start();
            
            updateSampleData();
            
        } catch (IOException e) {
            addLog("✗ Connection error: " + e.getMessage());
            JOptionPane.showMessageDialog(this, 
                "Không thể kết nối đến Server!\n" + e.getMessage(),
                "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void updateSampleData() {
        // Trong production, dữ liệu này sẽ được lấy từ Server
        // Hiện tại dùng data mẫu để demo
        tableModel.setRowCount(0);
        Random rand = new Random();
        
        String[] zones = {"Lab-301", "Lab-302", "Lab-303", "Server-Room", "Office-A", "Office-B"};
        int totalAgents = 0;
        int onlineCount = 0;
        int offlineCount = 0;
        Set<String> zoneSet = new HashSet<>();
        
        for (String zone : zones) {
            int numAgents = 2 + rand.nextInt(4);
            zoneSet.add(zone);
            
            for (int i = 1; i <= numAgents; i++) {
                totalAgents++;
                String agentId = zone.replace("-", "") + "-PC" + String.format("%02d", i);
                double cpu = 5 + rand.nextDouble() * 85;
                double ram = 20 + rand.nextDouble() * 70;
                double totalRam = 8 + rand.nextInt(25);
                double disk = 25 + rand.nextDouble() * 65;
                double totalDisk = 100 + rand.nextInt(900);
                
                boolean online = rand.nextDouble() > 0.15;
                String status = online ? "🟢 Online" : "🔴 Offline";
                if (online) onlineCount++; else offlineCount++;
                
                String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
                
                tableModel.addRow(new Object[]{
                    agentId, zone,
                    String.format("%.2f", cpu),
                    String.format("%.2f", ram),
                    String.format("%.2f", totalRam),
                    String.format("%.2f", disk),
                    String.format("%.2f", totalDisk),
                    status, time
                });
            }
        }
        
        updateStatLabel(totalLabel, "Tổng Agents", String.valueOf(totalAgents));
        updateStatLabel(onlineLabel, "Online", String.valueOf(onlineCount));
        updateStatLabel(offlineLabel, "Offline", String.valueOf(offlineCount));
        updateStatLabel(zonesLabel, "Phân khu", String.valueOf(zoneSet.size()));
    }
    
    private void disconnect() {
        try {
            if (refreshTimer != null) refreshTimer.stop();
            if (in != null) in.close();
            if (socket != null) socket.close();
            isConnected = false;
            
            statusLabel.setText("⚫ CHƯA KẾT NỐI");
            statusLabel.setForeground(Color.RED);
            statusLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.RED, 2),
                BorderFactory.createEmptyBorder(5, 15, 5, 15)));
            
            addLog("✓ Disconnected");
        } catch (IOException e) {
            addLog("✗ Disconnect error: " + e.getMessage());
        }
    }
    
    private void exportReport() {
        try {
            String filename = "client_report_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".csv";
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
            JOptionPane.showMessageDialog(this, 
                "✅ Exported successfully!\n\nFile: " + filename + "\nTotal: " + tableModel.getRowCount() + " agents",
                "Success", JOptionPane.INFORMATION_MESSAGE);
                
        } catch (IOException e) {
            addLog("✗ Export error: " + e.getMessage());
            JOptionPane.showMessageDialog(this, 
                "Export error:\n" + e.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void showAbout() {
        JOptionPane.showMessageDialog(this,
            "Hardware Monitoring System v3.0\n\n" +
            "Client Dashboard - Remote Monitoring\n" +
            "Architecture: Client-Server-Agent\n\n" +
            "Features:\n" +
            "• Real-time monitoring\n" +
            "• Multi-zone management\n" +
            "• CSV export\n" +
            "• Beautiful modern UI\n\n" +
            "© 2025 Monitoring System",
            "About",
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void addLog(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
            logArea.append("[" + timestamp + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
    
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {}
        
        SwingUtilities.invokeLater(() -> {
            new MonitoringClient().setVisible(true);
        });
    }
}