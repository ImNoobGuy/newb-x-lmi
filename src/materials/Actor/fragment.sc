$input v_color0, v_fog, v_light, v_texcoord0, v_wpos, v_edgemap

#include <bgfx_shader.sh>
#include <MinecraftRenderer.Materials/ActorUtil.dragonh>
#include <newb/main.sh>

uniform vec4 ColorBased;
uniform vec4 ChangeColor;
uniform vec4 UseAlphaRewrite;
uniform vec4 TintedAlphaTestEnabled;
uniform vec4 MatColor;
uniform vec4 OverlayColor;
uniform vec4 MultiplicativeTintColor;
uniform vec4 ActorFPEpsilon;
uniform vec4 HudOpacity;
uniform vec4 DimensionID;

SAMPLER2D_AUTOREG(s_MatTexture);
SAMPLER2D_AUTOREG(s_MatTexture1);

void main() {
  #if defined(DEPTH_ONLY) || defined(INSTANCING)
    gl_FragColor = vec4(0.0, 0.0, 0.0, 0.0);
    return;
  #elif defined(DEPTH_ONLY_OPAQUE)
    gl_FragColor = vec4(mix(vec3_splat(1.0), v_fog.rgb, v_fog.a), 1.0);
    return;
  #endif

  vec4 albedo = getActorAlbedoNoColorChange(v_texcoord0, s_MatTexture, s_MatTexture1, MatColor);
  
  /*vec2 offset = 1.0 / vec2(textureSize(s_MatTexture, 0));
  vec2 sampleUV = v_texcoord0 + offset * vec2(-0.15, -0.15);
  vec4 neighborTex = texture2D(s_MatTexture, sampleUV);
  if (neighborTex.a > 0.6) {
    vec3 neighbor = neighborTex.rgb;
    vec3 contrast = albedo.rgb - neighbor;
    albedo.rgb += contrast * 0.2;
  }*/

  #ifdef ALPHA_TEST
    float alpha = mix(albedo.a, (albedo.a * OverlayColor.a), TintedAlphaTestEnabled.x);
    if (shouldDiscard(albedo.rgb, alpha, ActorFPEpsilon.x)) {
      discard;
    }
  #endif

  #ifdef CHANGE_COLOR_MULTI
    albedo = applyMultiColorChange(albedo, ChangeColor.rgb, MultiplicativeTintColor.rgb);
  #elif defined(CHANGE_COLOR)
    albedo = applyColorChange(albedo, ChangeColor, albedo.a);
    albedo.a *= ChangeColor.a;
  #endif

  #ifdef ALPHA_TEST
    albedo.a = max(UseAlphaRewrite.r, albedo.a);
  #endif

  albedo.rgb *= mix(vec3(1.0, 1.0, 1.0), v_color0.rgb, ColorBased.x);

  albedo = applyOverlayColor(albedo, OverlayColor);

  albedo *= albedo;

  vec4 light = v_light;
  #if defined(EMISSIVE) || defined(EMISSIVE_ONLY)
    light.rgb = max(light.rgb, 2.0*NL_GLOW_TEX*(1.0-albedo.a)); // glow effect
  #endif

  albedo = applyLighting(albedo, light);
  
  float torchIntensity = luminance(v_light.rgb);
  torchIntensity = clamp(torchIntensity, 0.0, 1.0);
  vec3 torchColor;
  if (DimensionID.x < 0.5) {
    torchColor = NL_OVERWORLD_TORCH_COL;
  } else if (DimensionID.x < 1.5) {
    torchColor = NL_NETHER_TORCH_COL;
  } else {
    torchColor = NL_END_TORCH_COL;
  }
  if (torchIntensity > 0.01) {
    albedo.rgb += torchColor * torchIntensity * 1.0;
  }
  
  vec3 normal = normalize(cross(dFdx(v_wpos), dFdy(v_wpos)));
  float shades = mix(1.0, 0.5, abs(normal.x));
  
  vec3 glow = nlGlow(s_MatTexture, v_texcoord0, 1.0);
  albedo.rgb += glow;

  #ifdef TRANSPARENT
    albedo = applyHudOpacity(albedo, HudOpacity.x);
  #endif

  #ifdef NL_ENTITY_EDGE_HIGHLIGHT
    albedo.rgb *= nlEntityEdgeHighlight(v_edgemap);
  #endif
  
  #ifdef TESTI
    vec4 pattern = texture2D(s_MatTexture1, v_texcoord0);
    float mask = pattern.r;
    float intensity = 5.0;
    albedo.rgb += albedo.rgb * mask * intensity;
  #endif
  
  albedo.rgb *= shades;

  albedo.rgb = mix(albedo.rgb, v_fog.rgb, v_fog.a);

  albedo.rgb = colorCorrection(albedo.rgb);

  gl_FragColor = albedo;
}
