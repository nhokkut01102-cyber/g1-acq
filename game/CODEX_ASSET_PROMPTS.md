# Prompt vẽ tạo vật cho Codex (map nhỏ "Rừng Đèn Lụi")

Game tự nạp `game/assets/<tên>.png` nếu file tồn tại và thay hình vẽ tạm bằng code.
Yêu cầu chung cho mọi file: PNG nền trong suốt, một vật thể mỗi file, chân vật thể chạm sát mép dưới và nằm giữa theo chiều ngang, không đổ bóng xuống đất, không chữ, không khung.

## Phần phong cách (dán vào đầu mọi prompt)

```
Dark whimsical fantasy environment asset, Gothic storybook illustration, hand-drawn expressive ink
outlines, organic asymmetrical silhouette, German Expressionist design, rough textured brushwork,
richly detailed painterly surface, muted earthy color palette, 2.5D game asset in three-quarter
top-down perspective, high-definition hand-painted 2D, single isolated object on a fully transparent
background, object base touching the bottom edge and horizontally centered, soft neutral lighting
from upper left, no cast shadow on the ground.
No pixel art, no plastic-looking surfaces, no generic vector graphics, no rigid geometric vegetation,
no oversaturated colors, no photorealism, no excessive cartoon exaggeration, no text.
```

## Danh sách tạo vật

| File | Kích thước gợi ý | Mô tả thêm vào prompt |
|---|---|---|
| `tree_1.png` … `tree_5.png` | 840×880 | Twisted ancient tree with a gnarled leaning trunk, naturally curved branches, clumps of dark moss-green foliage, exposed roots. Mỗi file một dáng khác nhau (nghiêng trái, nghiêng phải, chẻ đôi, thấp lùn, cao gầy). |
| `tree_dead_1.png`, `tree_dead_2.png` | 840×880 | Dead leafless twisted tree, pale grey bark, claw-like branches, strands of hanging moss. |
| `arch.png` | 520×500 | Broken medieval stone archway, two crooked pillars of irregular blocks, the arch collapsed on the right side, moss on top. |
| `wall_1.png` | 324×220 | Crumbling low stone wall, three blocks wide, uneven height, moss-covered. |
| `wall_2.png` | 236×220 | Crumbling low stone wall, two blocks wide, uneven height, moss-covered. |
| `grave_1.png`, `grave_2.png` | 120×160 | Small tilted weathered gravestone with a carved cross, moss at the base. |
| `lantern.png` | 180×300 | Crooked wooden post bent at the top with a small hanging lantern glowing warm amber. |
| `mushrooms.png` | 140×120 | Cluster of four thin pale mushrooms with faintly glowing teal caps. |
| `fern.png` | 180×140 | Low wild fern tuft with curved fronds. |

## Việc cho Codex

1. Sinh từng ảnh theo bảng trên, lưu vào `game/assets/` đúng tên file.
2. Kiểm tra nền trong suốt thật (kênh alpha), cắt sát vật thể, giữ chân vật thể ở mép dưới.
3. Mở `game/index.html` để xem các tạo vật đứng trong map có ăn khớp nhau không.

Nhân vật và quái hiện vẫn vẽ bằng code vì chúng có chuyển động; khi cần thay bằng sprite sheet thì làm ở bước sau.
