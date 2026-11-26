extends Control
## Can ao noi: cham nua trai man hinh de hien can, keo de di chuyen.

const RADIUS := 36.0
var value := Vector2.ZERO
var _finger := -1
var _origin := Vector2.ZERO
var _knob := Vector2.ZERO


func _ready() -> void:
	set_anchors_preset(Control.PRESET_FULL_RECT)
	mouse_filter = Control.MOUSE_FILTER_IGNORE


func _input(event: InputEvent) -> void:
	if event is InputEventScreenTouch:
		if event.pressed and _finger == -1 and event.position.x < size.x * 0.6:
			_finger = event.index
			_origin = event.position
			_knob = _origin
			queue_redraw()
		elif not event.pressed and event.index == _finger:
			_finger = -1
			value = Vector2.ZERO
			queue_redraw()
	elif event is InputEventScreenDrag and event.index == _finger:
		var d: Vector2 = (event.position - _origin).limit_length(RADIUS)
		_knob = _origin + d
		value = d / RADIUS
		queue_redraw()


func _draw() -> void:
	if _finger == -1:
		return
	draw_circle(_origin, RADIUS + 6.0, Color(1, 1, 1, 0.18))
	draw_arc(_origin, RADIUS + 6.0, 0, TAU, 48, Color(1, 1, 1, 0.5), 1.0)
	draw_circle(_knob, 14.0, Color(1, 1, 1, 0.55))
