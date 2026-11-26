shader_type spatial;
// Mat dat: chon co / dat / cat theo ban do tron (splat), mep rang cua theo luoi diem anh.
uniform sampler2D grass_tex : source_color, filter_nearest, repeat_enable;
uniform sampler2D dirt_tex : source_color, filter_nearest, repeat_enable;
uniform sampler2D sand_tex : source_color, filter_nearest, repeat_enable;
uniform sampler2D splat : filter_linear;
uniform vec2 map_size = vec2(48.0, 36.0);
varying vec3 wpos;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }

void vertex() { wpos = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz; }

void fragment() {
	vec2 t = (floor(wpos.xz * 32.0) + 0.5) / 32.0;
	vec4 s = texture(splat, t / map_size);
	float w = 0.10 * sin(t.x * 3.1 + t.y * 1.3) + 0.07 * sin(t.y * 4.3 - t.x * 0.9) + (hash(floor(t * 16.0)) - 0.5) * 0.10;
	vec2 uv = wpos.xz / 2.0;
	vec3 col = texture(grass_tex, uv).rgb;
	float dr = s.r + w;
	float sa = s.g + w;
	if (sa > 0.5) {
		col = texture(sand_tex, uv).rgb;
		if (sa < 0.56) col *= 0.86;
	} else if (dr > 0.5) {
		col = texture(dirt_tex, uv).rgb;
		if (dr < 0.56) col *= 0.8;
	} else if (dr > 0.44 || sa > 0.44) {
		col *= 0.82;
	}
	col *= mix(0.55, 1.0, smoothstep(-1.3, -0.25, wpos.y));   // day nuoc toi dan
	ALBEDO = col;
	ROUGHNESS = 1.0;
	SPECULAR = 0.0;
}
