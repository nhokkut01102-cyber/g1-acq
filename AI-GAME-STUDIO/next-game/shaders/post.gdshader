shader_type canvas_item;
// Hau ky: giu diem anh sac o vung giua, lam mo tren/duoi (tilt-shift), am mau, toi goc.
uniform float focus = 0.56;
uniform float strength = 1.7;

void fragment() {
	vec2 ps = TEXTURE_PIXEL_SIZE;
	vec2 snap = (floor(UV / ps) + 0.5) * ps;
	float b = smoothstep(0.27, 0.56, abs(UV.y - focus)) * strength;
	vec3 c = texture(TEXTURE, snap).rgb;
	if (b > 0.01) {
		vec2 o = ps * b;
		vec3 acc = c * 0.16;
		acc += (texture(TEXTURE, UV + vec2(o.x, 0.0)).rgb + texture(TEXTURE, UV - vec2(o.x, 0.0)).rgb
			+ texture(TEXTURE, UV + vec2(0.0, o.y)).rgb + texture(TEXTURE, UV - vec2(0.0, o.y)).rgb) * 0.13;
		acc += (texture(TEXTURE, UV + o * 0.7).rgb + texture(TEXTURE, UV - o * 0.7).rgb
			+ texture(TEXTURE, UV + vec2(o.x, -o.y) * 0.7).rgb + texture(TEXTURE, UV + vec2(-o.x, o.y) * 0.7).rgb) * 0.08;
		c = mix(c, acc, min(b, 1.0));
	}
	c *= vec3(1.04, 1.0, 0.93);
	float lum = dot(c, vec3(0.299, 0.587, 0.114));
	c = mix(vec3(lum), c, 1.0);
	vec2 v = UV - 0.5;
	c *= 1.0 - dot(v, v) * 0.55;
	COLOR = vec4(c, 1.0);
}
