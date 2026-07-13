import os
from PIL import Image

def resize_logo():
    source_path = "/app/src/main/res/drawable/app_logo_z_1783902762916.jpg"
    if not os.path.exists(source_path):
        print(f"Error: Source image not found at {source_path}")
        return

    # Sizes map for launcher icons
    sizes = {
        "/app/src/main/res/mipmap-mdpi": 48,
        "/app/src/main/res/mipmap-hdpi": 72,
        "/app/src/main/res/mipmap-xhdpi": 96,
        "/app/src/main/res/mipmap-xxhdpi": 144,
        "/app/src/main/res/mipmap-xxxhdpi": 192
    }

    img = Image.open(source_path)

    for folder, size in sizes.items():
        os.makedirs(folder, exist_ok=True)
        # Resize using LANCZOS for high quality resampling
        resized_img = img.resize((size, size), Image.Resampling.LANCZOS)
        
        # Save as ic_launcher.png and ic_launcher_round.png
        png_path = os.path.join(folder, "ic_launcher.png")
        round_path = os.path.join(folder, "ic_launcher_round.png")
        
        resized_img.save(png_path, "PNG")
        resized_img.save(round_path, "PNG")
        print(f"Saved {size}x{size} to {png_path} and {round_path}")

if __name__ == "__main__":
    resize_logo()
