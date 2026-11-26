extends Node3D
## Canh mau HD-2D: the gioi 3D that, chat lieu pixel, the pixel cho cay co, nhan vat la sprite 2D.
## Toan bo dung bang code tu art/tex va art/sprites. 1 don vi = 32 diem anh.

const MW := 48.0
const MD := 36.0
const LEVEL := -0.3
const TILT := -0.49            # the nga ra sau ~28 do de khong bi det khi nhin tu tren xuong
const CAM_OFF := Vector3(0.0, 15.3, 12.9)

var rng := RandomNumberGenerator.new()
var stick: Node = null
var player: Node3D
var hero: Sprite3D
var cam: Camera3D
var mill_blades: Node3D
var _mats := {}
var _sphere: SphereMesh
var _blockers: Array[Vector3] = []     # (x, z, ban kinh)
var _t := 0.0


func _ready() -> void:
	rng.seed = 2611
	_env()
	_terrain()
	_house(Vector3(14.0, 0.0, 6.0))
	_windmill(Vector3(38.0, 0.0, 7.0))
	_pier()
	_field()
	_plants()
	_trees_and_rocks()
	_player()


# ------------------------------------------------------------ dia hinh
func path_z(x: float) -> float:
	return 11.5 + 1.5 * sin(x * 0.2)


func pond_d(x: float, z: float) -> float:
	return Vector2((x - 31.0) / 7.0, (z - 24.0) / 5.0).length() + 0.08 * sin(x * 1.3) + 0.06 * sin(z * 1.7)


func dirt_w(x: float, z: float) -> float:
	var w := smoothstep(1.7, 1.0, absf(z - path_z(x)))
	if z > 7.0 and z < path_z(x):
		w = maxf(w, smoothstep(1.3, 0.8, absf(x - 14.0)))
	return w


func sand_w(x: float, z: float) -> float:
	return smoothstep(1.5, 1.25, pond_d(x, z))


func height(x: float, z: float) -> float:
	var base := 0.12 * sin(x * 0.31) * cos(z * 0.27) + 0.05 * sin(x * 0.9 + z * 0.6)
	return base * (1.0 - sand_w(x, z)) - 1.3 * smoothstep(1.2, 0.55, pond_d(x, z)) - 0.06 * dirt_w(x, z)


func on_pier(x: float, z: float) -> bool:
	return absf(x - 31.0) < 0.85 and z > 16.4 and z < 22.6


func ground(x: float, z: float) -> Vector3:
	return Vector3(x, height(x, z), z)


func _terrain() -> void:
	var res := 4
	var iw := int(MW) * res
	var ih := int(MD) * res
	var splat := Image.create(iw, ih, false, Image.FORMAT_RGB8)
	var himg := Image.create(iw, ih, false, Image.FORMAT_RH)
	for j in ih:
		for i in iw:
			var x := (i + 0.5) / res
			var z := (j + 0.5) / res
			splat.set_pixel(i, j, Color(dirt_w(x, z), sand_w(x, z), 0.0))
			himg.set_pixel(i, j, Color(height(x, z), 0.0, 0.0))
	var st := SurfaceTool.new()
	st.begin(Mesh.PRIMITIVE_TRIANGLES)
	var step := 0.5
	var mx := 14.0     # le ngoai vung choi de may quay khong thay mep ban do
	var nx := int((MW + mx * 2.0) / step) + 1
	var nz := int((MD + mx * 2.0) / step) + 1
	for j in nz:
		for i in nx:
			var x := i * step - mx
			var z := j * step - mx
			st.set_uv(Vector2(x, z))
			st.add_vertex(ground(x, z))
	for j in nz - 1:
		for i in nx - 1:
			var a := j * nx + i
			st.add_index(a); st.add_index(a + 1); st.add_index(a + nx)
			st.add_index(a + 1); st.add_index(a + nx + 1); st.add_index(a + nx)
	st.generate_normals()
	var m := ShaderMaterial.new()
	m.shader = load("res://shaders/terrain.gdshader")
	m.set_shader_parameter("grass_tex", load("res://art/tex/co.png"))
	m.set_shader_parameter("dirt_tex", load("res://art/tex/dat.png"))
	m.set_shader_parameter("sand_tex", load("res://art/tex/cat.png"))
	m.set_shader_parameter("splat", ImageTexture.create_from_image(splat))
	m.set_shader_parameter("map_size", Vector2(MW, MD))
	var mi := MeshInstance3D.new()
	mi.mesh = st.commit()
	mi.material_override = m
	mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	add_child(mi)

	var wm := ShaderMaterial.new()
	wm.shader = load("res://shaders/water.gdshader")
	wm.set_shader_parameter("height_tex", ImageTexture.create_from_image(himg))
	wm.set_shader_parameter("map_size", Vector2(MW, MD))
	wm.set_shader_parameter("level", LEVEL)
	var plane := PlaneMesh.new()
	plane.size = Vector2(24.0, 18.0)
	var w := MeshInstance3D.new()
	w.mesh = plane
	w.material_override = wm
	w.position = Vector3(31.0, LEVEL, 24.0)
	w.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	add_child(w)


func _env() -> void:
	var e := Environment.new()
	e.background_mode = Environment.BG_COLOR
	e.background_color = Color(0.36, 0.56, 0.32)
	e.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	e.ambient_light_color = Color(0.66, 0.74, 0.90)
	e.ambient_light_energy = 0.42
	e.tonemap_mode = Environment.TONE_MAPPER_LINEAR
	e.glow_enabled = true
	e.glow_intensity = 0.3
	e.glow_bloom = 0.0
	e.glow_hdr_threshold = 1.0
	var we := WorldEnvironment.new()
	we.environment = e
	add_child(we)
	var sun := DirectionalLight3D.new()
	sun.light_color = Color(1.0, 0.93, 0.78)
	sun.light_energy = 0.95
	sun.shadow_enabled = true
	sun.directional_shadow_mode = DirectionalLight3D.SHADOW_ORTHOGONAL
	sun.directional_shadow_max_distance = 42.0
	sun.shadow_blur = 0.6
	add_child(sun)
	sun.look_at_from_position(Vector3(-6.0, 10.0, -3.0), Vector3.ZERO)


# ------------------------------------------------------------ vat lieu & khoi
func mat(name_: String, scale := 1.0, world_space := true) -> StandardMaterial3D:
	var key := "%s|%s|%s" % [name_, scale, world_space]
	if not _mats.has(key):
		var m := StandardMaterial3D.new()
		m.albedo_texture = load("res://art/tex/%s.png" % name_)
		m.texture_filter = BaseMaterial3D.TEXTURE_FILTER_NEAREST
		m.uv1_triplanar = true
		m.uv1_world_triplanar = world_space
		m.uv1_scale = Vector3.ONE * scale
		m.roughness = 1.0
		m.metallic_specular = 0.0
		_mats[key] = m
	return _mats[key]


func box(parent: Node3D, size: Vector3, pos: Vector3, m: Material, shadow := true) -> MeshInstance3D:
	var b := BoxMesh.new()
	b.size = size
	return _mesh(parent, b, pos, m, shadow)


func cyl(parent: Node3D, top: float, bottom: float, h: float, pos: Vector3, m: Material, sides := 10) -> MeshInstance3D:
	var c := CylinderMesh.new()
	c.top_radius = top
	c.bottom_radius = bottom
	c.height = h
	c.radial_segments = sides
	c.rings = 1
	return _mesh(parent, c, pos, m, true)


func _mesh(parent: Node3D, mesh: Mesh, pos: Vector3, m: Material, shadow: bool) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	mi.mesh = mesh
	mi.material_override = m
	mi.position = pos
	if not shadow:
		mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	parent.add_child(mi)
	return mi


func blob(parent: Node3D, pos: Vector3, scale_: Vector3, m: Material) -> void:
	if _sphere == null:
		_sphere = SphereMesh.new()
		_sphere.radial_segments = 9
		_sphere.rings = 5
	var mi := _mesh(parent, _sphere, pos, m, true)
	mi.scale = scale_ * 2.0


## Rai the pixel. pts: mang Vector3 (diem chan). px: so diem anh moi don vi.
func cards(sprite: String, pts: Array, px := 32.0, sway := 0.06, tilt := TILT, lit := 1.0) -> void:
	if pts.is_empty():
		return
	var t: Texture2D = load("res://art/sprites/%s.png" % sprite)
	var q := QuadMesh.new()
	q.size = Vector2(t.get_width(), t.get_height()) / px
	q.center_offset = Vector3(0.0, q.size.y / 2.0, 0.0)
	var m := ShaderMaterial.new()
	m.shader = load("res://shaders/card.gdshader")
	m.set_shader_parameter("tex", t)
	m.set_shader_parameter("sway", sway)
	m.set_shader_parameter("lit", lit)
	q.material = m
	var mm := MultiMesh.new()
	mm.transform_format = MultiMesh.TRANSFORM_3D
	mm.use_colors = true
	mm.mesh = q
	mm.instance_count = pts.size()
	for i in pts.size():
		var s := 1.0 if sway == 0.0 else rng.randf_range(0.85, 1.25)
		var flip := -1.0 if (sway != 0.0 and rng.randf() < 0.5) else 1.0
		var b := Basis(Vector3.RIGHT, tilt) * Basis.from_scale(Vector3(s * flip, s, 1.0))
		mm.set_instance_transform(i, Transform3D(b, pts[i]))
		var v := 1.0 if sway == 0.0 else rng.randf_range(0.86, 1.08)
		mm.set_instance_color(i, Color(v, v, v))
	var mi := MultiMeshInstance3D.new()
	mi.multimesh = mm
	mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	add_child(mi)


# ------------------------------------------------------------ cong trinh
func _house(at: Vector3) -> void:
	var n := Node3D.new()
	n.position = at + Vector3(0.0, height(at.x, at.z) - 0.1, 0.0)
	add_child(n)
	var w := 5.6
	var d := 3.8
	var wall_h := 2.5
	box(n, Vector3(w + 0.3, 0.6, d + 0.3), Vector3(0, 0.3, 0), mat("da_xay"))
	box(n, Vector3(w, wall_h, d), Vector3(0, 0.6 + wall_h / 2.0, 0), mat("vua"))
	for sx in [-1.0, 1.0]:
		for sz in [-1.0, 1.0]:
			box(n, Vector3(0.24, wall_h, 0.24), Vector3(sx * w / 2.0, 0.6 + wall_h / 2.0, sz * d / 2.0), mat("go_sam"))
	box(n, Vector3(w + 0.1, 0.2, 0.2), Vector3(0, 0.6 + wall_h - 0.1, d / 2.0 + 0.02), mat("go_sam"))
	box(n, Vector3(w + 0.1, 0.16, 0.12), Vector3(0, 1.75, d / 2.0 + 0.02), mat("go_sam"))
	# mai rom: hai mat doc + hai dau hoi
	var y0 := 0.6 + wall_h - 0.15
	var y1 := y0 + 2.3
	var hx := w / 2.0 + 0.55
	var hz := d / 2.0 + 0.7
	var slope := Vector2(hz, y1 - y0).length()
	var st := SurfaceTool.new()
	st.begin(Mesh.PRIMITIVE_TRIANGLES)
	for side in [1.0, -1.0]:
		var nrm := Vector3(0.0, hz, side * (y1 - y0)).normalized()
		var quad := [Vector3(-hx, y0, side * hz), Vector3(hx, y0, side * hz), Vector3(hx, y1, 0.0), Vector3(-hx, y1, 0.0)]
		var uvs := [Vector2(0, slope), Vector2(hx * 2.0, slope), Vector2(hx * 2.0, 0), Vector2(0, 0)]
		for k in [0, 1, 2, 0, 2, 3]:
			st.set_normal(nrm)
			st.set_uv(uvs[k])
			st.add_vertex(quad[k])
	var rm := StandardMaterial3D.new()
	rm.albedo_texture = load("res://art/tex/rom.png")
	rm.texture_filter = BaseMaterial3D.TEXTURE_FILTER_NEAREST
	rm.cull_mode = BaseMaterial3D.CULL_DISABLED
	rm.roughness = 1.0
	rm.metallic_specular = 0.0
	_mesh(n, st.commit(), Vector3.ZERO, rm, true)
	var sg := SurfaceTool.new()
	sg.begin(Mesh.PRIMITIVE_TRIANGLES)
	for side in [1.0, -1.0]:
		var gx: float = side * (w / 2.0)
		for v in [Vector3(gx, y0, d / 2.0 + 0.3), Vector3(gx, y0, -d / 2.0 - 0.3), Vector3(gx, y1 - 0.25, 0.0)]:
			sg.set_normal(Vector3(side, 0, 0))
			sg.add_vertex(v)
	var gm: StandardMaterial3D = mat("vua").duplicate()
	gm.cull_mode = BaseMaterial3D.CULL_DISABLED
	_mesh(n, sg.commit(), Vector3.ZERO, gm, true)
	box(n, Vector3(hx * 2.0 + 0.1, 0.22, 0.5), Vector3(0, y1, 0), mat("rom"))
	box(n, Vector3(0.7, 1.6, 0.7), Vector3(1.6, y1 - 0.3, -0.9), mat("da_xay"))
	var front := n.position + Vector3(0.0, 0.6, d / 2.0 + 0.04)
	cards("cua", [front + Vector3(0.0, -0.05, 0.0)], 24.0, 0.0, 0.0)
	cards("cua_so", [front + Vector3(-1.75, 0.75, 0.0), front + Vector3(1.75, 0.75, 0.0)], 30.0, 0.0, 0.0)
	_blockers.append(Vector3(at.x - 1.6, at.z, 2.3))
	_blockers.append(Vector3(at.x + 1.6, at.z, 2.3))
	for p in [Vector3(3.6, 0.4, 2.3), Vector3(4.4, 0.4, 2.0), Vector3(3.95, 1.15, 2.15)]:
		box(n, Vector3.ONE * 0.8, p, mat("thung", 1.25, false)).rotation.y = rng.randf_range(-0.3, 0.3)
	_blockers.append(Vector3(at.x + 4.0, at.z + 2.1, 0.9))


func _windmill(at: Vector3) -> void:
	var n := Node3D.new()
	n.position = at + Vector3(0.0, height(at.x, at.z) - 0.1, 0.0)
	add_child(n)
	cyl(n, 1.75, 1.85, 1.0, Vector3(0, 0.5, 0), mat("da_xay"), 12)
	cyl(n, 1.05, 1.6, 4.4, Vector3(0, 3.2, 0), mat("vua"), 12)
	cyl(n, 1.12, 1.12, 0.2, Vector3(0, 5.35, 0), mat("go_sam"), 12)
	cyl(n, 0.0, 1.45, 1.7, Vector3(0, 6.25, 0), mat("rom"), 12)
	box(n, Vector3(0.2, 0.2, 0.9), Vector3(0, 4.6, 1.3), mat("go_sam"))
	mill_blades = Node3D.new()
	mill_blades.position = Vector3(0, 4.6, 1.75)
	n.add_child(mill_blades)
	for i in 4:
		var arm := Node3D.new()
		arm.rotation.z = i * PI / 2.0
		mill_blades.add_child(arm)
		box(arm, Vector3(0.12, 3.3, 0.08), Vector3(0, 1.65, 0), mat("go_sam", 1.0, false))
		box(arm, Vector3(0.75, 2.4, 0.03), Vector3(0.44, 2.0, 0), mat("vai", 1.0, false))
	cards("cua", [n.position + Vector3(0.0, 0.1, 1.72)], 26.0, 0.0, 0.0)
	_blockers.append(Vector3(at.x, at.z, 2.2))


func _pier() -> void:
	var n := Node3D.new()
	add_child(n)
	var z := 16.5
	while z < 22.6:
		box(n, Vector3(1.8, 0.09, 0.34), Vector3(31.0, 0.12 + rng.randf_range(-0.01, 0.01), z), mat("van_go", 1.0, false))
		z += 0.4
	for pz in [16.6, 18.6, 20.6, 22.5]:
		for sx in [-0.95, 0.95]:
			box(n, Vector3(0.16, 2.1, 0.16), Vector3(31.0 + sx, -0.25, pz), mat("go_sam"))
	for sx in [-0.95, 0.95]:
		box(n, Vector3(0.06, 0.06, 5.9), Vector3(31.0 + sx, 0.72, 19.55), mat("go_sam"))


func _fence(a: Vector3, b: Vector3) -> void:
	var len_ := a.distance_to(b)
	var count := int(round(len_ / 1.3))
	for i in count + 1:
		var p := a.lerp(b, float(i) / count)
		box(self, Vector3(0.16, 0.95, 0.16), ground(p.x, p.z) + Vector3(0, 0.42, 0), mat("go_sam"))
	for y in [0.35, 0.7]:
		var mid := (a + b) / 2.0
		var r := box(self, Vector3(len_, 0.13, 0.11), ground(mid.x, mid.z) + Vector3(0, y, 0), mat("go_sam", 1.0, false))
		r.rotation.y = atan2(-(b.z - a.z), b.x - a.x)


func _field() -> void:
	var x0 := 3.0
	var x1 := 11.5
	var z0 := 14.6
	var z1 := 22.0
	var a: Array = []
	var b: Array = []
	var z := z0 + 0.3
	while z < z1 - 0.2:
		var x := x0 + 0.3
		while x < x1 - 0.2:
			var p := ground(x + rng.randf_range(-0.1, 0.1), z + rng.randf_range(-0.1, 0.1))
			if rng.randf() < 0.5:
				a.append(p)
			else:
				b.append(p)
			x += 0.26
		z += 0.26
	cards("lua_1", a, 30.0, 0.09)
	cards("lua_2", b, 30.0, 0.09)
	_fence(Vector3(x0, 0, z0), Vector3(x1, 0, z0))
	_fence(Vector3(x0, 0, z1), Vector3(x1, 0, z1))
	_fence(Vector3(x0, 0, z0), Vector3(x0, 0, z1))
	_fence(Vector3(x1, 0, z0), Vector3(x1, 0, z0 + 2.6))
	_fence(Vector3(x1, 0, z0 + 4.4), Vector3(x1, 0, z1))


func _is_grass(x: float, z: float, margin := 0.0) -> bool:
	if x < 0.5 or z < 0.5 or x > MW - 0.5 or z > MD - 0.5:
		return false
	if dirt_w(x, z) > 0.25 or pond_d(x, z) < 1.5 + margin * 0.1:
		return false
	if x > 2.6 and x < 11.9 and z > 14.2 and z < 22.4:
		return false
	for bl in _blockers:
		if Vector2(x - bl.x, z - bl.y).length() < bl.z + 0.6 + margin:
			return false
	return true


func _plants() -> void:
	var kinds := {"co_1": [], "co_2": [], "co_3": [], "co_kho": []}
	for i in 44000:
		var x := rng.randf_range(0.0, MW)
		var z := rng.randf_range(0.0, MD)
		if not _is_grass(x, z):
			continue
		var patch := sin(x * 0.5) * sin(z * 0.43) + 0.5 * sin(x * 0.13 + z * 0.21)
		if rng.randf() > 0.04 + 0.96 * smoothstep(-0.1, 0.6, patch):
			continue
		var r := rng.randf()
		var dry := sin(x * 0.21 + 1.0) * sin(z * 0.17) > 0.35
		var k := "co_1"
		if dry and r < 0.45:
			k = "co_kho"
		elif r < 0.35:
			k = "co_2"
		elif r < 0.62:
			k = "co_3"
		kinds[k].append(ground(x, z))
	for k in kinds:
		cards(k, kinds[k], 30.0, 0.07)

	var reeds: Array = []
	var pads: Array = []
	var pads2: Array = []
	for i in 1400:
		var x := rng.randf_range(20.0, 42.0)
		var z := rng.randf_range(16.0, 32.0)
		var d := pond_d(x, z)
		if d > 1.02 and d < 1.3 and not on_pier(x, z - 1.0) and sin(x * 0.9) + sin(z * 1.1) > -0.3:
			reeds.append(ground(x, z))
		elif d < 0.8 and d > 0.3 and i < 260 and sin(x * 1.7) * sin(z * 1.3) > 0.45 and not on_pier(x, z):
			if rng.randf() < 0.3:
				pads2.append(Vector3(x, LEVEL + 0.02, z))
			else:
				pads.append(Vector3(x, LEVEL + 0.02, z))
	cards("lau", reeds, 30.0, 0.08)
	cards("la_sung", pads, 26.0, 0.0, -PI / 2.0)
	cards("la_sung_hoa", pads2, 26.0, 0.0, -PI / 2.0)

	var fl := {"hoa_trang": [], "hoa_vang": [], "hoa_hong": [], "hoa_tim": []}
	var names := fl.keys()
	for i in 46:
		var cx := rng.randf_range(1.0, MW - 1.0)
		var cz := rng.randf_range(1.0, MD - 1.0)
		var k: String = names[rng.randi() % 4]
		for j in rng.randi_range(3, 7):
			var x := cx + rng.randf_range(-0.8, 0.8)
			var z := cz + rng.randf_range(-0.6, 0.6)
			if _is_grass(x, z):
				fl[k].append(ground(x, z))
	for k in fl:
		cards(k, fl[k], 28.0, 0.05)
	cards("bien", [ground(16.3, 9.6)], 26.0, 0.0, TILT * 0.5)


func _tree(x: float, z: float, s: float) -> void:
	var n := Node3D.new()
	n.position = ground(x, z)
	add_child(n)
	cyl(n, 0.15 * s, 0.32 * s, 2.3 * s, Vector3(0, 1.1 * s, 0), mat("go_sam"), 6)
	var lm := mat("la", 0.6)
	blob(n, Vector3(0, 2.9 * s, 0), Vector3(1.0, 0.85, 1.0) * s, lm)
	for i in 6:
		var a := i * TAU / 6.0 + rng.randf_range(-0.3, 0.3)
		var r := rng.randf_range(0.6, 0.85) * s
		var sz := rng.randf_range(0.55, 0.8) * s
		blob(n, Vector3(cos(a) * r, (2.5 + rng.randf_range(-0.2, 0.7)) * s, sin(a) * r), Vector3(sz, sz * 0.8, sz), lm)
	_blockers.append(Vector3(x, z, 0.35))
	var ms: Array = []
	for i in rng.randi_range(0, 3):
		ms.append(ground(x + rng.randf_range(-1.0, 1.0), z + rng.randf_range(0.3, 1.0)))
	cards("nam_do" if rng.randf() < 0.6 else "nam_nau", ms, 30.0, 0.0, TILT * 0.6)


func _trees_and_rocks() -> void:
	var placed: Array[Vector2] = []
	var tries := 0
	while placed.size() < 34 and tries < 3000:
		tries += 1
		var p := Vector2(rng.randf_range(1.0, MW - 1.0), rng.randf_range(1.0, MD - 1.0))
		var forest := sin(p.x * 0.19 + 2.0) * sin(p.y * 0.23 + 0.5)
		if forest < 0.1 and rng.randf() < 0.85:
			continue
		if not _is_grass(p.x, p.y, 1.2) or absf(p.y - path_z(p.x)) < 3.6 or (absf(p.x - 14.0) < 3.0 and p.y < 12.0):
			continue
		var ok := true
		for q in placed:
			if q.distance_to(p) < 2.6:
				ok = false
		if ok:
			placed.append(p)
			_tree(p.x, p.y, rng.randf_range(0.85, 1.25))
	for i in 16:      # bui cay
		var p := Vector2(rng.randf_range(1.0, MW - 1.0), rng.randf_range(1.0, MD - 1.0))
		if _is_grass(p.x, p.y, 0.5):
			var n := Node3D.new()
			n.position = ground(p.x, p.y)
			add_child(n)
			for j in rng.randi_range(2, 3):
				var s := rng.randf_range(0.35, 0.55)
				blob(n, Vector3(rng.randf_range(-0.4, 0.4), s * 0.6, rng.randf_range(-0.3, 0.3)), Vector3(s, s * 0.8, s), mat("la"))
			_blockers.append(Vector3(p.x, p.y, 0.5))
	var rocks := [Vector3(27.5, 21.5, 0.9), Vector3(35.0, 26.0, 0.7), Vector3(20.5, 13.6, 0.55), Vector3(24.0, 8.5, 0.8),
		Vector3(8.0, 8.5, 0.5), Vector3(34.5, 12.5, 0.45), Vector3(29.0, 27.2, 0.5)]
	for r in rocks:
		var g := ground(r.x, r.y)
		blob(self, Vector3(g.x, maxf(g.y, LEVEL - 0.25) + r.z * 0.25, g.z), Vector3(r.z, r.z * 0.62, r.z * 0.8), mat("da"))
		blob(self, Vector3(g.x + r.z * 0.6, maxf(g.y, LEVEL - 0.25) + r.z * 0.1, g.z + r.z * 0.3), Vector3(r.z, r.z * 0.6, r.z) * 0.5, mat("da"))
		_blockers.append(Vector3(r.x, r.y, r.z * 0.8))


# ------------------------------------------------------------ nhan vat & may quay
func _player() -> void:
	player = Node3D.new()
	player.position = ground(17.5, 12.2)
	add_child(player)
	hero = Sprite3D.new()
	hero.texture = load("res://art/hero/hero_tam.png")
	hero.pixel_size = 1.0 / 40.0
	hero.centered = false
	hero.offset = Vector2(-hero.texture.get_width() / 2.0, 0.0)
	hero.texture_filter = BaseMaterial3D.TEXTURE_FILTER_NEAREST
	hero.alpha_cut = SpriteBase3D.ALPHA_CUT_DISCARD
	hero.shaded = false
	hero.modulate = Color(0.97, 0.95, 0.92)
	hero.rotation.x = TILT
	player.add_child(hero)
	cam = Camera3D.new()
	cam.fov = 30.0
	cam.rotation_degrees.x = -49.9
	cam.position = _cam_target() + CAM_OFF
	add_child(cam)
	cam.make_current()


func _cam_target() -> Vector3:
	return Vector3(clampf(player.position.x, 10.0, MW - 10.0), 0.0, clampf(player.position.z, 4.5, MD - 5.0))


func walkable(x: float, z: float) -> bool:
	if x < 0.6 or z < 0.6 or x > MW - 0.6 or z > MD - 0.6:
		return false
	if height(x, z) < LEVEL - 0.1 and not on_pier(x, z):
		return false
	for bl in _blockers:
		if Vector2(x - bl.x, z - bl.y).length() < bl.z:
			return false
	return true


func _process(delta: float) -> void:
	_t += delta
	mill_blades.rotation.z -= delta * 0.5
	var v := Input.get_vector("ui_left", "ui_right", "ui_up", "ui_down")
	if stick != null and stick.value.length() > 0.0:
		v = stick.value
	var moving := v.length() > 0.15
	if moving:
		var step := v.limit_length(1.0) * 3.4 * delta
		var p := player.position
		if walkable(p.x + step.x, p.z):
			p.x += step.x
		if walkable(p.x, p.z + step.y):
			p.z += step.y
		p.y = 0.17 if on_pier(p.x, p.z) else height(p.x, p.z)
		player.position = p
		if absf(v.x) > 0.2:
			hero.flip_h = v.x < 0.0
	hero.position.y = absf(sin(_t * 11.0)) * 0.09 if moving else sin(_t * 2.2) * 0.012
	cam.position = cam.position.lerp(_cam_target() + CAM_OFF, 1.0 - exp(-6.0 * delta))
