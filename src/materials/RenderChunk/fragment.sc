$input v_color0, v_color1, v_fog, v_refl, v_texcoord0, v_lightmapUV, v_extra, v_isTree, v_wPos, v_isCross, v_time

#include <bgfx_shader.sh>
#include <newb/main.sh>

SAMPLER2D_AUTOREG(s_MatTexture);
SAMPLER2D_AUTOREG(s_SeasonsTexture);
SAMPLER2D_AUTOREG(s_LightMapTexture);

uniform vec4 SunDirection;
uniform vec4 DimensionID;

float sideShadow(vec3 normal, float g) {
    float side = 1.0;
    float shadowStrength = 0.3;

    if (DimensionID.x < 0.5) {
        vec3 lightDir = SunDirection.xyz;
        float sunDot = dot(normal, lightDir);
        float intensity = smoothstep(0.0, 0.4, -sunDot);
        side = mix(1.0, shadowStrength, intensity);

        float Fade = clamp(SunDirection.y * 0.6 + 0.6, 0.0, 1.0);
        side = mix(1.0, side, Fade);
    } else {
        vec3 fixedDir = normalize(vec3(0.5, 0.5, 0.0));
        float fixedDot = dot(normal, fixedDir);
        float intensity = smoothstep(0.0, 0.4, -fixedDot);
        side = mix(1.0, shadowStrength, intensity);
    }
    return side;
}
  
void main() {
  #if defined(DEPTH_ONLY_OPAQUE) || defined(DEPTH_ONLY) || defined(INSTANCING)
    gl_FragColor = vec4(1.0,1.0,1.0,1.0);
    return;
  #endif

  vec4 diffuse = texture2D(s_MatTexture, v_texcoord0);
  
  vec2 offset = 1.0 / vec2(textureSize(s_MatTexture, 0));
  vec2 sampleUV = v_texcoord0 + offset * vec2(-0.15, -0.15);
  vec4 neighborTex = texture2D(s_MatTexture, sampleUV);
  if (neighborTex.a > 0.6) {
    vec3 neighbor = neighborTex.rgb;
    vec3 contrast = diffuse.rgb - neighbor;
    float dist = length(v_wPos);
    float fade = clamp(1.0 - dist / 16.0, 0.0, 1.0);
    diffuse.rgb += contrast * 1.0 * fade;
  }
      
  vec4 color = v_color0;

  #ifdef ALPHA_TEST
    if ((v_isTree > 0.5 && gl_FrontFacing) || (diffuse.a < 0.6)) {
      discard;
    }
  #endif

  vec3 glow = nlGlow(s_MatTexture, v_texcoord0, v_extra.a);

  diffuse.rgb *= diffuse.rgb;

  #if defined(TRANSPARENT) && !(defined(SEASONS) || defined(RENDER_AS_BILLBOARDS))
    if (v_extra.b > 0.9) {
      vec3 blend = vec3_splat(1.0 - NL_WATER_TEX_OPACITY*(1.0 - diffuse.b*1.8));
      diffuse.rgb = blend*v_color1.rgb;
      diffuse.a = color.a;
    }
  #else
    diffuse.a = 1.0;
  #endif

  diffuse.rgb *= color.rgb;

  #ifndef ALPHA_TEST
    if (v_extra.b <= 0.9 && v_isTree > 0.5) {
        vec3 normal = normalize(cross(dFdx(v_wPos), dFdy(v_wPos)));
        float ss = sideShadow(normal, v_color1.g);
        diffuse.rgb *= ss;
    }
  #endif

  diffuse.rgb += glow;

  if (v_extra.b > 0.9) {
    diffuse.rgb += v_refl.rgb*v_refl.a;
  } else if (v_refl.a > 0.0) {
    // reflective effect - only on xz plane
    float dy = abs(dFdy(v_extra.g));
    if (dy < 0.0002) {
      float mask = v_refl.a*(clamp(v_extra.r*10.0,8.2,8.8)-7.8);
      diffuse.rgb *= 1.0 - 0.6*mask;
      diffuse.rgb += v_refl.rgb*mask;
    }
  }
  
  diffuse.rgb = mix(diffuse.rgb, v_fog.rgb, v_fog.a);
  
  diffuse.rgb = colorCorrection(diffuse.rgb);

  gl_FragColor = diffuse;
}