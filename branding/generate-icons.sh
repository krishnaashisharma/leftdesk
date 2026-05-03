#!/bin/bash
# Regenerates all LeftDesk platform icons from a source PNG.
# Usage: ./branding/generate-icons.sh [path-to-source.png]
set -e

SOURCE="${1:-flutter/assets/icon.png}"
ASSETS="flutter/assets"
cd "$(dirname "$0")/.."

echo "🎨 Generating LeftDesk icons from: $SOURCE"
echo ""

# --- Standalone sizes ---
echo "→ Standalone PNGs..."
mkdir -p "$ASSETS/icons"
for size in 16 32 48 64 128 256 512 1024; do
  sips -z $size $size "$SOURCE" --out "$ASSETS/icons/icon_${size}x${size}.png" > /dev/null
done
echo "  ✓ 8 sizes (16–1024px)"

# --- macOS .icns ---
echo "→ macOS .icns..."
mkdir -p "$ASSETS/AppIcon.iconset"
sips -z 16   16   "$SOURCE" --out "$ASSETS/AppIcon.iconset/icon_16x16.png"      > /dev/null
sips -z 32   32   "$SOURCE" --out "$ASSETS/AppIcon.iconset/icon_16x16@2x.png"   > /dev/null
sips -z 32   32   "$SOURCE" --out "$ASSETS/AppIcon.iconset/icon_32x32.png"      > /dev/null
sips -z 64   64   "$SOURCE" --out "$ASSETS/AppIcon.iconset/icon_32x32@2x.png"   > /dev/null
sips -z 128  128  "$SOURCE" --out "$ASSETS/AppIcon.iconset/icon_128x128.png"    > /dev/null
sips -z 256  256  "$SOURCE" --out "$ASSETS/AppIcon.iconset/icon_128x128@2x.png" > /dev/null
sips -z 256  256  "$SOURCE" --out "$ASSETS/AppIcon.iconset/icon_256x256.png"    > /dev/null
sips -z 512  512  "$SOURCE" --out "$ASSETS/AppIcon.iconset/icon_256x256@2x.png" > /dev/null
sips -z 512  512  "$SOURCE" --out "$ASSETS/AppIcon.iconset/icon_512x512.png"    > /dev/null
sips -z 1024 1024 "$SOURCE" --out "$ASSETS/AppIcon.iconset/icon_512x512@2x.png" > /dev/null
iconutil -c icns "$ASSETS/AppIcon.iconset" -o "$ASSETS/AppIcon.icns"
echo "  ✓ AppIcon.icns"

# --- Windows .ico ---
echo "→ Windows .ico..."
python3 -c "
from PIL import Image
import struct, io

def make_ico(paths):
    images = [Image.open(p).convert('RGBA') for p in paths]
    num = len(images)
    dir_size = 6 + 16 * num
    entries, image_data = [], []
    for img in images:
        buf = io.BytesIO()
        img.save(buf, format='PNG')
        data = buf.getvalue()
        entries.append((img.width, img.height, len(data)))
        image_data.append(data)
    out = io.BytesIO()
    out.write(struct.pack('<HHH', 0, 1, num))
    offset = dir_size
    for (w, h, size), data in zip(entries, image_data):
        out.write(struct.pack('<BBBBHHII', w if w < 256 else 0, h if h < 256 else 0, 0, 0, 1, 32, size, offset))
        offset += size
    for data in image_data: out.write(data)
    return out.getvalue()

sizes = [16, 32, 48, 64, 128, 256]
paths = ['flutter/assets/icons/icon_{s}x{s}.png'.format(s=s) for s in sizes]
ico_data = make_ico(paths)
with open('flutter/assets/app.ico', 'wb') as f:
    f.write(ico_data)
print('  ✓ app.ico ({:,} bytes)'.format(len(ico_data)))
"

# --- Android icons ---
echo "→ Android mipmap icons..."
ANDROID_RES="flutter/android/app/src/main/res"
declare -A SIZES=([mdpi]=48 [hdpi]=72 [xhdpi]=96 [xxhdpi]=144 [xxxhdpi]=192)
for density in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  size=${SIZES[$density]}
  mkdir -p "$ANDROID_RES/mipmap-$density"
  sips -z $size $size "$SOURCE" --out "$ANDROID_RES/mipmap-$density/ic_launcher.png"       > /dev/null
  sips -z $size $size "$SOURCE" --out "$ANDROID_RES/mipmap-$density/ic_launcher_round.png" > /dev/null
done
echo "  ✓ 5 densities × 2 variants (square + round)"

# --- Copy into Xcode asset catalogs if present ---
MACOS_ASSETS="flutter/macos/Runner/Assets.xcassets/AppIcon.appiconset"
if [ -d "$MACOS_ASSETS" ]; then
  cp "$ASSETS/AppIcon.iconset/"* "$MACOS_ASSETS/"
  echo "  ✓ macOS Xcode assets updated"
fi

IOS_ASSETS="flutter/ios/Runner/Assets.xcassets/AppIcon.appiconset"
if [ -d "$IOS_ASSETS" ]; then
  for spec in "20 1x" "40 2x" "60 3x" "29 1x" "58 2x" "87 3x" "40 1x" "80 2x" "120 3x" "120 2x" "180 3x" "1024 2x"; do
    sz=$(echo $spec | cut -d' ' -f1)
    scale=$(echo $spec | cut -d' ' -f2)
    sips -z $sz $sz "$SOURCE" --out "$IOS_ASSETS/Icon-App-${sz}x${sz}@${scale}.png" > /dev/null
  done
  echo "  ✓ iOS Xcode assets updated"
fi

echo ""
echo "✅ All icons generated successfully!"
