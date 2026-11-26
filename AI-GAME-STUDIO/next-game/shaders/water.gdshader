shader_type spatial;
render_mode blend_mix, cull_back, specular_disabled, shadows_disabled;
// Nuoc: mau theo do sau, vong bot song vo bo, lap lanh - tat ca bam luoi diem anh.
uniform sampler2D height_tex : filter_linear;
uniform vec2 map_size = vec2(48.0, 36.0);
uniform float level = -0.3;
uniform vec3 shallow : source_color = vec3(0.56, 0.85, 0.80);
uniform vec3 deep : source_color = vec3(0.20, 0.52, 0.62);
varying vec3 wpos;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }

void vertex() { wpos = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz; }

void fragment() {
	vec2 t = (floor(wpos.xz * 16.0) + 0.5) / 16.0;
	float depth = level - texture(height_tex, t / map_size).r;
	if (depth < 0.0) discard;
	float d = clamp(depth / 0.9, 0.0, 1.0);
	vec3 col = mix(shallow, deep, d);
	float a = mix(0.45, 0.9, d);
	float wob = 0.06 * sin(t.x * 2.3 + TIME * 0.7) + 0.05 * sin(t.y * 3.1 - TIME * 0.5);
	float ring = fract((depth + wob) * 3.0 - TIME * 0.22);
	float foam = step(depth + wob * 0.6, 0.05);
	if (depth < 0.55) foam = max(foam, step(0.86, ring) * (1.0 - depth / 0.55));
	float tw = hash(floor(wpos.xz * 16.0) + floor(TIME * 1.5) * 13.0);
	float streak = step(0.93, sin(t.y * 9.0 + sin(t.x * 0.7 + TIME * 0.4) * 2.0)) * step(0.55, hash(floor(vec2(t.x * 2.0 - TIME * 0.3, t.y * 16.0))));
	foam = max(foam, max(step(0.9965, tw), streak * 0.5 * d));
	col = mix(col, vec3(1.0), clamp(foam, 0.0, 1.0));
	ALBEDO = col;
	ALPHA = max(a, foam * 0.95);
	ROUGHNESS = 0.3;
}
