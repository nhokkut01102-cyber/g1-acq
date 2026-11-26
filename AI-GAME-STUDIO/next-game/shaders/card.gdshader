shader_type spatial;
render_mode cull_disabled, depth_draw_opaque;
// The pixel (co, lua, hoa...): cat nen trong suot, lay gio, chieu sang nhu mat dat.
uniform sampler2D tex : source_color, filter_nearest;
uniform float sway = 0.06;
uniform float lit = 1.0;

void vertex() {
	vec3 w = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz;
	float k = 1.0 - UV.y;
	VERTEX.x += sin(TIME * 1.6 + w.x * 0.9 + w.z * 0.7) * sway * k * k;
}

void fragment() {
	vec4 c = texture(tex, UV);
	ALBEDO = c.rgb * COLOR.rgb * lit;
	ALPHA = c.a;
	ALPHA_SCISSOR_THRESHOLD = 0.5;
	NORMAL = normalize((VIEW_MATRIX * vec4(0.0, 1.0, 0.0, 0.0)).xyz);
	ROUGHNESS = 1.0;
	SPECULAR = 0.0;
}
