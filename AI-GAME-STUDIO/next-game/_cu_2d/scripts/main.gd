extends Node2D
## Ban do thu: dung toan bo bang code tu tiles/ va props/.
## Dia hinh luu tai cac GOC o (corner Wang) - xem tools/gen_tiles.py.

const T := 32
const MW := 48
const MH := 30
const PAIRS := [["co", "dat"], ["co", "cat"], ["cat", "nuoc"], ["co", "nuoc"]]
const FULL := {"dat": "co_dat", "cat": "co_cat", "nuoc": "co_nuoc"}

## ten vat -> kich thuoc hop va cham o chan (0,0 = khong chan duong)
const SOLID := {
	"cay_la_rong_1": Vector2(16, 8), "cay_la_rong_2": Vector2(16, 8), "cay_la_rong_3": Vector2(16, 8),
	"nha_mai_rom": Vector2(120, 46), "coi_xay_gio": Vector2(64, 26), "gieng_nuoc": Vector2(36, 18),
	"da_lon": Vector2(40, 16), "da_vua": Vector2(26, 10), "thung_go": Vector2(28, 14), "chum": Vector2(16, 8),
	"bien_chi_duong": Vector2(6, 4), "hang_rao_ngang": Vector2(32, 6), "hang_rao_doc": Vector2(6, 32),
	"hang_rao_coc_goc": Vector2(6, 6), "bui_cay_1": Vector2(22, 8), "bui_cay_2": Vector2(22, 8),
	"bui_hoa_1": Vector2(22, 8), "bui_hoa_2": Vector2(22, 8),
}

var corners: Array = []
var sheets := {}
var grass: Texture2D
var walk_rects: Array[Rect2] = []   # cau: bo va cham nuoc ben duoi
var keep_out: Array[Rect2] = []     # vung khong rai cay co
var world: Node2D
var rng := RandomNumberGenerator.new()


func _ready() -> void:
	rng.seed = 2611
	grass = load("res://tiles/nen_co.png")
	for p in PAIRS:
		sheets[p[0] + "_" + p[1]] = load("res://tiles/nen_%s_%s.png" % p)
	for y in MH + 1:
		var row := []
		for x in MW + 1:
			row.append(_terrain(x, y))
		corners.append(row)

	var flat := Node2D.new()
	add_child(flat)
	_flat(flat, "cau_go_ngan", Vector2(928, 440))
	_flat(flat, "cau_tau", Vector2(352, 590))
	world = Node2D.new()
	world.y_sort_enabled = true
	add_child(world)
	_water_walls()
	_village()
	_scatter()

	var player := preload("res://scripts/player.gd").new()
	player.position = Vector2(600, 470)
	world.add_child(player)
	var cam := Camera2D.new()
	cam.position_smoothing_enabled = true
	cam.position_smoothing_speed = 8.0
	cam.limit_left = 0
	cam.limit_top = 0
	cam.limit_right = MW * T
	cam.limit_bottom = MH * T
	player.add_child(cam)

	var hud := CanvasLayer.new()
	add_child(hud)
	var stick := preload("res://scripts/joystick.gd").new()
	hud.add_child(stick)
	player.stick = stick
	hud.add_child(preload("res://scripts/fps_label.gd").new())


func _terrain(x: int, y: int) -> String:
	var rc := 30
	if absf(y - 14.5) >= 3.0:
		rc = 30 + int(round(2.0 * sin((y - 14) / 5.0)))
	if x == rc or x == rc + 1:
		return "nuoc"
	var d := Vector2((x - 12) / 5.5, (y - 22) / 4.0).length()
	if d < 0.62:
		return "nuoc"
	if d < 1.3:
		return "cat"
	if (y == 14 or y == 15) and (x <= 28 or x >= 33):
		return "dat"
	if (x == 18 or x == 19) and y >= 10 and y <= 13:
		return "dat"
	return "co"


func _cell(x: int, y: int) -> Array:
	return [corners[y][x], corners[y][x + 1], corners[y + 1][x], corners[y + 1][x + 1]]


func _draw() -> void:
	for y in MH:
		for x in MW:
			var c := _cell(x, y)
			var dst := Rect2(x * T, y * T, T, T)
			var kinds := {}
			for k in c:
				kinds[k] = true
			if kinds.size() == 1:
				if c[0] == "co":
					draw_texture_rect_region(grass, dst, Rect2(((x * 7 + y * 13) % 4) * T, 0, T, T))
				else:
					draw_texture_rect_region(sheets[FULL[c[0]]], dst, Rect2(3 * T, 3 * T, T, T))
				continue
			var done := false
			for p in PAIRS:
				if kinds.size() == 2 and kinds.has(p[0]) and kinds.has(p[1]):
					var idx := 0
					for i in 4:
						if c[i] == p[1]:
							idx |= 1 << i
					draw_texture_rect_region(sheets[p[0] + "_" + p[1]], dst, Rect2((idx % 4) * T, (idx / 4) * T, T, T))
					done = true
					break
			if not done:
				push_warning("O (%d,%d) co to hop dia hinh khong ghep duoc: %s" % [x, y, str(c)])
				draw_texture_rect_region(grass, dst, Rect2(0, 0, T, T))


func _flat(parent: Node2D, name_: String, top_left: Vector2) -> void:
	var s := Sprite2D.new()
	s.texture = load("res://props/%s.png" % name_)
	s.centered = false
	s.position = top_left
	parent.add_child(s)
	var r := Rect2(top_left, s.texture.get_size())
	walk_rects.append(r)
	keep_out.append(r.grow(24))


func _water_walls() -> void:
	var body := StaticBody2D.new()
	add_child(body)
	for y in MH:
		for x in MW:
			var n := 0
			for k in _cell(x, y):
				if k == "nuoc":
					n += 1
			if n < 4:
				continue
			var r := Rect2(x * T, y * T, T, T)
			var skip := false
			for w in walk_rects:
				if w.intersects(r):
					skip = true
			if skip:
				continue
			var cs := CollisionShape2D.new()
			var sh := RectangleShape2D.new()
			sh.size = Vector2(T, T)
			cs.shape = sh
			cs.position = r.get_center()
			body.add_child(cs)


## Dat mot vat; pos = diem chan (giua day).
func prop(name_: String, pos: Vector2) -> void:
	var tex: Texture2D = load("res://props/%s.png" % name_)
	var s := Sprite2D.new()
	s.texture = tex
	s.centered = false
	s.offset = Vector2(-tex.get_width() / 2, -tex.get_height() + 2)
	s.position = pos
	world.add_child(s)
	if SOLID.has(name_):
		var size: Vector2 = SOLID[name_]
		var body := StaticBody2D.new()
		var cs := CollisionShape2D.new()
		var sh := RectangleShape2D.new()
		sh.size = size
		cs.shape = sh
		cs.position = Vector2(0, -size.y / 2)
		body.add_child(cs)
		s.add_child(body)


func _village() -> void:
	prop("nha_mai_rom", Vector2(592, 330))
	prop("gieng_nuoc", Vector2(460, 410))
	prop("thung_go", Vector2(690, 336))
	prop("thung_go", Vector2(716, 348))
	prop("chum", Vector2(500, 334))
	prop("chum", Vector2(482, 342))
	prop("bien_chi_duong", Vector2(650, 436))
	prop("coi_xay_gio", Vector2(1250, 380))
	prop("da_lon", Vector2(1130, 560))
	keep_out.append(Rect2(420, 150, 330, 290))
	keep_out.append(Rect2(1170, 180, 160, 230))
	# ruong lua co hang rao
	var fx := 128
	var fy := 250
	var cols := 7
	var rows := 4
	keep_out.append(Rect2(fx - 30, fy - 50, cols * 32 + 60, rows * 32 + 80))
	for i in cols:
		prop("hang_rao_ngang", Vector2(fx + 16 + i * 32, fy))
		if i != 3:   # chua loi vao phia duoi
			prop("hang_rao_ngang", Vector2(fx + 16 + i * 32, fy + rows * 32))
	for j in rows:
		prop("hang_rao_doc", Vector2(fx + 3, fy + 32 + j * 32))
		prop("hang_rao_doc", Vector2(fx + cols * 32 + 3, fy + 32 + j * 32))
	prop("hang_rao_coc_goc", Vector2(fx + cols * 32 + 3, fy))
	prop("hang_rao_coc_goc", Vector2(fx + cols * 32 + 3, fy + rows * 32))
	prop("hang_rao_coc_goc", Vector2(fx + 3 + 4 * 32, fy + rows * 32))
	for j in 5:
		for i in 11:
			prop("cum_lua_%d" % (1 + (i + j) % 2), Vector2(fx + 24 + i * 18 + (j % 2) * 6, fy + 26 + j * 20))


func _all_grass(p: Vector2, margin: float) -> bool:
	if p.x < 20 or p.y < 40 or p.x > MW * T - 20 or p.y > MH * T - 8:
		return false
	for o in [Vector2(-margin, -margin), Vector2(margin, -margin), Vector2(-margin, margin), Vector2(margin, margin)]:
		var q: Vector2 = p + o
		var cx := clampi(int(q.x / T), 0, MW - 1)
		var cy := clampi(int(q.y / T), 0, MH - 1)
		for k in _cell(cx, cy):
			if k != "co":
				return false
	for r in keep_out:
		if r.has_point(p):
			return false
	return true


func _scatter() -> void:
	var plan := [
		[["cay_la_rong_1", "cay_la_rong_2", "cay_la_rong_3"], 46, 20.0, 44.0],
		[["bui_cay_1", "bui_cay_2", "bui_hoa_1", "bui_hoa_2"], 34, 14.0, 26.0],
		[["da_nho", "da_vua", "da_vua", "da_lon"], 16, 14.0, 30.0],
		[["cum_co_cao_1", "cum_co_cao_2"], 120, 6.0, 0.0],
		[["hoa_1", "hoa_2", "hoa_3", "hoa_4", "nam_1", "nam_2"], 90, 6.0, 0.0],
	]
	var placed: Array[Vector2] = []
	for item in plan:
		var n := 0
		var tries := 0
		while n < item[1] and tries < 4000:
			tries += 1
			var p := Vector2(rng.randi_range(0, MW * T), rng.randi_range(0, MH * T))
			if not _all_grass(p, item[2]):
				continue
			var ok := true
			if item[3] > 0.0:
				for q in placed:
					if q.distance_to(p) < item[3]:
						ok = false
						break
			if not ok:
				continue
			if item[3] > 0.0:
				placed.append(p)
			var names: Array = item[0]
			prop(names[rng.randi() % names.size()], p)
			n += 1
