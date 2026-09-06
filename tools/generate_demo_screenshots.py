"""Generate OCR-friendly fake screenshot PNGs for the RecallOS debug demo."""

from pathlib import Path
import struct
import zlib

WIDTH, HEIGHT = 900, 1200
SCALE = 3
GLYPHS = {
    "A": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    "B": ["11110", "10001", "10001", "11110", "10001", "10001", "11110"],
    "C": ["01111", "10000", "10000", "10000", "10000", "10000", "01111"],
    "D": ["11110", "10001", "10001", "10001", "10001", "10001", "11110"],
    "E": ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    "F": ["11111", "10000", "10000", "11110", "10000", "10000", "10000"],
    "G": ["01111", "10000", "10000", "10111", "10001", "10001", "01111"],
    "H": ["10001", "10001", "10001", "11111", "10001", "10001", "10001"],
    "I": ["11111", "00100", "00100", "00100", "00100", "00100", "11111"],
    "J": ["00111", "00010", "00010", "00010", "00010", "10010", "01100"],
    "K": ["10001", "10010", "10100", "11000", "10100", "10010", "10001"],
    "L": ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
    "M": ["10001", "11011", "10101", "10101", "10001", "10001", "10001"],
    "N": ["10001", "11001", "10101", "10011", "10001", "10001", "10001"],
    "O": ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    "P": ["11110", "10001", "10001", "11110", "10000", "10000", "10000"],
    "Q": ["01110", "10001", "10001", "10001", "10101", "10010", "01101"],
    "R": ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
    "S": ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
    "T": ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
    "U": ["10001", "10001", "10001", "10001", "10001", "10001", "01110"],
    "V": ["10001", "10001", "10001", "10001", "10001", "01010", "00100"],
    "W": ["10001", "10001", "10001", "10101", "10101", "11011", "10001"],
    "X": ["10001", "10001", "01010", "00100", "01010", "10001", "10001"],
    "Y": ["10001", "10001", "01010", "00100", "00100", "00100", "00100"],
    "Z": ["11111", "00001", "00010", "00100", "01000", "10000", "11111"],
    "0": ["01110", "10001", "10011", "10101", "11001", "10001", "01110"],
    "1": ["00100", "01100", "00100", "00100", "00100", "00100", "01110"],
    "2": ["01110", "10001", "00001", "00010", "00100", "01000", "11111"],
    "3": ["11110", "00001", "00001", "01110", "00001", "00001", "11110"],
    "4": ["00010", "00110", "01010", "10010", "11111", "00010", "00010"],
    "5": ["11111", "10000", "10000", "11110", "00001", "00001", "11110"],
    "6": ["01110", "10000", "10000", "11110", "10001", "10001", "01110"],
    "7": ["11111", "00001", "00010", "00100", "01000", "01000", "01000"],
    "8": ["01110", "10001", "10001", "01110", "10001", "10001", "01110"],
    "9": ["01110", "10001", "10001", "01111", "00001", "00001", "01110"],
    ":": ["00000", "00100", "00100", "00000", "00100", "00100", "00000"],
    ".": ["00000", "00000", "00000", "00000", "00000", "00110", "00110"],
    ",": ["00000", "00000", "00000", "00000", "00110", "00100", "01000"],
    "-": ["00000", "00000", "00000", "11111", "00000", "00000", "00000"],
    "/": ["00001", "00010", "00010", "00100", "01000", "01000", "10000"],
    "$": ["00100", "01111", "10100", "01110", "00101", "11110", "00100"],
    "#": ["01010", "11111", "01010", "01010", "11111", "01010", "00000"],
    "+": ["00000", "00100", "00100", "11111", "00100", "00100", "00000"],
    "%": ["11001", "11010", "00010", "00100", "01000", "01011", "10011"],
    "(": ["00010", "00100", "01000", "01000", "01000", "00100", "00010"],
    ")": ["01000", "00100", "00010", "00010", "00010", "00100", "01000"],
}

SAMPLES = {
    "01_tech_stack": ["HACKATHON TECH STACK", "KOTLIN + COMPOSE", "ROOM DATABASE", "ML KIT OCR", "MEDIAPIPE EMBEDDINGS", "GEMMA LOCAL LLM"],
    "02_receipt_cafe": ["MORNING BREW RECEIPT", "LATTE             $4.50", "AVOCADO TOAST     $8.25", "BLUEBERRY MUFFIN   $3.75", "TOTAL            $16.50", "THANK YOU"],
    "03_receipt_market": ["FRESH MARKET", "BANANAS           $2.49", "COFFEE            $9.99", "PASTA             $3.49", "TAX               $1.28", "TOTAL            $17.25"],
    "04_address": ["DELIVERY ADDRESS", "ALEX MORGAN", "42 RIVER STREET", "APT 7B", "BROOKLYN NY 11211", "DELIVER AFTER 5 PM"],
    "05_meeting": ["PRODUCT MEETING NOTES", "DATE: SEPTEMBER 5 2026", "SHIP OCR PIPELINE", "TEST SHARE TARGET", "DESIGN SEARCH RESULTS", "OWNER: PRIYA"],
    "06_recipe": ["TOMATO PASTA RECIPE", "BOIL PASTA FOR 9 MINUTES", "SAUTE GARLIC AND TOMATO", "ADD BASIL AND OLIVE OIL", "MIX WITH PARMESAN", "SERVES 2"],
    "07_travel": ["TRAVEL ITINERARY", "FLIGHT: AA 204", "NEW YORK TO AUSTIN", "DEPARTS 08:40", "GATE B12", "SEAT 14A"],
    "08_tasks": ["WEEKLY TASKS", "BUY GROCERIES", "REVIEW DESIGN DOC", "CALL THE BANK", "BACK UP PHOTOS", "BOOK DENTIST APPOINTMENT"],
    "09_invoice": ["INVOICE 1042", "NORTHSTAR DESIGN", "MOBILE APP AUDIT", "HOURS: 12", "RATE: $125", "AMOUNT DUE: $1500"],
    "10_wifi": ["OFFICE WIFI", "NETWORK: STUDIO GUEST", "PASSWORD: BLUE COFFEE 27", "FLOOR 3", "CONTACT IT SUPPORT"],
    "11_book": ["BOOK NOTES", "THE LEFT HAND OF DARKNESS", "AUTHOR: URSULA LE GUIN", "THEME: TRUST AND CHANGE", "READ CHAPTERS 1 TO 4", "DISCUSS FRIDAY"],
    "12_health": ["APPOINTMENT REMINDER", "DENTAL CLEANING", "THURSDAY OCTOBER 12", "2:30 PM", "DR PATEL", "BRING INSURANCE CARD"],
    "13_event": ["CONFERENCE SCHEDULE", "ANDROID DEV SUMMIT", "KEYNOTE 09:00", "WORKSHOP: ON DEVICE AI", "ROOM 204", "LUNCH 12:30"],
    "14_budget": ["MONTHLY BUDGET", "RENT             $1800", "TRANSPORTATION     $120", "GROCERIES          $350", "SAVINGS            $500", "REMAINING          $230"],
    "15_webpage": ["LOCAL FIRST AI", "RUN MODELS WITHOUT CLOUD", "PRIVATE BY DEFAULT", "FAST SEARCH ON DEVICE", "OCR PLUS EMBEDDINGS", "READ MORE AT EXAMPLE DOT COM"],
    "16_package": ["PACKAGE TRACKING", "TRACKING: ZX 381 904", "EXPECTED FRIDAY", "RECIPIENT: SAM LEE", "LOCKER 18", "PICKUP CODE 4721"],
}


def png_chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)


def render(lines, output):
    pixels = bytearray([255, 255, 255] * WIDTH * HEIGHT)

    def pixel(x, y, color):
        if 0 <= x < WIDTH and 0 <= y < HEIGHT:
            offset = (y * WIDTH + x) * 3
            pixels[offset:offset + 3] = bytes(color)

    def text(x, y, value, color=(24, 34, 48)):
        for char in value:
            glyph = GLYPHS.get(char, GLYPHS.get(char.upper(), ["00000"] * 7))
            for row, pattern in enumerate(glyph):
                for col, filled in enumerate(pattern):
                    if filled == "1":
                        for dy in range(SCALE):
                            for dx in range(SCALE):
                                pixel(x + col * SCALE + dx, y + row * SCALE + dy, color)
            x += 6 * SCALE

    text(54, 48, "RECALL OS / DEMO CAPTURE", (0, 109, 119))
    y = 130
    for line in lines:
        text(54, y, line.upper())
        y += 52
    raw_rows = b"".join(b"\x00" + bytes(pixels[row * WIDTH * 3:(row + 1) * WIDTH * 3]) for row in range(HEIGHT))
    png = b"\x89PNG\r\n\x1a\n"
    png += png_chunk(b"IHDR", struct.pack(">IIBBBBB", WIDTH, HEIGHT, 8, 2, 0, 0, 0))
    png += png_chunk(b"IDAT", zlib.compress(raw_rows, 9))
    png += png_chunk(b"IEND", b"")
    output.write_bytes(png)


if __name__ == "__main__":
    destination = Path(__file__).parents[1] / "app" / "src" / "main" / "assets" / "demo_samples"
    destination.mkdir(parents=True, exist_ok=True)
    for name, lines in SAMPLES.items():
        render(lines, destination / f"{name}.png")
    print(f"Generated {len(SAMPLES)} demo screenshots in {destination}")