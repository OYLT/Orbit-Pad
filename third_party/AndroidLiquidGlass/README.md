# AndroidLiquidGlass source adaptation

Upstream: https://github.com/Kyant0/AndroidLiquidGlass
Pinned revision: `65ab177e90e5c1d8c62e70cf7755841982da65f6` (branch `kmp`).
License: Apache-2.0; full license and attribution are retained here and in APK assets.

Orbit Pad uses the upstream AGSL sources directly through its native Java View renderer.
It does not depend on the Compose artifact. The Kotlin/Compose catalog is a reference
for layer composition and material/interaction parameters, rather than a compiled dependency.

| Upstream source | Local use |
| --- | --- |
| `backdrop/src/commonMain/kotlin/com/kyant/backdrop/internal/Shaders.kt` | `assets/liquidglass/refraction.agsl`, `refraction_dispersion.agsl`, `highlight.agsl` |
| `backdrop/.../effects/Lens.kt` | `KyantGlass.Lens`: size, offset, capsule radii, signed amount, depth and dispersion uniforms |
| `backdrop/.../effects/ColorFilter.kt` | Saturation 1.5 before Gaussian blur |
| `app/.../components/LiquidBottomTabs.kt` | Frosted panel, combined backdrop + tab-content selection lens, dark tint, blue accent and interactive highlight |
| `app/.../utils/DampedDragAnimation.kt` | Design reference for press expansion, independent drag velocity and rebound |

The shader text is extracted from the Kotlin triple-quoted constants, with the shared
`RoundedRectSDF` expanded. Refraction variants add inverse shear coordinates and
transform the SDF normal to match Orbit Pad's velocity deformation. Square-root and
normalization guards avoid edge precision singularities. The highlight shader is
unchanged. Orbit Pad retains its existing gesture ownership/cancellation, bounded
panel movement, saved settings, accessibility semantics and page transitions.

The native adapter lives in `KyantGlass.java`, with composition in `GlassDock.java`
and page-source capture in `GlassBackdrop.java`. Only the page and transparent tab
content are sources; no node records its own output. No screenshot bitmap is used
in the rendering loop.

The APK includes `licenses/AndroidLiquidGlass-LICENSE.txt` and `-NOTICE.txt`.
The complete notice and license can be read under Settings > About Orbit Pad.
