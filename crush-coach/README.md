# 💘 Crush Coach

App Android có **bong bóng nổi** trên màn hình. Chạm vào bong bóng để **BẬT / TẮT**. Khi bật, AI (Claude của Anthropic) đọc cuộc trò chuyện đang mở trên Messenger, Zalo, Instagram, Telegram… rồi **gợi ý 3 câu trả lời**, đoán cảm xúc của crush và cho vài mẹo nhỏ. Cậu cũng có thể hỏi riêng AI, ví dụ "Nên rủ đi chơi chưa?".

## Cách dùng bong bóng

| Thao tác | Tác dụng |
|---|---|
| **Chạm** | BẬT / TẮT AI (bong bóng chuyển hồng và có chữ `ON`) |
| **Giữ lâu** | Mở hoặc thu gọn bảng gợi ý |
| **Kéo** | Di chuyển bong bóng, thả ra nó tự dạt vào mép màn hình |

Khi đang BẬT:
- Vừa bật lên là AI đọc màn hình hiện tại và gợi ý luôn.
- Mỗi khi **crush nhắn tin mới**, AI tự đọc và gợi ý (tắt được trong app).
- Trong bảng gợi ý: **Chèn vào ô chat** để điền sẵn câu trả lời (cậu tự bấm gửi), hoặc **Sao chép**.
- Nút ⟳ lấy bộ gợi ý khác. Các nút câu hỏi nhanh và ô "Hỏi riêng AI…" để xin tư vấn.
- Nếu cậu đang gõ dở một câu trong ô chat, gợi ý đầu tiên sẽ là bản sửa cho hay hơn của câu đó.

## Cài đặt

1. **Tải APK**: vào tab **Actions** của repo → chọn lần chạy mới nhất của **Crush Coach APK** → tải `crush-coach-apk` (file zip) → giải nén lấy `app-debug.apk` → cài lên điện thoại (cho phép "Cài ứng dụng không rõ nguồn gốc").
2. **API key**: tạo key tại [console.anthropic.com](https://console.anthropic.com/settings/keys) → mở Crush Coach → dán vào mục **1 · Kết nối AI**.
3. **Bật Trợ năng**: bấm **Mở cài đặt Trợ năng** → bật **Crush Coach – bong bóng gợi ý**.
   - Android 13 trở lên mà mục này bị mờ: Cài đặt → Ứng dụng → Crush Coach → ⋮ (góc trên bên phải) → **Cho phép cài đặt bị hạn chế**, rồi quay lại bật.
4. Bật công tắc **Hiện bong bóng trên màn hình** → mở app chat → chạm bong bóng.
5. (Không bắt buộc) Điền mục **3 · Về crush**: tên, cách xưng hô (anh – em, tớ – cậu…), giai đoạn, phong cách muốn trả lời, vài ghi chú về crush — gợi ý sẽ hợp hơn nhiều.

Không muốn dùng bong bóng? Mục **💬 Tư vấn nhanh** trong app cho phép dán đoạn chat vào để hỏi.

## 📒 Sổ tay về crush — AI học dần mỗi ngày

1. **Lưu tin nhắn**: khi bong bóng BẬT, app lưu tin của cuộc trò chuyện với crush ngay trên máy (SQLite, không sao lưu lên mạng). Chỉ lưu chat có tên chứa **tên crush** ở mục 3, hoặc chat cậu bấm **📌 Ghi nhớ chat này** trong bảng gợi ý. Chạm lại vào nút đó để tắt ghi nhớ một chat.
2. **Ghi lại quyết định**: mỗi gợi ý cậu bấm *Chèn* hoặc *Sao chép* đều được ghi lại, để biết câu nào của app đã được dùng.
3. **Cô đọng mỗi ngày** (khoảng 23h, chạy ngầm khi có mạng; quá một ngày chưa chạy thì app tự chạy bù): AI đọc *sổ tay cũ + tin mới kể từ lần trước + các gợi ý đã dùng*, rồi viết lại sổ tay gồm: tổng quan, giai đoạn & xu hướng, điều đã biết về crush, **cách nhắn đã hiệu quả**, **điều nên tránh**, chuyện đang dang dở, và một dòng nhật ký cho ngày đó. Nhờ so tin của mình với phản ứng của crush, mỗi ngày AI rút thêm kinh nghiệm.
4. **Khi gợi ý**: AI chỉ đọc **sổ tay + 3 dòng nhật ký gần nhất + màn hình hiện tại**, không đọc lại cả lịch sử, nên vừa nhanh vừa rẻ.

Trong app, mục **5 · Sổ tay về crush** cho xem sổ tay, bấm **Cô đọng ngay**, tắt việc ghi nhớ, hoặc **xoá toàn bộ dữ liệu đã lưu**.

Lưu ý: app chỉ lưu được tin hiện trên màn hình khi bong bóng đang BẬT; tin cũ hơn lúc bắt đầu dùng app thì không lưu. Nếu crush đổi tên hiển thị, app coi đó là cuộc trò chuyện mới.

## Nó hoạt động thế nào

- **Bong bóng & bảng gợi ý** là cửa sổ của dịch vụ Trợ năng (`TYPE_ACCESSIBILITY_OVERLAY`), nên không cần thêm quyền "hiển thị trên ứng dụng khác".
- **Đọc tin nhắn**: lấy chữ trên màn hình qua Trợ năng. Tin nằm sát mép phải được coi là của mình, sát mép trái là của crush, ở giữa là mốc thời gian. Từ Android 11, app có thể **chụp màn hình** gửi kèm để AI đọc chính xác hơn (bong bóng và bảng được ẩn đi lúc chụp). Mặc định app chỉ chụp khi không đọc được chữ; bật "Luôn gửi kèm ảnh chụp màn hình" nếu muốn chính xác hơn.
- **AI**: gọi Claude qua [SDK Java chính thức của Anthropic](https://github.com/anthropics/anthropic-java), trả về JSON có cấu trúc (cảm xúc, phân tích, 3 gợi ý, mẹo, câu trả lời, cảnh báo). Mặc định dùng **Claude Haiku 5.5** (rẻ nhất) với effort thấp để trả lời nhanh; đổi được sang Sonnet 5.5 hoặc Opus 5.5 nếu muốn gợi ý tinh tế hơn. Với Opus / Sonnet, app bật chế độ tự chuyển model dự phòng phía máy chủ nếu bộ lọc an toàn từ chối nhầm.
- **Chèn câu trả lời**: ghi chữ vào ô nhập tin bằng `ACTION_SET_TEXT`. App nào không cho thì câu đó được sao chép để cậu tự dán.

## Quyền riêng tư & chi phí

- Chỉ khi bong bóng **BẬT** (hoặc khi bấm "Hỏi AI"), chữ và ảnh màn hình mới được gửi tới Anthropic. Khi TẮT, app không đọc gì cả.
- Tin nhắn với crush được lưu **trên máy** cho sổ tay. Lúc cô đọng, app gửi tin mới kể từ lần trước tới Anthropic. Ảnh chụp màn hình không bao giờ được lưu. Tắt hoặc xoá được trong mục 5.
- API key và dữ liệu chỉ nằm trên máy (đã tắt sao lưu).
- API của Anthropic tính tiền riêng, **không** nằm trong gói Claude Pro/Max: cần nạp credit tại console.anthropic.com. Nên đặt hạn mức chi tiêu trong mục Limits của Console.
- Chi phí ước tính mỗi lần gợi ý (chưa đo thực tế):

  | Model | Không gửi ảnh | Có gửi ảnh |
  |---|---|---|
  | Haiku 5.5 (mặc định) | ~10–15đ | ~15–20đ |
  | Sonnet 5.5 | ~200đ | ~300–400đ |
  | Opus 5.5 | ~400đ | ~500–800đ |

  Cô đọng sổ tay mỗi ngày với Haiku 5.5: khoảng 50–100đ/ngày (tuỳ nhắn nhiều hay ít).

## Build từ mã nguồn

Cần JDK 17 và Android SDK (API 35). Mở thư mục `crush-coach/` bằng Android Studio, hoặc:

```bash
cd crush-coach
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Mỗi lần đẩy code có thay đổi trong `crush-coach/`, GitHub Actions (`.github/workflows/crush-coach-apk.yml`) tự build và đính kèm APK.

Yêu cầu: Android 8.0 (API 26) trở lên; chụp màn hình cần Android 11 (API 30) trở lên.

> Mỗi lần CI build, APK được ký bằng một khóa debug mới, nên khi cài bản mới có thể phải gỡ bản cũ trước.
