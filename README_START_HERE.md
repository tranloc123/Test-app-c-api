# SCBD TikTok Live Probe V0.1

Bản thử độc lập để kiểm tra xem dữ liệu realtime của một phiên TikTok LIVE có thể được quan sát từ WebView trên Android hay không.

## Mục tiêu

Nhập TikTok ID, ví dụ:

`@flo1234`

App mở:

`https://www.tiktok.com/@flo1234/live`

Sau đó probe ghi log từ 4 lớp:

1. `WS_*` — WebSocket được tạo / frame nhận được.
2. `FETCH_*` và `XHR_*` — request/response có URL liên quan live/webcast/gift/comment/rank.
3. `NET_URL` — resource URL do Android WebView thấy.
4. `DOM` — text mới xuất hiện trên trang, dùng làm fallback.

Bản này KHÔNG đọc hoặc xuất:
- mật khẩu;
- cookie;
- localStorage;
- token đăng nhập.

Nó chỉ dùng để xác định con đường realtime nào đang mang comment/gift vào trang LIVE.

---

## Build APK bằng điện thoại + GitHub Actions

1. Tạo một repository GitHub trống.
2. Upload TOÀN BỘ nội dung của thư mục project này vào repo.
3. Mở tab **Actions**.
4. Chọn **Build SCBD TikTok Live Probe APK**.
5. Chọn **Run workflow**.
6. Khi workflow xanh, tải artifact:
   `SCBD-TikTok-Live-Probe-V0.1-APK`
7. Giải nén artifact và cài:
   `SCBD_TikTok_Live_Probe_V0_1.apk`

Không cần Android Studio trên điện thoại.

---

## Cách test

### Test A — mở LIVE

1. Mở app.
2. Nhập `@ID` streamer đang LIVE.
3. Bấm **MỞ LIVE**.
4. Chờ trang TikTok LIVE tải xong.

Ở thanh trạng thái nên thấy:

`docStart=YES`

Nếu `docStart=NO`, app vẫn chạy nhưng WebSocket hook có thể bỏ lỡ kết nối được tạo rất sớm.

### Test B — comment

1. Bấm **MARK**.
2. Từ một tài khoản TikTok khác, gửi comment rất dễ tìm, ví dụ:

`SCBDTEST12345`

3. Chờ 3–10 giây.
4. Bấm **MARK** lần nữa.
5. Xem log có:
   - `DOM ... SCBDTEST12345`
   - `WS_TEXT`
   - `WS_BIN`
   - `FETCH_BODY` / `XHR_BODY`
   - hoặc URL chứa `webcast`, `im/fetch`, `comment`, `message`.

### Test C — gift

1. Bấm **MARK**.
2. Một viewer gửi 1 gift rẻ nhất.
3. Bấm **MARK** lần nữa sau khi gift hiện trên LIVE.
4. Bấm **XUẤT LOG**.

Log được lưu vào thư mục Download với tên dạng:

`SCBD_LIVE_PROBE_20260922_181500.txt`

Gửi file log đó lại để phân tích.

---

## Cách đọc kết quả nhanh

### Trường hợp tốt nhất

Có nhiều:

`[WS_BIN] {"len":...,"b64":"..."}`

đúng ngay lúc comment/gift xuất hiện.

=> TikTok đang đẩy realtime qua binary WebSocket. Bước sau sẽ là xác định envelope/protobuf và decoder.

### Nếu có `WS_TEXT`

=> Dễ hơn nhiều. Có thể parser trực tiếp nội dung text/JSON.

### Nếu không có WS nhưng có `FETCH/XHR`

=> TikTok Web trên phiên bản WebView này có thể dùng polling/fetch stream thay vì `window.WebSocket`.

### Nếu chỉ có `DOM`

=> Ta vẫn chứng minh được dữ liệu xuất hiện trong WebView, nhưng DOM scraping chỉ nên dùng fallback vì giao diện TikTok có thể đổi class/markup.

### Nếu trang LIVE không mở

TikTok có thể:
- chặn embedded WebView;
- yêu cầu xác minh/captcha/login;
- redirect sang trang khác.

Khi đó log `PAGE`, `NET_URL` và ảnh màn hình sẽ cho biết lớp nào bị chặn.

---

## Tiêu chí PASS V0.1

Chỉ cần đạt một trong hai:

1. Comment test xuất hiện trong `DOM`, hoặc
2. Có network/WS event mới xuất hiện đúng thời điểm comment/gift.

Chưa cần decode gift hoàn chỉnh ở V0.1.

Mục tiêu của V0.1 là tìm **đường ống dữ liệu thật** trước.
