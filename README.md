# 🖥️ Hệ thống Giám sát Phần cứng (Hardware Monitoring System)

Hệ thống giám sát phần cứng thời gian thực với kiến trúc Client-Server-Agent, cho phép theo dõi CPU, RAM và Disk của nhiều máy tính từ một trung tâm điều khiển.

## 📋 Mục lục

- [Tổng quan](#tổng-quan)
- [Tính năng](#tính-năng)
- [Kiến trúc hệ thống](#kiến-trúc-hệ-thống)
- [Yêu cầu hệ thống](#yêu-cầu-hệ-thống)
- [Cài đặt](#cài-đặt)
- [Hướng dẫn sử dụng](#hướng-dẫn-sử-dụng)
- [Cấu trúc dự án](#cấu-trúc-dự-án)
- [Giao thức truyền thông](#giao-thức-truyền-thông)
- [Xuất báo cáo](#xuất-báo-cáo)

## 🎯 Tổng quan

Hệ thống giám sát phần cứng là một ứng dụng Java Swing cho phép:
- Thu thập thông tin phần cứng (CPU, RAM, Disk) từ nhiều máy tính
- Hiển thị dữ liệu thời gian thực trên dashboard
- Vẽ biểu đồ theo dõi xu hướng sử dụng tài nguyên
- Phân nhóm máy tính theo phân khu (zone)
- Xuất báo cáo CSV để phân tích

## ✨ Tính năng

### Hardware Agent
- ✅ Thu thập thông tin CPU, RAM, Disk mỗi giây
- ✅ Hiển thị biểu đồ real-time cho từng metric
- ✅ Kết nối với server để gửi dữ liệu
- ✅ Giao diện hiện đại với progress bar và màu sắc cảnh báo
- ✅ Log hoạt động chi tiết

### Monitoring Server
- ✅ Nhận và xử lý dữ liệu từ nhiều agents
- ✅ Dashboard hiển thị tất cả agents
- ✅ Biểu đồ tổng hợp trung bình tất cả agents
- ✅ Phân loại agents theo phân khu (zone)
- ✅ Tự động lưu log CSV
- ✅ Xuất báo cáo CSV theo yêu cầu
- ✅ Theo dõi trạng thái online/offline của agents

### Monitoring Client
- ✅ Kết nối đến server để xem dữ liệu
- ✅ Dashboard hiển thị thông tin agents
- ✅ Thống kê tổng quan (tổng agents, online, offline, số phân khu)
- ✅ Xuất báo cáo CSV

### Monitoring System (All-in-One)
- ✅ Kết hợp Server và Local Agent trong một ứng dụng
- ✅ Tự động giám sát máy tính local
- ✅ Nhận kết nối từ các agents khác

## 🏗️ Kiến trúc hệ thống

```
┌─────────────┐         ┌─────────────┐         ┌─────────────┐
│   Agent 1   │────────▶│             │◀────────│   Agent 2   │
│  (Lab-301)  │         │   Server    │         │  (Lab-302)  │
└─────────────┘         │   (Port     │         └─────────────┘
                        │    8888)    │
┌─────────────┐         │             │         ┌─────────────┐
│   Agent 3   │────────▶│             │◀────────│   Client    │
│  (Lab-303)  │         │             │         │  (Viewer)   │
└─────────────┘         └─────────────┘         └─────────────┘
```

- **Agent**: Thu thập dữ liệu phần cứng và gửi lên server
- **Server**: Nhận, xử lý và lưu trữ dữ liệu từ các agents
- **Client**: Kết nối đến server để xem dữ liệu

## 💻 Yêu cầu hệ thống

- **Java**: JDK 8 trở lên
- **Hệ điều hành**: Windows, Linux, macOS
- **Bộ nhớ**: Tối thiểu 512MB RAM
- **Mạng**: Kết nối mạng cho giao tiếp Client-Server-Agent

## 📦 Cài đặt

### 1. Clone repository

```bash
git clone <https://github.com/quangtsbn/UngDungGiamSatHeThongTuXa.git>
cd BTL
```

### 2. Biên dịch dự án

```bash
# Biên dịch tất cả file Java
javac -d bin src/baitap/*.java src/module-info.java
```

Hoặc sử dụng IDE như IntelliJ IDEA, Eclipse để import và build dự án.

### 3. Chạy ứng dụng

#### Chạy Server:
```bash
java -cp bin baitap.MonitoringServer
```

#### Chạy Agent:
```bash
java -cp bin baitap.HardwareAgent
```

#### Chạy Client:
```bash
java -cp bin baitap.MonitoringClient
```

#### Chạy All-in-One System:
```bash
java -cp bin baitap.MonitoringSystem
```

## 🚀 Hướng dẫn sử dụng

### Sử dụng Monitoring Server

1. **Khởi động Server**:
   - Chạy `MonitoringServer.java`
   - Server sẽ tự động khởi động trên port 8888
   - Chờ các agents kết nối

2. **Xem Dashboard**:
   - Tab "📊 Dashboard": Xem danh sách tất cả agents
   - Tab "📈 Biểu đồ Tổng hợp": Xem biểu đồ trung bình CPU, RAM, Disk
   - Tab "📋 Server Log": Xem log hoạt động

3. **Xuất báo cáo**:
   - Vào tab "📋 Server Log"
   - Click nút "📊 XUẤT BÁO CÁO CSV"
   - File CSV sẽ được lưu trong thư mục dự án

### Sử dụng Hardware Agent

1. **Khởi động Agent**:
   - Chạy `HardwareAgent.java`
   - Giao diện sẽ hiển thị với 3 tabs

2. **Cấu hình kết nối**:
   - Nhập **Server Host**: IP hoặc hostname của server (mặc định: localhost)
   - Nhập **Server Port**: Port của server (mặc định: 8888)
   - Nhập **Phân khu**: Tên phân khu (ví dụ: Lab-301)

3. **Kết nối Server**:
   - Click nút "🔗 KẾT NỐI SERVER"
   - Agent sẽ tự động đăng ký với server
   - Bắt đầu gửi dữ liệu mỗi giây

4. **Xem dữ liệu**:
   - Tab "📊 Metrics": Xem CPU, RAM, Disk với progress bar
   - Tab "📈 Biểu đồ": Xem biểu đồ real-time
   - Tab "📋 Log": Xem log hoạt động

### Sử dụng Monitoring Client

1. **Khởi động Client**:
   - Chạy `MonitoringClient.java`

2. **Kết nối Server**:
   - Nhập Server Host và Port
   - Click "🔗 KẾT NỐI"
   - Client sẽ hiển thị dữ liệu từ server

3. **Xem dữ liệu**:
   - Dashboard hiển thị danh sách agents
   - Thống kê tổng quan ở phía trên
   - Log hoạt động ở phía dưới

### Sử dụng Monitoring System (All-in-One)

1. **Khởi động**:
   - Chạy `MonitoringSystem.java`
   - Hệ thống sẽ tự động:
     - Khởi động server trên port 8888
     - Bắt đầu giám sát máy tính local
     - Hiển thị dữ liệu local và các agents khác

2. **Xem dữ liệu**:
   - Tab "📊 Dashboard": Xem tất cả agents
   - Tab "📈 Biểu đồ Real-time": Xem biểu đồ
   - Tab "💻 Local Agent": Xem thông tin máy local
   - Tab "📋 System Log": Xem log hệ thống

## 📁 Cấu trúc dự án

```
BTL/
├── src/
│   ├── baitap/
│   │   ├── HardwareAgent.java      # Agent thu thập dữ liệu
│   │   ├── MonitoringServer.java   # Server nhận và xử lý dữ liệu
│   │   ├── MonitoringClient.java   # Client xem dữ liệu
│   │   └── MonitoringSystem.java   # Hệ thống all-in-one
│   └── module-info.java            # Module configuration
├── bin/                             # Thư mục chứa file .class
├── *.csv                            # File log và báo cáo CSV
└── README.md                        # File này
```

## 🔌 Giao thức truyền thông

### Định dạng message

#### REGISTER (Agent đăng ký với Server)
```
REGISTER|<AgentID>|<Zone>
```
Ví dụ: `REGISTER|DESKTOP-ABC123|Lab-301`

#### DATA (Agent gửi dữ liệu)
```
DATA|<AgentID>|<Zone>|<Timestamp>|<CPU%>|<RAM%>|<TotalRAM_GB>|<Disk%>|<TotalDisk_GB>|<FreeDisk_GB>
```
Ví dụ: `DATA|DESKTOP-ABC123|Lab-301|1699123456789|45.2|67.8|16.0|52.3|500.0|238.5`

#### DISCONNECT (Agent ngắt kết nối)
```
DISCONNECT|<AgentID>
```

### Port mặc định
- **Server Port**: 8888

## 📊 Xuất báo cáo

### Định dạng CSV

#### Server Log CSV
```csv
Timestamp,DateTime,AgentID,Zone,CPU(%),RAM(%),TotalRAM(GB),Disk(%),TotalDisk(GB),FreeDisk(GB)
1699123456789,2024-11-05 15:30:56,DESKTOP-ABC123,Lab-301,45.20,67.80,16.00,52.30,500.00,238.50
```

#### Report CSV
```csv
AgentID,Zone,CPU(%),RAM(%),RAM(GB),Disk(%),Disk(GB),Status,Time
DESKTOP-ABC123,Lab-301,45.20,67.80,16.00,52.30,500.00,🟢 Online,15:30:56
```

### Tên file
- **Server log**: `server_log_YYYYMMDD_HHmmss.csv`
- **Client report**: `client_report_YYYYMMDD_HHmmss.csv`
- **Hardware log**: `hardware_log_YYYYMMDD_HHmmss.csv`

## 🎨 Giao diện

- **Màu sắc cảnh báo**:
  - 🟢 Xanh lá (< 50%): Bình thường
  - 🟡 Vàng (50-75%): Cảnh báo
  - 🔴 Đỏ (> 75%): Nguy hiểm

- **Biểu đồ**:
  - Hiển thị 60 điểm dữ liệu gần nhất (60 giây)
  - Gradient màu sắc
  - Hiển thị giá trị hiện tại, trung bình và tối đa

## 🔧 Xử lý sự cố

### Agent không kết nối được Server
- Kiểm tra Server đã khởi động chưa
- Kiểm tra IP và Port có đúng không
- Kiểm tra Firewall có chặn kết nối không
- Kiểm tra Server và Agent có cùng mạng không

### Dữ liệu không hiển thị
- Kiểm tra Agent đã kết nối và đăng ký thành công chưa
- Kiểm tra log để xem có lỗi gì không
- Đảm bảo Agent đang gửi dữ liệu (kiểm tra tab Log)

### CPU usage hiển thị 0%
- Một số hệ thống có thể không hỗ trợ đầy đủ Java Management API
- Thử chạy với quyền administrator/root

## 📝 Ghi chú

- Dữ liệu được cập nhật mỗi **1 giây**
- Agent được coi là **offline** nếu không gửi dữ liệu trong **5 giây**
- Biểu đồ lưu trữ tối đa **60 điểm dữ liệu** (60 giây)
- File CSV được tự động tạo khi khởi động Server

## 👥 Tác giả

Dự án được phát triển cho môn học BTL (Bài tập lớn).

## 📄 License

Dự án này được phát triển cho mục đích học tập.

---

**Phiên bản**: 1.0  
**Ngôn ngữ**: Java  
**Framework**: Java Swing  
**Kiến trúc**: Client-Server-Agen

## 📞 Hỗ trợ
Nếu gặp vấn đề, vui lòng tạo issue trên GitHub hoặc liên hệ:

Email: minhquangts2004@gmail.com

