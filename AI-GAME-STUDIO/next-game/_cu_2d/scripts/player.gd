extends CharacterBody2D
## Nhan vat: 8 huong, Idle/Running. Tu dong dung sprite PixelLab neu co o
## res://characters/hero/<dong tac>/<huong>/NN.png, khong thi dung ban tam.

const SPEED := 110.0
const DIRS := ["east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east"]
const ANIMS := {"idle": 5.0, "run": 10.0}

var stick: Node = null
var facing := 2
var sprite: AnimatedSprite2D


func _ready() -> void:
	motion_mode = CharacterBody2D.MOTION_MODE_FLOATING
	var cs := CollisionShape2D.new()
	var sh := CircleShape2D.new()
	sh.radius = 6.0
	cs.shape = sh
	cs.position = Vector2(0, -5)
	add_child(cs)
	sprite = AnimatedSprite2D.new()
	sprite.sprite_frames = _load_frames()
	sprite.offset = Vector2(0, -27)
	add_child(sprite)
	sprite.play("idle_south")


func _load_frames() -> SpriteFrames:
	var base := "res://characters/hero"
	if not DirAccess.dir_exists_absolute(base + "/idle/south"):
		base = "res://characters/hero_placeholder"
	var sf := SpriteFrames.new()
	sf.remove_animation("default")
	for anim in ANIMS:
		for d in DIRS:
			var key: String = "%s_%s" % [anim, d]
			sf.add_animation(key)
			sf.set_animation_speed(key, ANIMS[anim])
			sf.set_animation_loop(key, true)
			var dir := "%s/%s/%s" % [base, anim, d]
			var files := {}
			for f in DirAccess.get_files_at(dir):
				f = f.trim_suffix(".import").trim_suffix(".remap")
				if f.ends_with(".png"):
					files[f] = true
			var names := files.keys()
			names.sort()
			for f in names:
				sf.add_frame(key, load(dir + "/" + f))
	return sf


func _physics_process(_delta: float) -> void:
	var v := Input.get_vector("ui_left", "ui_right", "ui_up", "ui_down")
	if stick != null and stick.value.length() > 0.0:
		v = stick.value
	if v.length() > 0.15:
		facing = posmod(int(round(v.angle() / (PI / 4.0))), 8)
		velocity = v.limit_length(1.0) * SPEED
		_play("run")
	else:
		velocity = Vector2.ZERO
		_play("idle")
	move_and_slide()


func _play(anim: String) -> void:
	var key := "%s_%s" % [anim, DIRS[facing]]
	if sprite.animation != key:
		sprite.play(key)
