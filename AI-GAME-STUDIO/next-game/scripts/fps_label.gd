extends Label
## Hien toc do khung hinh (de do tren may that).

func _ready() -> void:
	position = Vector2(6, 4)
	add_theme_font_size_override("font_size", 10)
	add_theme_color_override("font_outline_color", Color.BLACK)
	add_theme_constant_override("outline_size", 3)


func _process(_delta: float) -> void:
	text = "%d FPS" % Engine.get_frames_per_second()
