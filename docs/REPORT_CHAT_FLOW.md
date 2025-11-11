## Report Chat Flow - Luồng Chi Tiết Từng Bước

Tài liệu này mô tả chi tiết từng bước trong luồng chat report, bao gồm ai gửi request gì, request/response cụ thể, và sau mỗi bước thì làm gì tiếp theo.

---

## 📋 Tổng Quan

**Luồng chính:**
1. **Reporter (Rider/Driver)** tạo report từ ride history
2. **Admin** xem report và khởi tạo 2 cuộc chat riêng biệt (với reporter và reported user)
3. **Reporter và Reported User** trả lời admin qua chat
4. **Admin** tiếp tục trao đổi và kết thúc report
5. **Hệ thống tự động** xử lý nếu không có phản hồi sau 3 ngày

---

## 🔄 Luồng Chi Tiết Từng Bước

### BƯỚC 1: Reporter tạo report từ ride history

**Người thực hiện:** Reporter (Rider hoặc Driver) - **Mobile App**

#### 1.1. Xem lịch sử chuyến đi đã hoàn thành

**Endpoint:** `GET /api/v1/rides/my-completed-rides` (Driver) hoặc `GET /api/v1/ride-requests/ride-history` (Rider)

**Request:**
```
GET /api/v1/rides/my-completed-rides?page=0&size=20&sortBy=completedAt&sortDir=desc
Headers: Authorization: Bearer {token}
```

**Response:**
```json
{
  "data": [
    {
      "sharedRideId": 123,
      "status": "COMPLETED",
      "completedAt": "2025-11-10T10:30:00Z",
      "startLocation": {...},
      "endLocation": {...},
      "driver": {...}
    }
  ],
  "pagination": {...}
}
```

**Sau bước này:** User chọn 1 ride để xem chi tiết hoặc report ngay.

---

#### 1.2. Xem chi tiết chuyến đi (tùy chọn)

**Endpoint:** `GET /api/v1/shared-rides/{rideId}`

**Request:**
```
GET /api/v1/shared-rides/123
Headers: Authorization: Bearer {token}
```

**Response:**
```json
{
  "sharedRideId": 123,
  "status": "COMPLETED",
  "driver": {
    "driverId": 45,
    "user": {
      "userId": 20,
      "fullName": "Tran Van B"
    }
  },
  "sharedRideRequest": {
    "sharedRideRequestId": 100,
    "rider": {
      "riderId": 10,
      "user": {
        "userId": 15,
        "fullName": "Nguyen Van A"
      }
    }
  }
}
```

**Sau bước này:** User quyết định report về người kia (driver hoặc rider).

---

#### 1.3. Gửi report về chuyến đi

**Endpoint:** `POST /api/v1/shared-rides/{rideId}/report`

**Người gửi:** Reporter (User ID: 15 - Nguyen Van A)

**Request:**
```
POST /api/v1/shared-rides/123/report
Headers: 
  Authorization: Bearer {token}
  Content-Type: application/json

Body:
{
  "reportType": "SAFETY",
  "description": "Tài xế đến muộn 30 phút và có hành vi không phù hợp",
  "priority": "MEDIUM"
}
```

**Response:**
```json
{
  "reportId": 42,
  "status": "PENDING",
  "reportType": "SAFETY",
  "description": "Tài xế đến muộn 30 phút và có hành vi không phù hợp",
  "priority": "MEDIUM",
  "reporterId": 15,
  "reporterName": "Nguyen Van A",
  "sharedRideId": 123,
  "driverId": 45,
  "driverName": "Tran Van B",
  "createdAt": "2025-11-10T11:00:00Z"
}
```

**Sau bước này:** 
- Report được tạo với status `PENDING`
- Admin nhận notification về report mới
- Reporter có thể theo dõi report qua `GET /api/v1/user-reports/my-reports`

---

### BƯỚC 2: Admin xem và quản lý reports

**Người thực hiện:** Admin - **Web Dashboard**

#### 2.1. Xem danh sách reports

**Endpoint:** `GET /api/v1/user-reports`

**Request:**
```
GET /api/v1/user-reports?status=PENDING&reportType=SAFETY&page=0&size=20&sortBy=createdAt&sortDir=desc
Headers: Authorization: Bearer {admin_token}
```

**Response:**
```json
{
  "data": [
    {
      "reportId": 42,
      "status": "PENDING",
      "reportType": "SAFETY",
      "reporterId": 15,
      "reporterName": "Nguyen Van A",
      "sharedRideId": 123,
      "driverId": 45,
      "createdAt": "2025-11-10T11:00:00Z"
    }
  ],
  "pagination": {...}
}
```

**Sau bước này:** Admin chọn 1 report để xem chi tiết.

---

#### 2.2. Xem chi tiết report

**Endpoint:** `GET /api/v1/user-reports/{reportId}`

**Request:**
```
GET /api/v1/user-reports/42
Headers: Authorization: Bearer {admin_token}
```

**Response:**
```json
{
  "reportId": 42,
  "status": "PENDING",
  "reportType": "SAFETY",
  "description": "Tài xế đến muộn 30 phút và có hành vi không phù hợp",
  "priority": "MEDIUM",
  "reporterId": 15,
  "reporterName": "Nguyen Van A",
  "reporterEmail": "nguyenvana@example.com",
  "reportedUserId": 20,  // ✅ ID của người bị report (driver)
  "reportedUserName": "Tran Van B",  // ✅ Tên người bị report
  "sharedRideId": 123,
  "driverId": 45,
  "driverName": "Tran Van B",
  "reporterChatStartedAt": null,  // Chưa bắt đầu chat với reporter
  "reportedChatStartedAt": null,  // Chưa bắt đầu chat với reported user
  "createdAt": "2025-11-10T11:00:00Z"
}
```

**Sau bước này:** 
- Admin biết được `reporterId = 15` và `reportedUserId = 20`
- Admin quyết định khởi tạo chat với cả 2 người

---

### BƯỚC 3: Admin khởi tạo chat với reporter

**Người thực hiện:** Admin (User ID: 1) - **Web Dashboard**

#### 3.1. Admin bắt đầu chat với reporter

**Endpoint:** `POST /api/v1/user-reports/{reportId}/start-chat`

**Request:**
```
POST /api/v1/user-reports/42/start-chat
Headers: 
  Authorization: Bearer {admin_token}
  Content-Type: application/json

Body:
{
  "targetUserId": 15,  // ID của reporter (lấy từ response GET report)
  "initialMessage": "Xin chào, tôi là admin. Mình trao đổi về báo cáo này nhé."
}
```

**Response:**
```json
{
  "messageId": 101,
  "senderId": 1,
  "senderName": "Admin User",
  "receiverId": 15,
  "receiverName": "Nguyen Van A",
  "conversationId": "report_42_users_1_15",  // ✅ Format: report_{reportId}_users_{adminId}_{reporterId}
  "conversationType": "REPORT",
  "reportId": 42,
  "rideRequestId": null,
  "messageType": "TEXT",
  "content": "Xin chào, tôi là admin. Mình trao đổi về báo cáo này nhé.",
  "metadata": null,
  "isRead": false,
  "sentAt": "2025-11-10T12:00:00Z"
}
```

**Kết quả trong database:**
- Tạo message với `conversationType = REPORT`
- Tạo conversation với ID: `report_42_users_1_15`
- Cập nhật `user_reports.reporter_chat_started_at = 2025-11-10T12:00:00Z`
- Chuyển `user_reports.status = IN_PROGRESS`
- Gửi WebSocket notification cho reporter (User ID: 15)

**Sau bước này:** 
- Reporter nhận notification và có thể xem conversation mới
- **Reporter BÂY GIỜ MỚI CÓ THỂ** gửi tin nhắn trả lời admin (vì admin đã start chat)
- Admin tiếp tục khởi tạo chat với reported user

---

### BƯỚC 4: Admin khởi tạo chat với reported user

**Người thực hiện:** Admin (User ID: 1) - **Web Dashboard**

#### 4.1. Admin bắt đầu chat với reported user

**Endpoint:** `POST /api/v1/user-reports/{reportId}/start-chat`

**Request:**
```
POST /api/v1/user-reports/42/start-chat
Headers: 
  Authorization: Bearer {admin_token}
  Content-Type: application/json

Body:
{
  "targetUserId": 20,  // ID của reported user (lấy từ response GET report - reportedUserId)
  "initialMessage": "Xin chào, có báo cáo về bạn liên quan đến chuyến đi #123. Mình trao đổi nhé."
}
```

**Response:**
```json
{
  "messageId": 102,
  "senderId": 1,
  "senderName": "Admin User",
  "receiverId": 20,
  "receiverName": "Tran Van B",
  "conversationId": "report_42_users_1_20",  // ✅ Format: report_{reportId}_users_{adminId}_{reportedUserId}
  "conversationType": "REPORT",
  "reportId": 42,
  "rideRequestId": null,
  "messageType": "TEXT",
  "content": "Xin chào, có báo cáo về bạn liên quan đến chuyến đi #123. Mình trao đổi nhé.",
  "metadata": null,
  "isRead": false,
  "sentAt": "2025-11-10T12:05:00Z"
}
```

**Kết quả trong database:**
- Tạo message với `conversationType = REPORT`
- Tạo conversation với ID: `report_42_users_1_20`
- Cập nhật `user_reports.reported_chat_started_at = 2025-11-10T12:05:00Z`
- Gửi WebSocket notification cho reported user (User ID: 20)

**Sau bước này:** 
- Reported user nhận notification
- **Reported User BÂY GIỜ MỚI CÓ THỂ** gửi tin nhắn trả lời admin (vì admin đã start chat)
- **Lưu ý quan trọng:** Reporter và Reported User **KHÔNG THỂ** gửi tin nhắn trước khi admin start chat. Hệ thống sẽ báo lỗi nếu họ cố gắng gửi.

---

### BƯỚC 5: Reporter trả lời admin

**Người thực hiện:** Reporter (User ID: 15 - Nguyen Van A) - **Mobile App**

#### 5.1. Reporter xem danh sách conversations

**Endpoint:** `GET /api/v1/chat/conversations`

**Request:**
```
GET /api/v1/chat/conversations
Headers: Authorization: Bearer {reporter_token}
```

**Response:**
```json
[
  {
    "conversationId": "ride_100_users_10_15",
    "conversationType": "RIDE_REQUEST",
    "rideRequestId": 100,
    "reportId": null,
    "otherUserId": 10,
    "otherUserName": "Driver Name",
    "lastMessage": "...",
    "unreadCount": 0
  },
  {
    "conversationId": "report_42_users_1_15",  // ✅ Conversation với admin về report
    "conversationType": "REPORT",
    "rideRequestId": null,
    "reportId": 42,  // ✅ Link đến report
    "otherUserId": 1,
    "otherUserName": "Admin User",
    "lastMessage": "Xin chào, tôi là admin. Mình trao đổi về báo cáo này nhé.",
    "lastMessageTime": "2025-11-10T12:00:00Z",
    "unreadCount": 1
  }
]
```

**Sau bước này:** Reporter chọn conversation `report_42_users_1_15` để xem tin nhắn.

---

#### 5.2. Reporter xem tin nhắn trong conversation

**Endpoint:** `GET /api/v1/chat/conversations/by-id/{conversationId}/messages`

**Request:**
```
GET /api/v1/chat/conversations/by-id/report_42_users_1_15/messages
Headers: Authorization: Bearer {reporter_token}
```

**Response:**
```json
[
  {
    "messageId": 101,
    "senderId": 1,
    "senderName": "Admin User",
    "receiverId": 15,
    "receiverName": "Nguyen Van A",
    "conversationId": "report_42_users_1_15",
    "conversationType": "REPORT",
    "reportId": 42,
    "messageType": "TEXT",
    "content": "Xin chào, tôi là admin. Mình trao đổi về báo cáo này nhé.",
    "sentAt": "2025-11-10T12:00:00Z",
    "isRead": false
  }
]
```

**Sau bước này:** Reporter đọc tin nhắn và chuẩn bị trả lời.

---

#### 5.3. Reporter gửi tin nhắn trả lời admin

**Endpoint:** `POST /api/v1/chat/messages`

**Request:**
```
POST /api/v1/chat/messages
Headers: 
  Authorization: Bearer {reporter_token}
  Content-Type: application/json

Body:
{
  "receiverId": 1,           // ✅ BẮT BUỘC - Admin ID
  "reportId": 42,            // ✅ BẮT BUỘC - Dùng reportId (KHÔNG dùng rideRequestId)
  "rideRequestId": null,     // ✅ PHẢI NULL cho report chat
  "messageType": "TEXT",     // ✅ BẮT BUỘC
  "content": "Vâng, tài xế đã đến muộn 30 phút và có thái độ không tốt với tôi.",  // ✅ BẮT BUỘC
  "metadata": null           // ✅ CÓ THỂ NULL - Optional
}
```

**Response:**
```json
{
  "messageId": 103,
  "senderId": 15,
  "senderName": "Nguyen Van A",
  "receiverId": 1,
  "receiverName": "Admin User",
  "conversationId": "report_42_users_1_15",
  "conversationType": "REPORT",
  "reportId": 42,
  "rideRequestId": null,
  "messageType": "TEXT",
  "content": "Vâng, tài xế đã đến muộn 30 phút và có thái độ không tốt với tôi.",
  "metadata": null,
  "isRead": false,
  "sentAt": "2025-11-10T12:10:00Z"
}
```

**Kết quả trong database:**
- Lưu message với `conversationType = REPORT`
- Cập nhật `user_reports.reporter_last_reply_at = 2025-11-10T12:10:00Z`
- Gửi WebSocket notification cho admin

**Sau bước này:** 
- Admin nhận notification và có thể xem tin nhắn mới
- Admin tiếp tục trao đổi hoặc chuyển sang chat với reported user

---

### BƯỚC 6: Reported User trả lời admin

**Người thực hiện:** Reported User (User ID: 20 - Tran Van B) - **Mobile App**

#### 6.1. Reported User xem danh sách conversations

**Endpoint:** `GET /api/v1/chat/conversations`

**Request:**
```
GET /api/v1/chat/conversations
Headers: Authorization: Bearer {reported_user_token}
```

**Response:**
```json
[
  {
    "conversationId": "report_42_users_1_20",  // ✅ Conversation với admin về report
    "conversationType": "REPORT",
    "rideRequestId": null,
    "reportId": 42,
    "otherUserId": 1,
    "otherUserName": "Admin User",
    "lastMessage": "Xin chào, có báo cáo về bạn liên quan đến chuyến đi #123. Mình trao đổi nhé.",
    "lastMessageTime": "2025-11-10T12:05:00Z",
    "unreadCount": 1
  }
]
```

**Sau bước này:** Reported user chọn conversation để xem và trả lời.

---

#### 6.2. Reported User gửi tin nhắn trả lời admin

**Endpoint:** `POST /api/v1/chat/messages`

**Request:**
```
POST /api/v1/chat/messages
Headers: 
  Authorization: Bearer {reported_user_token}
  Content-Type: application/json

Body:
{
  "receiverId": 1,           // ✅ BẮT BUỘC - Admin ID
  "reportId": 42,            // ✅ BẮT BUỘC - Dùng reportId
  "rideRequestId": null,     // ✅ PHẢI NULL cho report chat
  "messageType": "TEXT",     // ✅ BẮT BUỘC
  "content": "Xin lỗi admin, hôm đó tôi gặp sự cố xe nên đến muộn. Tôi không có ý định xấu.",  // ✅ BẮT BUỘC
  "metadata": null           // ✅ CÓ THỂ NULL - Optional
}
```

**Response:**
```json
{
  "messageId": 104,
  "senderId": 20,
  "senderName": "Tran Van B",
  "receiverId": 1,
  "receiverName": "Admin User",
  "conversationId": "report_42_users_1_20",
  "conversationType": "REPORT",
  "reportId": 42,
  "rideRequestId": null,
  "messageType": "TEXT",
  "content": "Xin lỗi admin, hôm đó tôi gặp sự cố xe nên đến muộn. Tôi không có ý định xấu.",
  "metadata": null,
  "isRead": false,
  "sentAt": "2025-11-10T12:15:00Z"
}
```

**Kết quả trong database:**
- Lưu message với `conversationType = REPORT`
- Cập nhật `user_reports.reported_last_reply_at = 2025-11-10T12:15:00Z`
- Gửi WebSocket notification cho admin

**Sau bước này:** Admin nhận notification và có thể xem tin nhắn từ cả 2 phía.

---

### BƯỚC 7: Admin tiếp tục trao đổi

**Người thực hiện:** Admin (User ID: 1) - **Web Dashboard**

#### 7.1. Admin xem danh sách conversations

**Endpoint:** `GET /api/v1/chat/conversations`

**Request:**
```
GET /api/v1/chat/conversations
Headers: Authorization: Bearer {admin_token}
```

**Response:**
```json
[
  {
    "conversationId": "report_42_users_1_15",
    "conversationType": "REPORT",
    "reportId": 42,
    "otherUserId": 15,
    "otherUserName": "Nguyen Van A",
    "lastMessage": "Vâng, tài xế đã đến muộn 30 phút...",
    "unreadCount": 1
  },
  {
    "conversationId": "report_42_users_1_20",
    "conversationType": "REPORT",
    "reportId": 42,
    "otherUserId": 20,
    "otherUserName": "Tran Van B",
    "lastMessage": "Xin lỗi admin, hôm đó tôi gặp sự cố xe...",
    "unreadCount": 1
  }
]
```

**Sau bước này:** Admin chọn conversation để xem tin nhắn và trả lời.

---

#### 7.2. Admin xem tin nhắn trong conversation với reporter

**Endpoint:** `GET /api/v1/chat/conversations/by-id/{conversationId}/messages`

**Request:**
```
GET /api/v1/chat/conversations/by-id/report_42_users_1_15/messages
Headers: Authorization: Bearer {admin_token}
```

**Response:**
```json
[
  {
    "messageId": 101,
    "senderId": 1,
    "content": "Xin chào, tôi là admin...",
    "sentAt": "2025-11-10T12:00:00Z"
  },
  {
    "messageId": 103,
    "senderId": 15,
    "content": "Vâng, tài xế đã đến muộn 30 phút...",
    "sentAt": "2025-11-10T12:10:00Z"
  }
]
```

**Sau bước này:** Admin đọc tin nhắn và trả lời.

---

#### 7.3. Admin trả lời reporter

**Endpoint:** `POST /api/v1/chat/messages`

**Request:**
```
POST /api/v1/chat/messages
Headers: 
  Authorization: Bearer {admin_token}
  Content-Type: application/json

Body:
{
  "receiverId": 15,          // ✅ BẮT BUỘC - Reporter ID
  "reportId": 42,            // ✅ BẮT BUỘC - Dùng reportId
  "rideRequestId": null,     // ✅ PHẢI NULL cho report chat
  "messageType": "TEXT",     // ✅ BẮT BUỘC
  "content": "Cảm ơn bạn đã phản hồi. Mình đã trao đổi với tài xế và họ đã giải thích về sự cố xe. Bạn có muốn mình xử lý thêm gì không?",  // ✅ BẮT BUỘC
  "metadata": null           // ✅ CÓ THỂ NULL - Optional
}
```

**Response:**
```json
{
  "messageId": 105,
  "senderId": 1,
  "senderName": "Admin User",
  "receiverId": 15,
  "receiverName": "Nguyen Van A",
  "conversationId": "report_42_users_1_15",
  "conversationType": "REPORT",
  "reportId": 42,
  "messageType": "TEXT",
  "content": "Cảm ơn bạn đã phản hồi. Mình đã trao đổi với tài xế và họ đã giải thích về sự cố xe. Bạn có muốn mình xử lý thêm gì không?",
  "sentAt": "2025-11-10T12:20:00Z"
}
```

**Sau bước này:** Reporter nhận notification và có thể tiếp tục trao đổi.

---

### BƯỚC 8: Kết thúc report

**Người thực hiện:** Admin - **Web Dashboard**

#### 8.1. Admin cập nhật status report

**Endpoint:** `PATCH /api/v1/user-reports/{reportId}`

**Request:**
```
PATCH /api/v1/user-reports/42
Headers: 
  Authorization: Bearer {admin_token}
  Content-Type: application/json

Body:
{
  "status": "RESOLVED",
  "adminNotes": "Đã trao đổi với cả 2 bên. Tài xế đã giải thích về sự cố xe. Reporter đồng ý đóng report."
}
```

**Response:**
```json
{
  "reportId": 42,
  "status": "RESOLVED",
  "adminNotes": "Đã trao đổi với cả 2 bên. Tài xế đã giải thích về sự cố xe. Reporter đồng ý đóng report.",
  "resolvedAt": "2025-11-10T13:00:00Z",
  "resolverId": 1,
  "resolverName": "Admin User"
}
```

**Sau bước này:** 
- Report đã được đóng (status = `RESOLVED` hoặc `DISMISSED`)
- Cả reporter và reported user nhận notification
- **⚠️ QUAN TRỌNG:** Sau khi report đóng, **KHÔNG THỂ** gửi tin nhắn nữa. Tất cả các bên (admin, reporter, reported user) sẽ nhận lỗi 403 nếu cố gắng gửi tin nhắn

---

## 🔄 Quy Tắc Tự Động (Scheduled Job)

**Job:** `ReportChatFollowUpJob` - Chạy mỗi ngày lúc 03:00

### Quy tắc 1: Reporter không trả lời sau 3 ngày

**Điều kiện:**
- `reporterChatStartedAt` đã có (admin đã bắt đầu chat)
- `reporterLastReplyAt` = null HOẶC > 3 ngày kể từ `reporterChatStartedAt`

**Hành động tự động:**
- `status` → `DISMISSED`
- `autoClosedAt` = thời điểm hiện tại
- `autoClosedReason` = `"REPORTER_NO_RESPONSE"`
- Gửi notification cho reporter

### Quy tắc 2: Reported user không trả lời sau 3 ngày

**Điều kiện:**
- `reportedChatStartedAt` đã có (admin đã bắt đầu chat)
- `reportedLastReplyAt` = null HOẶC > 3 ngày kể từ `reportedChatStartedAt`

**Hành động tự động:**
- `status` → `RESOLVED`
- `autoClosedAt` = thời điểm hiện tại
- `autoClosedReason` = `"REPORTED_NO_RESPONSE"`
- **Suspend user** (`user.status = SUSPENDED`)
- Gửi notification cho reported user

---

## 🔒 Validation và Quyền Truy Cập

### Quy tắc gửi tin nhắn trong report chat:

**⚠️ QUAN TRỌNG:** Không thể gửi tin nhắn nếu report đã đóng (status = `RESOLVED` hoặc `DISMISSED`)

1. **Admin:**
   - ✅ Có thể gửi tin nhắn bất cứ lúc nào (khi report chưa đóng)
   - ❌ **KHÔNG THỂ** gửi tin nhắn nếu report status = `RESOLVED` hoặc `DISMISSED`
   - ✅ Chỉ có thể gửi cho reporter hoặc reported user của report đó
   - ✅ **Có 2 cách để khởi tạo chat:**
     - **Cách 1:** Gọi `POST /api/v1/user-reports/{reportId}/start-chat` (khuyến nghị - có initial message)
     - **Cách 2:** Gửi tin nhắn trực tiếp qua `POST /api/v1/chat/messages` (hệ thống tự động set `reportedChatStartedAt`/`reporterChatStartedAt`)
   - ✅ **Lưu ý:** Khi admin gửi tin nhắn đầu tiên đến reporter/reported user, hệ thống sẽ **tự động** set `reporterChatStartedAt` hoặc `reportedChatStartedAt` nếu chưa có

2. **Reporter:**
   - ❌ **KHÔNG THỂ** gửi tin nhắn nếu admin chưa gửi tin nhắn đầu tiên (`reporterChatStartedAt = null`)
   - ❌ **KHÔNG THỂ** gửi tin nhắn nếu report status = `RESOLVED` hoặc `DISMISSED`
   - ✅ **CHỈ CÓ THỂ** gửi tin nhắn sau khi admin đã gửi tin nhắn đầu tiên (qua `start-chat` hoặc trực tiếp qua `send-message`) VÀ report chưa đóng
   - ✅ Chỉ có thể gửi cho admin (receiver phải là admin)

3. **Reported User:**
   - ❌ **KHÔNG THỂ** gửi tin nhắn nếu admin chưa gửi tin nhắn đầu tiên (`reportedChatStartedAt = null`)
   - ❌ **KHÔNG THỂ** gửi tin nhắn nếu report status = `RESOLVED` hoặc `DISMISSED`
   - ✅ **CHỈ CÓ THỂ** gửi tin nhắn sau khi admin đã gửi tin nhắn đầu tiên (qua `start-chat` hoặc trực tiếp qua `send-message`) VÀ report chưa đóng
   - ✅ Chỉ có thể gửi cho admin (receiver phải là admin)

**Lỗi nếu vi phạm:**

1. **Reporter/Reported user gửi tin nhắn trước khi admin gửi tin nhắn đầu tiên:**
   ```
   HTTP 403 Forbidden
   {
     "error": {
       "message": "Admin has not started a chat with the reporter yet. Please wait for admin to initiate the conversation."
     }
   }
   ```
   hoặc
   ```
   HTTP 403 Forbidden
   {
     "error": {
       "message": "Admin has not started a chat with you yet. Please wait for admin to initiate the conversation."
     }
   }
   ```

2. **Gửi tin nhắn khi report đã đóng (RESOLVED hoặc DISMISSED):**
   ```
   HTTP 403 Forbidden
   {
     "error": {
       "message": "Cannot send messages to a closed report. Report status: RESOLVED"
     }
   }
   ```
   hoặc
   ```
   HTTP 403 Forbidden
   {
     "error": {
       "message": "Cannot send messages to a closed report. Report status: DISMISSED"
     }
   }
   ```

---

## 📝 Lưu Ý Quan Trọng

### 1. Về `metadata` trong SendMessageRequest
- **Optional field**, có thể để `null` hoặc không gửi
- Dùng cho các trường hợp đặc biệt:
  - Location coordinates: `{"lat": 10.123, "lng": 106.456}`
  - Image URL: `{"imageUrl": "https://..."}`
- Với report chat thông thường, **không cần** metadata

### 2. Về `rideRequestId` vs `reportId` - Các field có thể null
- **Ride chat:** Dùng `rideRequestId` (ID của `SharedRideRequest` khi rider join ride)
- **Report chat:** Chỉ cần `reportId`, **KHÔNG cần** `rideRequestId`

**Khi gửi message cho report chat:**
```json
{
  "receiverId": 1,           // ✅ BẮT BUỘC - ID người nhận (admin hoặc reporter/reported user)
  "reportId": 42,            // ✅ BẮT BUỘC cho report chat - ID của report
  "rideRequestId": null,     // ✅ PHẢI NULL cho report chat (không dùng cho report)
  "messageType": "TEXT",     // ✅ BẮT BUỘC - Loại tin nhắn (TEXT, IMAGE, etc.)
  "content": "...",          // ✅ BẮT BUỘC - Nội dung tin nhắn
  "metadata": null           // ✅ CÓ THỂ NULL - Optional metadata (location, image URL, etc.)
}
```

**Tóm tắt các field trong SendMessageRequest cho report message:**
- ✅ **Bắt buộc:** `receiverId`, `reportId`, `messageType`, `content`
- ✅ **Phải null:** `rideRequestId` (chỉ dùng cho ride chat)
- ✅ **Có thể null:** `metadata` (optional, dùng cho location/image nếu cần)

### 3. Về endpoint get messages
- **Endpoint cũ:** `GET /api/v1/chat/conversations/{rideRequestId}/messages`
  - Chỉ dùng cho **RIDE_REQUEST** conversations
  - **KHÔNG hoạt động** với report chat
- **Endpoint mới:** `GET /api/v1/chat/conversations/by-id/{conversationId}/messages`
  - Hỗ trợ cả **RIDE_REQUEST** và **REPORT** conversations
  - Dùng `conversationId` từ response `GET /api/v1/chat/conversations`

### 4. Về conversationId format
- **Ride chat:** `ride_{rideRequestId}_users_{smallerUserId}_{largerUserId}`
  - Ví dụ: `ride_100_users_10_15`
- **Report chat:** `report_{reportId}_users_{smallerUserId}_{largerUserId}`
  - Ví dụ: `report_42_users_1_15` (admin=1, reporter=15)
  - Ví dụ: `report_42_users_1_20` (admin=1, reported=20)

### 5. Về việc admin khởi tạo chat
- **Admin có 2 cách để bắt đầu chat:**
  1. **Qua endpoint `start-chat` (khuyến nghị):**
     - `POST /api/v1/user-reports/{reportId}/start-chat`
     - Có thể gửi `initialMessage` tùy chọn
     - Tự động set `reporterChatStartedAt` hoặc `reportedChatStartedAt`
     - Tự động chuyển status report sang `IN_PROGRESS`
  
  2. **Gửi tin nhắn trực tiếp:**
     - `POST /api/v1/chat/messages` với `reportId` và `receiverId`
     - Hệ thống **tự động** set `reporterChatStartedAt` hoặc `reportedChatStartedAt` khi admin gửi tin nhắn đầu tiên
     - **Lưu ý:** Cách này không tự động chuyển status sang `IN_PROGRESS`, cần update thủ công nếu cần

- **Sau khi admin gửi tin nhắn đầu tiên (bằng cách nào cũng được):**
  - Reporter/Reported user **mới có thể** gửi tin nhắn trả lời
  - Hệ thống sẽ báo lỗi 403 nếu họ cố gắng gửi trước khi admin gửi tin nhắn đầu tiên

---

## 🎯 Tóm Tắt Luồng

1. **Reporter (Mobile):** Xem ride history → Tạo report → Nhận notification khi admin chat
2. **Admin (Web):** Xem reports → Xem chi tiết (có `reportedUserId`) → Khởi tạo chat với reporter → Khởi tạo chat với reported user
3. **Reporter (Mobile):** Xem conversations → Xem messages → Trả lời admin
4. **Reported User (Mobile):** Xem conversations → Xem messages → Trả lời admin
5. **Admin (Web):** Xem conversations → Trao đổi với cả 2 → Kết thúc report
6. **Hệ thống (Auto):** Job kiểm tra mỗi ngày → Auto-dismiss/auto-ban nếu không phản hồi

---

## 🔍 Database Schema Changes

Xem migration files:
- `V25__Add_report_chat_support_to_messages.sql` - Thêm support cho report chat
- `V26__Add_report_chat_followup_columns.sql` - Thêm columns tracking chat follow-up
