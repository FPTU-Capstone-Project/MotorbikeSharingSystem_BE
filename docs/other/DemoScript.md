# Hướng dẫn Demo app

---

## Chuẩn bị Demo

### Thiết bị cần có
- **2 điện thoại** (hoặc 2 emulator): 1 cho hành khách (Rider), 1 cho tài xế (Driver)
- Cả hai cần bật **định vị GPS** và cho phép ứng dụng truy cập vị trí
- Đảm bảo có kết nối mạng ổn định

### Tài khoản Demo

| Vai trò | Email | Mật khẩu | Dùng cho |
|---------|-------|----------|----------|
| Hành khách | `john.doe@example.com` | `Password1!` | Điện thoại hành khách |
| Tài xế | `driver1@example.com` | `Password1!` | Điện thoại tài xế |

### Số dư ví ban đầu
Trước khi bắt đầu demo, kiểm tra số dư ví của cả hai tài khoản:

| Tài khoản | Số dư khả dụng | Số dư đang giữ |
|-----------|----------------|----------------|
| John Doe (Hành khách) | 300.000đ | 0đ |
| Driver One (Tài xế) | 300.000đ | 0đ |

> **Cách kiểm tra ví**: 
> - Hành khách: Tab **"Ví tiền"** ở thanh điều hướng dưới cùng
> - Tài xế: Tab **"Thu nhập"** ở thanh điều hướng dưới cùng

---

## Kịch bản Demo 1: Tham gia chuyến xe có sẵn

**Tình huống**: Tài xế đã tạo chuyến đi từ S2.02 Vinhomes Grand Park đến FPT University HCMC. Hành khách tìm thấy và tham gia chuyến xe này.

**Giá chuyến**: 10.000đ (Tài xế nhận 9.000đ sau khi trừ 10% hoa hồng)

### Bước 1: Tài xế tạo chuyến chia sẻ

**Trên điện thoại TÀI XẾ:**

1. Mở app → Đăng nhập với `driver1@example.com` / `Password1!`
2. Đảm bảo công tắc **"Trực tuyến"** ở góc trên bên phải đang **BẬT** (màu xanh)
3. Tại màn hình chính, nhấn nút **"Tạo chuyến chia sẻ"**
4. Chọn tuyến đường:
   - Điểm đón: **S2.02 Vinhomes Grand Park**
   - Điểm đến: **FPT University HCMC**
   - Thời gian khởi hành: Chọn thời gian phù hợp (ví dụ: 15-30 phút sau thời điểm hiện tại)
5. Nhấn **"Tạo chuyến đi"**
6. **Kết quả mong đợi**: Hiện thông báo "Thành công" và chuyến xe xuất hiện trong danh sách với trạng thái **"Đang chờ"**

### Bước 2: Hành khách tìm và tham gia chuyến

**Trên điện thoại HÀNH KHÁCH:**

1. Mở app → Đăng nhập với `john.doe@example.com` / `Password1!`
2. Tại màn hình chính, nhấn nút **"Tìm chuyến đi"**
3. Danh sách các chuyến xe khả dụng hiển thị
4. Tìm chuyến xe của Driver One (S2.02 → FPTU)
5. Nhấn nút **"Tham gia"** trên thẻ chuyến xe
6. Màn hình đặt xe mở ra → Xác nhận điểm đón của bạn
7. Nhấn **"Xem giá cước"** → Hiển thị giá **10.000đ**
8. Nhấn **"Đặt xe ngay"**

**Kiểm tra ví hành khách:**
| Thời điểm | Số dư khả dụng | Số dư đang giữ |
|-----------|----------------|----------------|
| Sau khi nhấn "Đặt xe ngay" | 290.000đ | 10.000đ |

> Lưu ý: 10.000đ đã bị tạm giữ, chưa thanh toán thật

### Bước 3: Tài xế nhận yêu cầu

**Trên điện thoại TÀI XẾ:**

1. **Kết quả mong đợi**: Một cửa sổ popup xuất hiện với tiêu đề **"Yêu cầu tham gia"** hoặc **"Chuyến đi mới"**
2. Popup hiển thị:
   - Tên hành khách: John Doe
   - Điểm đón và điểm đến
   - Giá cước: 10.000đ
   - ⏱️ Đồng hồ đếm ngược 90 giây
3. Nhấn nút **"Nhận chuyến"**
4. Hiển thị thông báo: **"Thành công! Bạn đã nhận chuyến đi thành công. Hãy chuẩn bị đón khách."**
5. Nhấn **"Bắt đầu chuyến đi"** → Màn hình theo dõi GPS mở ra

### Bước 4: Đón khách và di chuyển

**Trên điện thoại TÀI XẾ:**

1. Màn hình theo dõi hiển thị:
   - Bản đồ với vị trí tài xế (điểm xanh)
   - Điểm đón của hành khách (điểm xanh lá)
   - Điểm đến (điểm đỏ)
   - Đường đi được vẽ trên bản đồ
2. Di chuyển đến điểm đón (hoặc nhấn **"Giả lập tới điểm đón"** nếu demo trên emulator)
3. Khi đến gần điểm đón (trong vòng 100m), nhấn **"Đã nhận khách"**
4. Hiển thị thông báo: **"Đã nhận khách - Bắt đầu di chuyển đến điểm đến."**

**Trên điện thoại HÀNH KHÁCH:**

1. **Kết quả mong đợi**: Tự động chuyển sang màn hình theo dõi chuyến đi
2. Hiển thị vị trí realtime của tài xế trên bản đồ
3. Trạng thái chuyến đi: **"Đang diễn ra"**

### Bước 5: Hoàn thành chuyến đi

**Trên điện thoại TÀI XẾ:**

1. Di chuyển đến điểm đến (hoặc nhấn **"Giả lập tới điểm đến"**)
2. Khi đến gần điểm đến (trong vòng 100m), nhấn **"Hoàn thành chuyến đi"**
3. Xác nhận trong hộp thoại → Nhấn **"Xác nhận"**
4. Chuyển đến màn hình hoàn thành với thông tin thu nhập

**Số dư ví sau khi hoàn thành:**
| Tài khoản | Số dư khả dụng | Số dư đang giữ |
|-----------|----------------|----------------|
| John Doe (Hành khách) | 290.000đ | 0đ |
| Driver One (Tài xế) | 309.000đ | 0đ |

> Hành khách đã thanh toán 10.000đ, Tài xế nhận 9.000đ (sau khi trừ 10% hoa hồng = 1.000đ)

**Trên điện thoại HÀNH KHÁCH:**

1. Nhận thông báo: **"Chuyến đi hoàn thành"**
2. Có thể đánh giá tài xế (1-5 sao)

---

## Kịch bản Demo 2: Đặt xe mới (Hệ thống tự động ghép)

**Tình huống**: Hành khách muốn đặt xe ngay, hệ thống tự động tìm tài xế phù hợp.

**Số dư ví trước demo:**
| Tài khoản | Số dư khả dụng | Số dư đang giữ |
|-----------|----------------|----------------|
| John Doe (Hành khách) | 290.000đ | 0đ |
| Driver One (Tài xế) | 309.000đ | 0đ |

### Bước 1: Tài xế chuẩn bị nhận cuốc

**Trên điện thoại TÀI XẾ:**

1. Mở app → Đăng nhập với `driver1@example.com` / `Password1!`
2. Bật công tắc **"Trực tuyến"** ở góc trên bên phải (màu xanh)
3. Tạo một chuyến chia sẻ mới:
   - Nhấn **"Tạo chuyến chia sẻ"**
   - Chọn tuyến: **FPT University HCMC** → **S2.02 Vinhomes Grand Park**
   - Nhấn **"Tạo chuyến đi"**
4. Chuyến xe xuất hiện với trạng thái **"Đang chờ"**

### Bước 2: Hành khách đặt xe

**Trên điện thoại HÀNH KHÁCH:**

1. Mở app → Đăng nhập với `john.doe@example.com` / `Password1!`
2. Tại màn hình chính, nhấn nút **"Đặt xe ngay"**
3. Màn hình đặt xe mở ra:
   - Nhập điểm đón: **FPT University HCMC**
   - Nhập điểm đến: **S2.02 Vinhomes Grand Park**
4. Nhấn **"Xem giá cước"** → Hiển thị giá **10.000đ**
5. Nhấn **"Đặt xe ngay"**
6. Chuyển đến màn hình chờ ghép xe với animation xoay

**Kiểm tra ví hành khách:**
| Thời điểm | Số dư khả dụng | Số dư đang giữ |
|-----------|----------------|----------------|
| Sau khi đặt xe | 280.000đ | 10.000đ |

### Bước 3: Tài xế nhận yêu cầu

**Trên điện thoại TÀI XẾ:**

1. **Kết quả mong đợi**: Popup **"Chuyến đi mới"** xuất hiện với đồng hồ đếm ngược
2. Xem thông tin:
   - Tên hành khách: John Doe
   - Điểm đón: FPT University HCMC
   - Điểm đến: S2.02 Vinhomes Grand Park
   - Giá: 10.000đ
3. Nhấn **"Nhận chuyến"**

**📱 Trên điện thoại HÀNH KHÁCH:**

1. Animation chờ dừng lại
2. Hiển thị: **"Đã tìm thấy tài xế!"**
3. Tự động chuyển sang màn hình theo dõi chuyến đi

### Bước 4-5: Đón khách và hoàn thành

*(Thực hiện tương tự Kịch bản 1, Bước 4-5)*

**Số dư ví sau khi hoàn thành:**
| Tài khoản | Số dư khả dụng | Số dư đang giữ |
|-----------|----------------|----------------|
| John Doe (Hành khách) | 280.000đ | 0đ |
| Driver One (Tài xế) | 318.000đ | 0đ |

---

## Kịch bản Demo 3: Tài xế nhận yêu cầu từ danh sách chờ (Broadcast)

**Tình huống**: Hành khách đặt xe nhưng không có tài xế online. Yêu cầu được đưa vào danh sách chờ. Tài xế chủ động vào xem và nhận.

**Số dư ví trước demo:**
| Tài khoản | Số dư khả dụng | Số dư đang giữ |
|-----------|----------------|----------------|
| John Doe (Hành khách) | 280.000đ | 0đ |
| Driver One (Tài xế) | 318.000đ | 0đ |

### Bước 1: Hành khách đặt xe

**Trên điện thoại HÀNH KHÁCH:**

1. Mở app → Đăng nhập
2. Nhấn **"Đặt xe ngay"**
3. Nhập lộ trình:
   - Điểm đón: **S2.02 Vinhomes Grand Park**
   - Điểm đến: **FPT University HCMC**
4. Nhấn **"Xem giá cước"** → **"Đặt xe ngay"**
5. Màn hình chờ ghép xe hiển thị: **"Đang tìm tài xế..."**

**Ví hành khách:**
| Thời điểm | Số dư khả dụng | Số dư đang giữ |
|-----------|----------------|----------------|
| Sau khi đặt | 270.000đ | 10.000đ |

### Bước 2: Tài xế xem danh sách yêu cầu chờ

**Trên điện thoại TÀI XẾ:**

1. Mở app → Đăng nhập
2. Tại màn hình chính, tìm mục **"Yêu cầu chờ"** hoặc nhấn vào biểu tượng danh sách
3. Kéo xuống để làm mới danh sách (pull-to-refresh)
4. **Kết quả mong đợi**: Yêu cầu của John Doe xuất hiện trong danh sách

### Bước 3: Tài xế nhận yêu cầu từ danh sách

**Trên điện thoại TÀI XẾ:**

1. Nhấn vào yêu cầu của John Doe
2. Xem chi tiết:
   - Điểm đón: S2.02 Vinhomes Grand Park
   - Điểm đến: FPT University HCMC
   - Giá: 10.000đ
3. Nhấn **"Nhận chuyến"** hoặc **"Xác nhận"**
4. Hệ thống tự động tạo chuyến xe mới và ghép với yêu cầu

**Trên điện thoại HÀNH KHÁCH:**

1. Animation chờ dừng lại
2. Thông báo: **"Đã tìm thấy tài xế!"**
3. Chuyển sang màn hình theo dõi

### Bước 4-5: Đón khách và hoàn thành

*(Thực hiện tương tự các kịch bản trước)*

**Số dư ví sau khi hoàn thành:**
| Tài khoản | Số dư khả dụng | Số dư đang giữ |
|-----------|----------------|----------------|
| John Doe (Hành khách) | 270.000đ | 0đ |
| Driver One (Tài xế) | 327.000đ | 0đ |

---

## Tóm tắt các màn hình chính

### Thanh điều hướng - Hành khách (Bottom Tab)

| Icon | Tên tab | Chức năng |
|------|---------|-----------|
| 🏠 | **Trang chủ** | Đặt xe, tìm chuyến, xem chuyến gần đây |
| 💰 | **Ví tiền** | Xem số dư, nạp tiền, lịch sử giao dịch |
| 📜 | **Lịch sử** | Xem các chuyến đi đã hoàn thành |
| 👤 | **Hồ sơ** | Thông tin cá nhân, cài đặt |

### Thanh điều hướng - Tài xế (Bottom Tab)

| Icon | Tên tab | Chức năng |
|------|---------|-----------|
| 🏠 | **Trang chủ** | Tạo chuyến, xem chuyến hiện tại, bật/tắt online |
| 💵 | **Thu nhập** | Xem thu nhập, thống kê |
| 📜 | **Lịch sử** | Xem các chuyến đã hoàn thành |
| ⭐ | **Đánh giá** | Xem đánh giá từ hành khách |
| 👤 | **Hồ sơ** | Thông tin cá nhân, xe, cài đặt |

---

## 📊 Trạng thái chuyến đi (hiển thị trên màn hình)

| Trạng thái | Ý nghĩa | Khi nào xuất hiện |
|------------|---------|-------------------|
| **Đang chờ** | Chuyến xe đã tạo, chờ hành khách | Tài xế vừa tạo chuyến chia sẻ |
| **Đã xác nhận** | Tài xế đã nhận chuyến, chưa đón khách | Sau khi tài xế nhấn "Nhận chuyến" |
| **Đang diễn ra** | Đã đón khách, đang di chuyển | Sau khi tài xế nhấn "Đã nhận khách" |
| **Hoàn thành** | Chuyến đi kết thúc | Sau khi tài xế nhấn "Hoàn thành chuyến đi" |
| **Đã hủy** | Chuyến đi bị hủy | Khi hành khách hoặc tài xế hủy |

---

## Xử lý sự cố thường gặp

### Ví không cập nhật số dư
- **Giải pháp**: Vào tab Ví tiền, kéo xuống để làm mới (pull-to-refresh)

### Không nhận được thông báo chuyến mới (Tài xế)
- **Kiểm tra**: Công tắc "Trực tuyến" có đang BẬT không?
- **Giải pháp**: Tắt app, mở lại, bật lại "Trực tuyến"

### Màn hình chờ ghép xe quá lâu
- **Nguyên nhân**: Không có tài xế online phù hợp
- **Giải pháp**: Đợi tài xế vào "Yêu cầu chờ" để nhận thủ công

### Nút "Hoàn thành chuyến đi" không hoạt động
- **Nguyên nhân**: Chưa đến gần điểm đến (trong vòng 100m)
- **Giải pháp**: Di chuyển gần hơn hoặc sử dụng nút "Giả lập tới điểm đến"

### GPS không cập nhật vị trí
- **Giải pháp**: 
  1. Kiểm tra quyền truy cập vị trí của app
  2. Tắt chế độ tiết kiệm pin
  3. Khởi động lại app

---


