# 🇰🇷 Hàn Bubble

App Android luyện tiếng Hàn bằng chính những câu cậu viết mỗi ngày. Cơ chế giống Crush Coach: một **bong bóng nổi** trên màn hình, chạm để **BẬT / TẮT**. Khi bật, **mỗi câu cậu gõ xong ở bất kỳ app nào** (Messenger, Zalo, ghi chú…) được dịch sang tiếng Hàn và hiện trong **một khung riêng**:

- Câu tiếng Hàn **tô màu theo từ loại**: danh từ, động từ, tính từ, trợ từ, đuôi câu/ngữ pháp…
- Phiên âm Latinh và nghĩa tiếng Việt.
- **Văn phong** được chọn theo cách cậu viết: 반말 (thân mật), 해요체 (lịch sự thân thiện), 합니다체 (trang trọng).
- Bấm **▾ Từ loại & ngữ pháp** để xem **dạng gốc** của từng từ (가 → 가다), **ngữ pháp gốc** (-고 싶다) và **cách chia** theo văn phong (가다 → 가고 싶어요, 해요체).
- 🔊 nghe đọc tiếng Hàn (giọng đọc có sẵn trên máy). **✕** để đóng khung.
- Nếu cậu viết thẳng bằng tiếng Hàn, AI sửa lỗi và giải thích.

Mọi câu được lưu vào **📚 Sổ học** trong app, cũng tô màu và có giải thích, để đọc lại và học lại.

## Bảng màu từ loại

| Màu | Từ loại |
|---|---|
| 🔵 xanh dương | Danh từ 명사 |
| 🩵 xanh ngọc | Đại từ 대명사 |
| 🔴 đỏ | Động từ 동사 |
| 🟠 cam | Tính từ 형용사 |
| 🟢 xanh lá | Trạng từ 부사 |
| 🟣 tím | Trợ từ 조사 |
| 🩷 hồng đậm | Đuôi câu / ngữ pháp 어미 |
| 🟤 nâu | Số từ 수사 |
| 🫒 ô liu | Định từ 관형사 |
| 🟡 vàng | Thán từ 감탄사 |
| ⚫ xám xanh | Liên từ 접속사 |

## Khi nào một câu được dịch

- Câu kết thúc bằng `.` `!` `?` `…` hoặc xuống dòng → dịch khi cậu ngừng gõ khoảng 1 giây.
- Câu chưa có dấu kết thúc → dịch khi ngừng gõ khoảng 3 giây, hoặc ngay khi bấm gửi (ô nhập bị xoá trắng).
- Nếu gõ tiếp hoặc sửa một câu đã dịch dở, bản dịch mới **thay** bản cũ trong Sổ học, không tạo bản trùng.
- Không bao giờ đọc ô mật khẩu. Câu chỉ có emoji, số hay đường link thì bỏ qua.

## Cài đặt

1. **Tải APK**: tab **Actions** của repo → lần chạy mới nhất của **Han Bubble APK** → tải `han-bubble-apk` (zip) → giải nén lấy `app-debug.apk` → cài lên điện thoại.
2. **API key Anthropic**: dán vào mục **1 · Kết nối AI** (dùng chung key với Crush Coach được).
3. **Bật Trợ năng** cho **Hàn Bubble – dịch câu sang tiếng Hàn**. Android 13+ mà mục bị mờ: Cài đặt → Ứng dụng → Hàn Bubble → ⋮ → **Cho phép cài đặt bị hạn chế**.
4. Bật **Hiện bong bóng trên màn hình** → chạm bong bóng (chuyển xanh, chữ `ON`) → nhắn tin như bình thường.
5. Muốn nghe 🔊: cài giọng tiếng Hàn trong Cài đặt → Hệ thống → Ngôn ngữ → Chuyển văn bản thành giọng nói (nếu máy chưa có).

Mục **3 · Tuỳ chọn dịch** cho phép cố định văn phong, bật/tắt phiên âm, và mở sẵn phần từ loại & ngữ pháp. Mục **✍️ Thử dịch nhanh** cho phép dịch một câu ngay trong app.

## Cách hoạt động

- Bong bóng và khung dịch là cửa sổ của dịch vụ Trợ năng (`TYPE_ACCESSIBILITY_OVERLAY`). Khung dịch không giành bàn phím nên cậu vẫn gõ tiếp bình thường.
- Mỗi câu chỉ cần **một lần gọi Claude** (SDK Java chính thức của Anthropic, đầu ra JSON có cấu trúc): câu tiếng Hàn, phiên âm, nghĩa, văn phong, các mẩu từ kèm từ loại / dạng gốc / nghĩa, và các điểm ngữ pháp. App tự dò vị trí từng mẩu trong câu để tô màu, nên AI có lệch khoảng trắng thì màu vẫn đúng chỗ.
- Mặc định dùng **Claude Haiku 5.5** (rẻ nhất). Đổi được sang Sonnet 5.5 / Opus 5.5 nếu muốn giải thích kỹ hơn; khi đó app bật chế độ tự chuyển model dự phòng nếu bị từ chối nhầm.
- Sổ học lưu trong SQLite trên máy, không sao lưu lên mạng.

## Chi phí (ước tính, chưa đo thực tế)

API Anthropic tính tiền riêng, **không** nằm trong gói Claude Pro/Max.

| Model | Mỗi câu |
|---|---|
| Haiku 5.5 (mặc định) | ~10–20đ |
| Sonnet 5.5 | ~200đ |
| Opus 5.5 | ~400đ |

Nhắn nhiều thì tốn nhiều: bật bong bóng khi muốn luyện, tắt khi không cần. Nên đặt hạn mức chi tiêu trong console.anthropic.com (mục Limits).

## Build từ mã nguồn

Cần JDK 17 và Android SDK (API 35):

```bash
cd han-bubble
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Yêu cầu: Android 8.0 (API 26) trở lên.
