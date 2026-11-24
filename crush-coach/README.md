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

## Nó hoạt động thế nào

- **Bong bóng & bảng gợi ý** là cửa sổ của dịch vụ Trợ năng (`TYPE_ACCESSIBILITY_OVERLAY`), nên không cần thêm quyền "hiển thị trên ứng dụng khác".
- **Đọc tin nhắn**: lấy chữ trên màn hình qua Trợ năng. Tin nằm sát mép phải được coi là của mình, sát mép trái là của crush, ở giữa là mốc thời gian. Từ Android 11, app còn **chụp màn hình** gửi kèm để AI đọc chính xác hơn (bong bóng và bảng được ẩn đi lúc chụp).
- **AI**: gọi Claude qua [SDK Java chính thức của Anthropic](https://github.com/anthropics/anthropic-java), trả về JSON có cấu trúc (cảm xúc, phân tích, 3 gợi ý, mẹo, câu trả lời, cảnh báo). Mặc định dùng Claude Opus 5.5 với effort thấp để trả lời nhanh; đổi được sang Sonnet 5.5 hoặc Haiku 5.5. Với Opus / Sonnet, app bật chế độ tự chuyển model dự phòng phía máy chủ nếu bộ lọc an toàn từ chối nhầm.
- **Chèn câu trả lời**: ghi chữ vào ô nhập tin bằng `ACTION_SET_TEXT`. App nào không cho thì câu đó được sao chép để cậu tự dán.

## Quyền riêng tư & chi phí

- Chỉ khi bong bóng **BẬT** (hoặc khi bấm "Hỏi AI"), chữ và ảnh màn hình mới được gửi tới Anthropic. Khi TẮT, app không đọc gì cả.
- App không lưu lịch sử chat. API key chỉ lưu trên máy (đã tắt sao lưu).
- Mỗi lần gợi ý tốn một ít tiền trên tài khoản Anthropic của cậu (ảnh màn hình chiếm phần lớn). Muốn rẻ hơn: chọn Haiku 5.5 hoặc tắt "Gửi kèm ảnh chụp màn hình".

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
