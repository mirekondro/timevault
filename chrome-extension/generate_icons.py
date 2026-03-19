#!/usr/bin/env python3
"""
Simple icon generator for TimeVault Chrome extension
Creates PNG icons with gradient background and brain emoji
"""

import base64
from io import BytesIO

# Simple PNG file creation without PIL
def create_simple_png_icon(size, filename):
    """Create a simple colored square PNG icon"""

    # Create a simple PNG header and data for a colored square
    # This is a minimal PNG implementation
    width = height = size

    # PNG signature
    png_signature = b'\x89PNG\r\n\x1a\n'

    # IHDR chunk
    ihdr_data = (width.to_bytes(4, 'big') +
                height.to_bytes(4, 'big') +
                b'\x08\x02\x00\x00\x00')  # 8-bit RGB, no compression, no filter, no interlace

    # Calculate CRC for IHDR
    import zlib
    ihdr_crc = zlib.crc32(b'IHDR' + ihdr_data) & 0xffffffff
    ihdr_chunk = (len(ihdr_data).to_bytes(4, 'big') +
                  b'IHDR' +
                  ihdr_data +
                  ihdr_crc.to_bytes(4, 'big'))

    # Create image data (purple gradient-ish color)
    purple_color = [102, 126, 234]  # RGB for purple
    image_data = bytearray()

    for y in range(height):
        image_data.append(0)  # Filter byte for each row
        for x in range(width):
            # Simple gradient effect
            brightness = max(0.7, 1.0 - (x + y) / (width + height))
            r = int(purple_color[0] * brightness)
            g = int(purple_color[1] * brightness)
            b = int(purple_color[2] * brightness)
            image_data.extend([r, g, b])

    # Compress the image data
    compressed_data = zlib.compress(bytes(image_data))

    # IDAT chunk
    idat_crc = zlib.crc32(b'IDAT' + compressed_data) & 0xffffffff
    idat_chunk = (len(compressed_data).to_bytes(4, 'big') +
                  b'IDAT' +
                  compressed_data +
                  idat_crc.to_bytes(4, 'big'))

    # IEND chunk
    iend_crc = zlib.crc32(b'IEND') & 0xffffffff
    iend_chunk = b'\x00\x00\x00\x00IEND' + iend_crc.to_bytes(4, 'big')

    # Combine all chunks
    png_data = png_signature + ihdr_chunk + idat_chunk + iend_chunk

    # Write to file
    with open(filename, 'wb') as f:
        f.write(png_data)

    print(f"Created {filename} ({size}x{size})")

if __name__ == "__main__":
    import os

    # Create icons directory if it doesn't exist
    os.makedirs('icons', exist_ok=True)

    # Create all required icon sizes
    sizes = [16, 32, 48, 128]

    for size in sizes:
        try:
            create_simple_png_icon(size, f'icons/icon-{size}.png')
        except Exception as e:
            print(f"Error creating icon-{size}.png: {e}")

    print("Icon generation complete!")
