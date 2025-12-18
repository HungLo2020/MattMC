<img src="common/src/main/resources/sodium-icon.png" width="128">

# Sodium (Integrated into MattMC)

Sodium is a powerful rendering engine and optimization mod integrated into MattMC which improves frame rates and reduces
micro-stutter, while fixing many graphical issues in Minecraft.

This component is based on the [Sodium project](https://github.com/CaffeineMC/sodium) by JellySquid and contributors,
integrated as a first-class component of MattMC rather than loaded as an external mod.

---

## ✅ Hardware Compatibility

Sodium requires graphics cards with up-to-date drivers compatible with OpenGL 4.5 or newer. Most graphics cards released 
in the past 12 years meet these requirements, including:

- AMD Radeon HD 7000 Series (GCN 1) or newer
- NVIDIA GeForce 400 Series (Fermi) or newer
- Intel HD Graphics 500 Series (Skylake) or newer

Nearly all graphics cards compatible with Minecraft (which requires OpenGL 3.3) should also work with Sodium. However,
older graphics cards may not work with future versions.

### OpenGL Compatibility Layers

Devices which need to use OpenGL translation layers (such as GL4ES, ANGLE, etc.) are not supported and will very likely
not work. These translation layers do not implement required functionality and suffer from underlying driver bugs which 
cannot be worked around.

## 🛠️ Building

This Sodium integration is built as part of the MattMC project. See the main project README for build instructions.

### Build Requirements

- OpenJDK 21
- Gradle 8.10.x (provided via wrapper script)

## 📜 License

Except where otherwise stated (see [third-party license notices](thirdparty/NOTICE.txt)), the Sodium content is provided
under the [Polyform Shield 1.0.0](LICENSE.md) license by [JellySquid](https://jellysquid.me).

This integration into MattMC maintains the original license terms.
