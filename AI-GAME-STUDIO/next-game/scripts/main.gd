extends Control
## Khung chinh: canh 3D ve o do phan giai thap (~640x360) roi phong len,
## qua shader hau ky (lam mo xa gan, am mau). Giao dien nam o lop tren.

func _ready() -> void:
	var c := SubViewportContainer.new()
	c.stretch = true
	c.set_anchors_preset(Control.PRESET_FULL_RECT)
	c.mouse_filter = Control.MOUSE_FILTER_IGNORE
	c.texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR
	var pm := ShaderMaterial.new()
	pm.shader = load("res://shaders/post.gdshader")
	c.material = pm
	add_child(c)
	var vp := SubViewport.new()
	c.add_child(vp)
	var world := preload("res://scripts/world.gd").new()
	world.name = "World"
	vp.add_child(world)
	var stick := preload("res://scripts/joystick.gd").new()
	add_child(stick)
	world.stick = stick
	add_child(preload("res://scripts/fps_label.gd").new())
