"""
Generate PDFSuite launcher icon:
- Circular red gradient background
- White document with folded corner
- 'PDF' text in bold
Generates all mipmap densities.
"""
import sys, os, math
sys.path.insert(0, '')

try:
    from PIL import Image, ImageDraw, ImageFont
    PIL_AVAILABLE = True
except ImportError:
    PIL_AVAILABLE = False

if not PIL_AVAILABLE:
    import subprocess
    subprocess.run([sys.executable, '-m', 'pip', 'install', 'Pillow', '--quiet'])
    from PIL import Image, ImageDraw, ImageFont

def draw_icon(size):
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    d   = ImageDraw.Draw(img)

    s = size
    pad = int(s * 0.06)

    # Background circle — red gradient approximated with solid + lighter overlay
    # Draw filled circle
    r1 = (211, 47, 47, 255)   # #D32F2F
    r2 = (183, 28, 28, 255)   # #B71C1C
    d.ellipse([pad, pad, s-pad, s-pad], fill=r1)

    # Slightly lighter top half
    overlay = Image.new('RGBA', (s, s), (0,0,0,0))
    od = ImageDraw.Draw(overlay)
    od.ellipse([pad, pad, s-pad, s//2+pad], fill=(255,255,255,20))
    img = Image.alpha_composite(img, overlay)
    d = ImageDraw.Draw(img)

    # Document rectangle (white)
    doc_l = int(s * 0.30)
    doc_t = int(s * 0.20)
    doc_r = int(s * 0.76)
    doc_b = int(s * 0.84)
    corner_size = int(s * 0.18)

    # Draw document body (white, rounded slightly)
    doc_poly = [
        (doc_l, doc_t),
        (doc_r - corner_size, doc_t),
        (doc_r, doc_t + corner_size),
        (doc_r, doc_b),
        (doc_l, doc_b),
    ]
    d.polygon(doc_poly, fill=(255, 255, 255, 245))

    # Folded corner triangle (light gray/shadow)
    fold_poly = [
        (doc_r - corner_size, doc_t),
        (doc_r, doc_t + corner_size),
        (doc_r - corner_size, doc_t + corner_size),
    ]
    d.polygon(fold_poly, fill=(220, 220, 220, 230))

    # "PDF" text centered on document
    try:
        font_size = max(8, int(s * 0.22))
        fnt = ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf", font_size)
    except:
        fnt = ImageFont.load_default()

    text = "PDF"
    bbox = d.textbbox((0,0), text, font=fnt)
    tw = bbox[2] - bbox[0]
    th = bbox[3] - bbox[1]
    cx = (doc_l + doc_r) / 2
    cy = (doc_t + doc_b) / 2 - int(s * 0.03)
    tx = cx - tw / 2
    ty = cy - th / 2
    d.text((tx, ty), text, fill=r2, font=fnt)

    # Horizontal lines below text (document lines)
    line_y = int(cy + th * 0.8)
    line_l = doc_l + int(s * 0.07)
    line_r = doc_r - int(s * 0.10)
    lw = max(1, int(s * 0.025))
    for i in range(3):
        y = line_y + i * int(s * 0.065)
        d.rectangle([line_l, y, line_r, y+lw], fill=(180,180,180,200))

    return img

# Densities: folder_name -> size
densities = {
    'mipmap-mdpi':    48,
    'mipmap-hdpi':    72,
    'mipmap-xhdpi':   96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi':192,
}

base = r"C:\Users\usuario\Desktop\SER\PDFReader\app\src\main\res"
for folder, px in densities.items():
    out_dir = os.path.join(base, folder)
    os.makedirs(out_dir, exist_ok=True)
    img = draw_icon(px)
    # ic_launcher (square with rounded corners)
    img.save(os.path.join(out_dir, 'ic_launcher.png'))
    # ic_launcher_round (full circle)
    img.save(os.path.join(out_dir, 'ic_launcher_round.png'))
    print(f"  {folder}: {px}x{px}")

print("Icons generated!")
