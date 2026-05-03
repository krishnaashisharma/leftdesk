# LeftDesk Icon Assets

## Source File
- `flutter/assets/icon.png` — master 1024×1024 PNG (source of truth)
- `flutter/assets/icon-dark.png` — dark mode variant (copy of master, customise as needed)
- `flutter/assets/leftdesk-logo.jpg` — original uploaded JPEG

## Generated Files
| File | Description |
|------|-------------|
| `flutter/assets/AppIcon.icns` | macOS app icon (623 KB, all sizes) |
| `flutter/assets/app.ico` | Windows app icon (63 KB, 16/32/48/64/128/256 px) |
| `flutter/assets/icons/icon_NxN.png` | Standalone PNGs: 16,32,48,64,128,256,512,1024 |
| `flutter/assets/AppIcon.iconset/` | macOS iconset source (10 files for iconutil) |
| `flutter/android/app/src/main/res/mipmap-*/` | Android launcher icons (mdpi→xxxhdpi, square + round) |
| `flutter/flutter_launcher_icons.yaml` | flutter_launcher_icons config |

## Regenerating Icons
```bash
cd ~/leftdesk && bash branding/generate-icons.sh
```
Or with a custom source:
```bash
bash branding/generate-icons.sh path/to/new-icon.png
```
