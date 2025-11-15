## Luồng Endpoint Report (Từ đầu đến cuối)

Tài liệu này mô tả các endpoint mà user/admin sử dụng trong toàn bộ vòng đời của report, và cách mỗi bước dẫn đến bước tiếp theo.

### 1) User tạo report từ lịch sử chuyến đi (Mobile)

**Bước 1: Xem lịch sử chuyến đi**
- GET `/api/v1/shared-rides?status=COMPLETED&page=0&size=20`
- Hoặc dùng endpoint lịch sử có sẵn trong app

**Bước 2: Xem chi tiết chuyến đi (tùy chọn)**
- GET `/api/v1/shared-rides/{rideId}`
- Để xem thông tin trước khi report

**Bước 3: Gửi report về chuyến đi**
- POST `/api/v1/shared-rides/{rideId}/report`
- Body: 
  ```json
  {
    "reportType": "SAFETY",
    "description": "Tài xế đến muộn và có hành vi không phù hợp",
    "priority": "MEDIUM"
  }
  ```
- Response: 
  ```json
  {
    "reportId": 42,
    "status": "PENDING",
    "sharedRideId": 123,
    "driverId": 45,
    "reporterId": 15,
    "reporterName": "Nguyen Van A"
  }
  ```

**Theo dõi report của mình:**
- Xem danh sách report: GET `/api/v1/user-reports/my-reports?page=0&size=20`
- Xem chi tiết 1 report: GET `/api/v1/user-reports/{reportId}`

---

### 2) Admin xem và quản lý reports (Admin Dashboard)

**Bước 1: Xem danh sách reports**
- GET `/api/v1/user-reports?status=PENDING&reportType=SAFETY&page=0&size=20&sortBy=createdAt&sortDir=desc`
- Có thể filter theo `status`, `reportType`

**Bước 2: Xem chi tiết 1 report**
- GET `/api/v1/user-reports/{reportId}`
- Response bao gồm:
  ```json
  {
    "reportId": 42,
    "reporterId": 15,
    "reporterName": "Nguyen Van A",
    "reporterEmail": "nguyenvana@example.com",
    "reportedUserId": 20,  // ✅ ID của người bị report
    "reportedUserName": "Tran Van B",  // ✅ Tên người bị report
    "sharedRideId": 123,
    "driverId": 45,
    "driverName": "Tran Van B",
    "status": "PENDING",
    "description": "...",
    "reporterChatStartedAt": null,
    "reportedChatStartedAt": null
  }
  ```
- **Lưu ý:** Response đã có sẵn `reportedUserId` và `reportedUserName`, admin không cần tự tính toán.

---

### 3) Admin khởi tạo chat (2 cuộc trò chuyện riêng biệt)

**Bước 1: Admin bắt đầu chat với reporter**
- POST `/api/v1/user-reports/{reportId}/start-chat`
- Body:
  ```json
  {
    "targetUserId": 15,  // ID của reporter
    "initialMessage": "Xin chào, tôi là admin. Mình trao đổi về báo cáo này nhé."
  }
  ```
- **Kết quả:**
  - Tạo conversation với `conversationId = report_42_users_1_15` (admin=1, reporter=15)
  - Gửi tin nhắn đầu tiên
  - Cập nhật `reporterChatStartedAt` trong database
  - Chuyển status report sang `IN_PROGRESS`

**Bước 2: Admin bắt đầu chat với reported user**
- POST `/api/v1/user-reports/{reportId}/start-chat`
- Body:
  ```json
  {
    "targetUserId": 20,  // ID của reported user (lấy từ response GET report)
    "initialMessage": "Xin chào, có báo cáo về bạn. Mình trao đổi nhé."
  }
  ```
- **Kết quả:**
  - Tạo conversation với `conversationId = report_42_users_1_20` (admin=1, reported=20)
  - Gửi tin nhắn đầu tiên
  - Cập nhật `reportedChatStartedAt` trong database

---

### 4) Luồng chat sau khi admin khởi tạo

**Reporter trả lời admin:**
- POST `/api/v1/chat/messages`
- Body:
  ```json
  {
    "receiverId": 1,  // Admin ID
    "reportId": 42,   // ✅ Dùng reportId thay vì rideRequestId
    "messageType": "TEXT",
    "content": "Vâng, tài xế đã đến muộn 30 phút..."
  }
  ```
- **Kết quả:**
  - Lưu message với `conversationType = REPORT`
  - Cập nhật `reporterLastReplyAt` trong database
  - Gửi WebSocket notification cho admin

**Reported User trả lời admin:**
- POST `/api/v1/chat/messages`
- Body:
  ```json
  {
    "receiverId": 1,  // Admin ID
    "reportId": 42,
    "messageType": "TEXT",
    "content": "Xin lỗi, hôm đó tôi gặp sự cố xe..."
  }
  ```
- **Kết quả:**
  - Lưu message với `conversationType = REPORT`
  - Cập nhật `reportedLastReplyAt` trong database
  - Gửi WebSocket notification cho admin

**Admin trả lời reporter hoặc reported user:**
- POST `/api/v1/chat/messages`
- Body:
  ```json
  {
    "receiverId": 15,  // Reporter ID hoặc reported user ID
    "reportId": 42,
    "messageType": "TEXT",
    "content": "Cảm ơn bạn đã phản hồi. Mình sẽ xem xét..."
  }
  ```

**Xem danh sách conversations (mobile/web):**
- GET `/api/v1/chat/conversations`
- Response bao gồm cả ride chat và report chat:
  ```json
  [
    {
      "conversationId": "ride_100_users_10_15",
      "conversationType": "RIDE_REQUEST",
      "rideRequestId": 100,
      "reportId": null,
      "otherUserId": 15,
      "lastMessage": "..."
    },
    {
      "conversationId": "report_42_users_1_15",
      "conversationType": "REPORT",
      "rideRequestId": null,
      "reportId": 42,
      "otherUserId": 1,
      "lastMessage": "..."
    }
  ]
  ```

**Xem tin nhắn trong 1 conversation:**
- GET `/api/v1/chat/conversations/{conversationId}/messages`
- Hoặc dùng endpoint theo ride: GET `/api/v1/chat/ride/{rideRequestId}/messages` (chỉ cho ride chat)

---

### 5) Quy tắc tự động theo dõi (Scheduled Job)

**Job chạy mỗi ngày:** `ReportChatFollowUpJob`

**Quy tắc 1: Reporter không trả lời sau 3 ngày**
- Điều kiện: `reporterChatStartedAt` đã có, nhưng `reporterLastReplyAt` = null hoặc > 3 ngày
- Hành động:
  - Status → `DISMISSED`
  - Set `autoClosedAt` = thời điểm hiện tại
  - Set `autoClosedReason` = `"REPORTER_NO_RESPONSE"`

**Quy tắc 2: Reported user không trả lời sau 3 ngày**
- Điều kiện: `reportedChatStartedAt` đã có, nhưng `reportedLastReplyAt` = null hoặc > 3 ngày
- Hành động:
  - Status → `RESOLVED`
  - Set `autoClosedAt` = thời điểm hiện tại
  - Set `autoClosedReason` = `"REPORTED_NO_RESPONSE"`
  - **Suspend user** (set `user.status = SUSPENDED`)

---

### 6) Kết thúc report (Admin thủ công)

**Sau khi trao đổi xong, admin có thể:**

**Cập nhật status:**
- PATCH `/api/v1/user-reports/{reportId}`
- Body:
  ```json
  {
    "status": "RESOLVED",
    "adminNotes": "Đã giải quyết xong, cả 2 bên đồng ý."
  }
  ```

**Hoặc resolve với message:**
- POST `/api/v1/user-reports/{reportId}/resolve`
- Body:
  ```json
  {
    "resolutionMessage": "Vấn đề đã được giải quyết. Cảm ơn bạn đã phản hồi."
  }
  ```

**Lưu ý:** Các endpoint này chỉ để ghi nhận kết quả cuối cùng sau khi chat đã kết thúc. Chúng không thay thế bước chat.

---

### Tóm tắt luồng

1. **User (Mobile):** Xem ride history → Tạo report → Theo dõi report
2. **Admin (Web):** Xem danh sách reports → Xem chi tiết (có `reportedUserId`) → Khởi tạo chat với reporter → Khởi tạo chat với reported user
3. **Chat:** Admin ↔ Reporter và Admin ↔ Reported User (2 cuộc trò chuyện riêng)
4. **Tự động:** Job kiểm tra mỗi ngày, auto-dismiss nếu reporter im lặng, auto-ban nếu reported user im lặng
5. **Kết thúc:** Admin cập nhật status hoặc resolve với message

### Lưu ý quan trọng

- ✅ **Response GET report đã có `reportedUserId` và `reportedUserName`** - Admin không cần tự tính toán
- ✅ **Reporter và Reported User trả lời qua cùng endpoint** `/api/v1/chat/messages` với `reportId`
- ✅ **Ride chat giữa rider và driver không thay đổi** - vẫn dùng `rideRequestId`
- ✅ **Report chat là cuộc trò chuyện riêng biệt** với `conversationType=REPORT`
- ✅ **Hệ thống tự động cập nhật `*_last_reply_at`** mỗi khi user trả lời
