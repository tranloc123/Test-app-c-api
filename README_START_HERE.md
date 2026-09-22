# SCBD TikTok LIVE V0.6.0 - REAL INTEGRATION

Bản này xâu chuỗi các phần **đã test thành công trước đây** thành một controller Termux duy nhất.
Không cần build APK mới. APK baseline hiện tại vẫn là **V0.5.9E / HOTFIX8**.

## Luồng hoàn chỉnh

```text
TikTok LIVE thật
   │
   └─ TikTools WSS
        │
        ├─ comment "1" / "2" -> khóa Team P1/P2 cho cả session
        ├─ gift -> tính điểm -> Top1 realtime của từng team
        └─ /pick 1..28 -> chỉ Top1 team thắng được quyền chọn
                 │
                 ▼
SCBD_TIKTOK_LIVE_V0_6_0 Controller (Termux, 127.0.0.1:8797)
        │
        ├─ UDP 127.0.0.1:8796 -> APK native Winner / Pick / Timeout
        │
        └─ PPSSPP Remote Debugger 127.0.0.1:9000
                 │
                 ├─ đọc HP thật P1/P2
                 ├─ phát hiện KO thật
                 ├─ GATE=3 + STATE3 -> outro thật
                 ├─ quay về Training
                 ├─ Full Character Select
                 ├─ P1 = pick Top1 hoặc Random30 khi timeout
                 ├─ P2 = Random30
                 └─ map -> trận kế tiếp
```

## Các quy tắc được giữ nguyên

- Viewer comment `1` -> Team P1.
- Viewer comment `2` -> Team P2.
- Team bị khóa cho toàn session, không đổi phe.
- Gift chỉ tính điểm sau khi viewer đã chọn team.
- TikTools gift streak chỉ tính khi `repeatEnd=true`.
- Top1 của **team thắng tại thời điểm KO** được chụp lại và có quyền `/pick`.
- Chỉ đúng Top1 đó được `/pick 1..28` trong 15 giây.
- First valid pick locks.
- `/pick 14 = KRATOS` giữ nguyên.
- Exact pick 1..28 tự map về source slot gốc, bỏ Custom slot 18 và Random slot 30.
- Timeout: native chỉ hiện `TIME OUT / GAME RANDOM`; controller đưa P1 tới source slot 30 để Soulcalibur tự random.
- P2 trận sau luôn source slot 30 Random.
- Điểm/team lock tồn tại qua nhiều trận cho tới khi bấm `RESET SESSION`.

## Bước cài trên ROG Phone 6

### 1. Dừng Bridge cũ

Nếu Termux đang chạy `START_NATIVE_BRIDGE.sh`, bấm:

```bash
Ctrl+C
```

V0.6.0 dùng lại port `8797`, nên không chạy bridge cũ cùng lúc.

### 2. Giải nén

Đặt ZIP vào Download rồi chạy:

```bash
cd /storage/emulated/0/Download
unzip -o SCBD_TIKTOK_LIVE_V0_6_0R1_REAL_INTEGRATION.zip -d SCBD_LIVE_V060R1
cd SCBD_LIVE_V060R1
```

### 3. Chạy offline test một lần

```bash
bash TEST_OFFLINE.sh
```

Phải có:

```text
SCBD V0.6.0R1 OFFLINE TEST: PASS
```

### 4. Chạy controller thật

```bash
bash START_SCBD_TIKTOK_LIVE.sh
```

Lần đầu script sẽ tự cài module `ws` bằng npm nếu máy chưa có.

### 5. Mở trang điều khiển

```text
http://127.0.0.1:8797/
```

Trên trang phải kiểm tra:

- `Native APK = ONLINE`
- `PPSSPP = CONNECTED`
- Game phase sẽ thành `ARMED` khi đang ở Training Combat và cả P1/P2 còn HP.

Nếu `PPSSPP = OFFLINE`, bật **PPSSPP Remote Debugger** như môi trường test cũ của dự án tại:

```text
ws://127.0.0.1:9000/debugger
subprotocol: debugger.ppsspp.org
```

## Kết nối TikTok LIVE thật

Trang control có 2 ô:

1. TikTok LIVE input
   - `@username`
   - username trần
   - link `https://www.tiktok.com/@username/live`
   - link TikTok rút gọn/share link

2. `TikTools API Key`

Bấm **KẾT NỐI LIVE**.

Khi thành công, trạng thái sẽ hiện dạng:

```text
CONNECTED @username | room <roomId>
```

> API key không được ghi vào file cấu hình của gói. Nó chỉ nằm trong RAM của controller trong phiên đang chạy.

## Test thật theo thứ tự

1. Hai viewer comment `1` và `2` để vào hai team.
2. Viewer đã có team gửi gift.
3. Xem Top P1/P2 thay đổi trên control page.
4. Chơi trận trong Training Combat tới khi một bên KO thật.
5. APK hiện winner theo Top1 team thắng.
6. Chờ Winner Intro xong, Top1 team thắng comment ví dụ:

```text
/pick 14
```

7. APK phải hiện `KRATOS` + Locked In.
8. Sau outro, controller tự vào Full Character Select.
9. P1 tự đi tới Kratos source slot 14, P2 đi Random30.
10. Map tự xác nhận và trận mới bắt đầu.

Nếu Top1 không pick trong 15 giây:

```text
TIME OUT -> P1 source slot 30 Random -> game tự chọn
```

## Điểm quà

File `gift_rules.txt` cho phép đặt rule riêng:

```text
Rose=30
GG=30
Finger Heart=250
```

Có thể sửa ngay trên control page rồi bấm **LƯU GIFT RULES**.

Quà không có rule riêng dùng fallback:

```text
diamondCount * repeatCount
```

## Những gì V0.6.0 chưa giả vờ là đã xong

- TikTok avatar URL đã đi cùng dữ liệu Winner, nhưng APK hiện tại **chưa tải/render avatar TikTok thật thành texture native**.
- Top leaderboard thật đang hiển thị trên control page; protocol native hiện tại chưa có packet đồng bộ toàn bộ Top5 vào HUD native.
- Gift heal HP / damage trực tiếp trong trận là hạng mục tương lai, không được bật lén trong bản này.
- Portrait lớn Locked In vẫn giữ baseline HOTFIX8, đã chốt là còn nợ polish hình ảnh.

Những phần trên không cản luồng thật: team -> gift -> Top1 -> KO -> winner -> `/pick` -> Character Select -> trận kế tiếp.

---

## V0.6.0R1 - Live Safety Hotfix

R1 không rebuild APK và không thay mapping nhân vật. Chỉ gia cố controller trước khi dùng LIVE thật:

- Dedupe gift theo `transactionId` trong 6 giờ để tránh cộng điểm hai lần khi event bị retry.
- Gift frame bị thiếu `user` vẫn có thể nhận diện bằng `senderUserId` của TikTools v3.
- Nhận cả event `chat` chuẩn hiện tại và alias `comment` để tương thích dữ liệu cũ.
- Giữ nguyên `repeatEnd=true` cho gift combo, Team Lock, Top1 snapshot, `/pick 1..28`, timeout Random30.

## Đang ngồi ở PC nhưng game vẫn chạy trên ROG Phone 6

Đây là cách nên dùng cho baseline hiện tại. **Không chuyển battle controller sang PC**, vì APK native UDP `8796` và PPSSPP Remote Debugger `9000` hiện đều là loopback trên điện thoại.

1. Trên ROG Phone 6: chạy APK HOTFIX8 + Termux controller V0.6.0R1.
2. Cắm điện thoại vào PC, bật USB debugging.
3. Trên PC, giải nén gói này và chạy:

```text
PC_CONTROL_WINDOWS.bat
```

File BAT sẽ chạy:

```text
adb forward tcp:8797 tcp:8797
```

sau đó mở `http://127.0.0.1:8797/` trên trình duyệt PC. Như vậy TikTok/game logic vẫn chạy local trên điện thoại, nhưng toàn bộ bảng điều khiển có thể thao tác từ PC.
