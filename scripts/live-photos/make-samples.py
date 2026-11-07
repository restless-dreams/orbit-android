"""Makes the small live photos the app's tests read, in app/src/test/resources/live/.

Each one is built the way a phone writes it, from the formats' own descriptions:
a still and a 1.5-second video made here, put together as Google's Motion
Photo (JPEG and HEIC), older Pixels' MicroVideo, Samsung's trailer, Huawei's
and Honor's, an iPhone's pair (HEIC still and QuickTime movie, sharing an id),
an older iPhone's movie (no ftyp, its sound described as lpcm), the iPhone's
movie with its sound described in QuickTime's second version, four frames to a
packet (counted a packet at a time, and a frame at a time), and an iPhone
still that lost its id on the way. Run it from the repository root, with
ffmpeg on the path:

    uv run --with pillow --with pillow-heif python scripts/live-photos/make-samples.py
"""

import io
import pathlib
import struct
import subprocess
import tempfile

import pillow_heif
from PIL import Image, ImageDraw

OUT = pathlib.Path("app/src/test/resources/live")
IDENTIFIER = "C0633EC8-48F1-43C9-84DC-23C480CA1FA4"


def still(color, size=(320, 240)):
    image = Image.new("RGB", size, color)
    draw = ImageDraw.Draw(image)
    draw.ellipse((size[0] // 4, size[1] // 4, size[0] * 3 // 4, size[1] * 3 // 4), fill=(255, 255, 255))
    return image


def jpeg(image, exif=None):
    out = io.BytesIO()
    image.save(out, "JPEG", quality=80, **({"exif": exif} if exif else {}))
    return out.getvalue()


def with_segment(picture, marker, payload):
    """A JPEG with a segment put right after its start-of-image marker."""
    segment = bytes([0xFF, marker]) + struct.pack(">H", len(payload) + 2) + payload
    return picture[:2] + segment + picture[2:]


def xmp_packet(body):
    return (
        '<?xpacket begin="﻿" id="W5M0MpCehiHzreSzNTczkc9d"?>'
        '<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">'
        + body
        + '</rdf:RDF></x:xmpmeta><?xpacket end="w"?>'
    ).encode("utf-8")


def motion_photo_xmp(primary_mime, video_length, padding, timestamp):
    return xmp_packet(
        '<rdf:Description rdf:about="" xmlns:Camera="http://ns.google.com/photos/1.0/camera/"'
        ' xmlns:Container="http://ns.google.com/photos/1.0/container/"'
        ' xmlns:Item="http://ns.google.com/photos/1.0/container/item/"'
        f' Camera:MotionPhoto="1" Camera:MotionPhotoVersion="1" Camera:MotionPhotoPresentationTimestampUs="{timestamp}">'
        "<Container:Directory><rdf:Seq>"
        f'<rdf:li rdf:parseType="Resource"><Container:Item Item:Mime="{primary_mime}" Item:Semantic="Primary" Item:Length="0" Item:Padding="{padding}"/></rdf:li>'
        f'<rdf:li rdf:parseType="Resource"><Container:Item Item:Mime="video/mp4" Item:Semantic="MotionPhoto" Item:Length="{video_length}"/></rdf:li>'
        "</rdf:Seq></Container:Directory></rdf:Description>"
    )


def micro_video_xmp(offset, timestamp):
    # Older Pixels wrote their own prefix for the same namespace: properties are matched by namespace, not prefix.
    return xmp_packet(
        '<rdf:Description rdf:about="" xmlns:GCamera="http://ns.google.com/photos/1.0/camera/">'
        "<GCamera:MicroVideo>1</GCamera:MicroVideo><GCamera:MicroVideoVersion>1</GCamera:MicroVideoVersion>"
        f"<GCamera:MicroVideoOffset>{offset}</GCamera:MicroVideoOffset>"
        f"<GCamera:MicroVideoPresentationTimestampUs>{timestamp}</GCamera:MicroVideoPresentationTimestampUs>"
        "</rdf:Description>"
    )


def xmp_segment(packet):
    return b"http://ns.adobe.com/xap/1.0/\x00" + packet


def box(kind, payload):
    return struct.pack(">I", len(payload) + 8) + kind + payload


def samsung_trailer(video):
    """Samsung's trailer: data blocks, then their directory (SEFH), its length and SEFT."""
    def block(kind, name, data):
        return struct.pack("<HHI", 0, kind, len(name)) + name + data

    blocks = [
        (0x0A01, block(0x0A01, b"Image_UTC_Data", b"1668877476000")),
        (0x0A30, block(0x0A30, b"MotionPhoto_Data", video)),
    ]
    data = b"".join(item for _, item in blocks)
    entries = b""
    position = 0

    for kind, item in blocks:
        # Each entry says how far back from the directory its block starts, and how long it is.
        entries += struct.pack("<HHII", 0, kind, len(data) - position, len(item))
        position += len(item)

    directory = b"SEFH" + struct.pack("<II", 107, len(blocks)) + entries
    return data + directory + struct.pack("<I", len(directory)) + b"SEFT"


def huawei_trailer(start_from_end):
    """Huawei's: their frame table, then three space-padded fields, the last LIVE_ and the video's distance from the end, less 40."""
    def field(text):
        return text.encode("ascii").ljust(20, b" ")

    frames = b"\x00iFrame\x00" + bytes(120)
    tail = field("v3_f47_c") + field("967:600") + field(f"LIVE_{start_from_end - 40}")
    return frames, tail


def apple_exif(identifier, taken):
    """EXIF with when it was taken and Apple's maker note holding the pair's id (tag 0x11), all big-endian."""
    note_ifd_at = 14
    value = identifier.encode("ascii") + b"\x00"
    note = b"Apple iOS\x00" + b"\x00\x01" + b"MM"
    # One entry: ContentIdentifier, ASCII, its text after the directory; places count from the note's start.
    note += struct.pack(">H", 1) + struct.pack(">HHII", 0x0011, 2, len(value), note_ifd_at + 2 + 12 + 4) + struct.pack(">I", 0) + value

    date = taken.encode("ascii") + b"\x00"
    ifd0_at = 8
    exif_ifd_at = ifd0_at + 2 + 12 + 4
    date_at = exif_ifd_at + 2 + 12 * 2 + 4
    note_at = date_at + len(date)
    tiff = b"MM\x00\x2a" + struct.pack(">I", ifd0_at)
    tiff += struct.pack(">H", 1) + struct.pack(">HHII", 0x8769, 4, 1, exif_ifd_at) + struct.pack(">I", 0)
    tiff += struct.pack(">H", 2)
    tiff += struct.pack(">HHII", 0x9003, 2, len(date), date_at)
    tiff += struct.pack(">HHII", 0x927C, 7, len(note), note_at)
    tiff += struct.pack(">I", 0)
    tiff += date + note
    return b"Exif\x00\x00" + tiff


def written_exif(taken):
    """EXIF as an iPhone's JPEG copy keeps it: only when the copy was written, in the first directory."""
    date = taken.encode("ascii") + b"\x00"
    tiff = b"MM\x00\x2a" + struct.pack(">I", 8)
    tiff += struct.pack(">H", 1) + struct.pack(">HHII", 0x0132, 2, len(date), 8 + 2 + 12 + 4) + struct.pack(">I", 0) + date
    return b"Exif\x00\x00" + tiff


def boxes(data):
    """The boxes one after another in `data`, each as its type and what's inside it."""
    found, position = [], 0

    while position + 8 <= len(data):
        size, kind = struct.unpack(">I4s", data[position:position + 8])
        found.append((kind, data[position + 8:position + size]))
        position += size

    return found


def older_iphone_movie(movie):
    """An older iPhone's movie, from one ffmpeg wrote: it starts without an ftyp, and its
    sound is described as lpcm, in QuickTime's third version, with flags for its samples."""
    top = boxes(movie)
    shift = sum(8 + len(body) for kind, body in top if kind == b"ftyp")

    def rewrite(kind, body):
        if kind in (b"moov", b"trak", b"mdia", b"minf", b"stbl"):
            return box(kind, b"".join(rewrite(*part) for part in boxes(body)))

        if kind == b"stco":
            count = struct.unpack(">I", body[4:8])[0]
            offsets = struct.unpack(f">{count}I", body[8:8 + 4 * count])
            return box(kind, body[:8] + struct.pack(f">{count}I", *(offset - shift for offset in offsets)))

        if kind == b"stsd" and body[12:16] == b"sowt":
            channels, bits = struct.unpack(">HH", body[32:36])
            rate = struct.unpack(">I", body[40:44])[0] >> 16
            lpcm = b"\x00" * 6 + struct.pack(">H", 1) + struct.pack(">HHI", 2, 0, 0) + struct.pack(">HHhHI", 3, 16, -2, 0, 65536)
            # Its size, rate, channels, a constant, bits, flags (signed and packed, little-endian), bytes and frames a packet.
            lpcm += struct.pack(">Id", 72, float(rate)) + struct.pack(">IIIIII", channels, 0x7F000000, bits, 0xC, channels * bits // 8, 1)
            return box(kind, body[:8] + box(b"lpcm", lpcm))

        return box(kind, body)

    return b"".join(rewrite(kind, body) for kind, body in top if kind != b"ftyp")


def second_version_movie(movie, packets):
    """An iPhone's movie, from one ffmpeg wrote, with its sound described in QuickTime's second
    version, four frames to a packet: its samples counted a packet at a time, each lasting four
    ticks, or, as QuickTime long counted uncompressed sound, a frame at a time."""
    frames = 4

    def sound(trak):
        media = next(body for kind, body in boxes(trak) if kind == b"mdia")
        handler = next(body for kind, body in boxes(media) if kind == b"hdlr")
        return handler[8:12] == b"soun"

    def counted(kind, body):
        if kind == b"stsd":
            size, name = struct.unpack(">I4s", body[8:16])
            fields = body[16:8 + size]
            channels = struct.unpack(">H", fields[16:18])[0]
            packet = frames * channels * 2
            # The version, then after the first version's fields: frames to a packet, a packet's bytes (for a channel, and in all), a sample's.
            second = fields[:8] + struct.pack(">H", 1) + fields[10:28] + struct.pack(">IIII", frames, packet // channels, packet, 2) + fields[28:]
            return box(kind, body[:8] + box(name, second) + body[8 + size:])

        if not packets:
            return box(kind, body)

        if kind == b"stts":
            count = struct.unpack(">I", body[4:8])[0]
            runs = [struct.unpack(">II", body[8 + 8 * index:16 + 8 * index]) for index in range(count)]
            assert all(ticks == 1 and samples % frames == 0 for samples, ticks in runs)
            return box(kind, body[:8] + b"".join(struct.pack(">II", samples // frames, frames) for samples, _ in runs))

        if kind == b"stsz":
            size, count = struct.unpack(">II", body[4:12])
            assert size > 0 and count % frames == 0
            return box(kind, body[:4] + struct.pack(">II", size * frames, count // frames))

        if kind == b"stsc":
            count = struct.unpack(">I", body[4:8])[0]
            runs = [struct.unpack(">III", body[8 + 12 * index:20 + 12 * index]) for index in range(count)]
            assert all(samples % frames == 0 for _, samples, _ in runs)
            return box(kind, body[:8] + b"".join(struct.pack(">III", first, samples // frames, description) for first, samples, description in runs))

        return box(kind, body)

    def rewrite(kind, body, in_sound=False):
        if kind == b"trak":
            return box(kind, b"".join(rewrite(*part, in_sound=sound(body)) for part in boxes(body)))

        if kind in (b"moov", b"mdia", b"minf", b"stbl"):
            return box(kind, b"".join(rewrite(*part, in_sound=in_sound) for part in boxes(body)))

        return counted(kind, body) if in_sound else box(kind, body)

    # The movie comes after the media data, so where the chunks start stays as it was.
    top = boxes(movie)
    assert [kind for kind, _ in top].index(b"mdat") < [kind for kind, _ in top].index(b"moov")
    return b"".join(rewrite(kind, body) for kind, body in top)


def heic(image, xmp=None, exif=None):
    heif = pillow_heif.from_pillow(image)
    out = io.BytesIO()
    heif.save(out, quality=60, **({"xmp": xmp} if xmp else {}), **({"exif": exif} if exif else {}))
    return out.getvalue()


def ffmpeg(*args):
    subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", *args], check=True)


def main():
    OUT.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory() as scratch:
        mp4 = pathlib.Path(scratch, "motion.mp4")
        mov = pathlib.Path(scratch, "live.mov")
        source = ["-f", "lavfi", "-i", "testsrc2=size=160x120:rate=10:duration=1.5", "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=8000:duration=1.5"]
        ffmpeg(*source, "-c:v", "libx264", "-profile:v", "baseline", "-pix_fmt", "yuv420p", "-crf", "35", "-g", "15", "-c:a", "aac", "-b:a", "16k", "-ac", "1",
               "-movflags", "+faststart", "-map_metadata", "-1", str(mp4))
        # An iPhone's: HEVC and uncompressed sound in QuickTime, with the pair's id, when it was made and a live photo's key.
        ffmpeg(*source, "-c:v", "libx265", "-tag:v", "hvc1", "-x265-params", "log-level=error", "-crf", "35", "-pix_fmt", "yuv420p", "-c:a", "pcm_s16le", "-ac", "1",
               "-movflags", "use_metadata_tags", "-metadata", f"com.apple.quicktime.content.identifier={IDENTIFIER}",
               "-metadata", "com.apple.quicktime.creationdate=2022-11-19T11:14:36-0600", "-metadata", "com.apple.quicktime.live-photo.vitality-score=1.000000",
               "-f", "mov", str(mov))
        video = mp4.read_bytes()
        (OUT / "motion.mp4").write_bytes(video)

        picture = jpeg(still((40, 90, 200)))
        (OUT / "plain.jpg").write_bytes(picture)

        google = with_segment(picture, 0xE1, xmp_segment(motion_photo_xmp("image/jpeg", len(video), 0, 733333)))
        (OUT / "google-motion-photo.jpg").write_bytes(google + video)

        micro = with_segment(picture, 0xE1, xmp_segment(micro_video_xmp(len(video), 1100000)))
        (OUT / "micro-video.jpg").write_bytes(micro + video)

        (OUT / "samsung-motion-photo.jpg").write_bytes(picture + samsung_trailer(video))

        gain_map = jpeg(still((128, 128, 128), (80, 60)))
        frames, tail = huawei_trailer(len(video) + 128 + 60)
        (OUT / "huawei-moving-picture.jpg").write_bytes(picture + gain_map + bytes(2048) + video + frames + tail)

        (OUT / "appended-video.jpg").write_bytes(picture + video)

        motion_heic = heic(still((200, 60, 60)), xmp=motion_photo_xmp("image/heic", len(video), 8, 500000))
        (OUT / "google-motion-photo.heic").write_bytes(motion_heic + box(b"mpvd", video))

        (OUT / "iphone-live.heic").write_bytes(heic(still((60, 160, 90)), exif=apple_exif(IDENTIFIER, "2022:11:19 11:14:36")))
        (OUT / "iphone-live.mov").write_bytes(mov.read_bytes())
        (OUT / "iphone-live-older.mov").write_bytes(older_iphone_movie(mov.read_bytes()))
        (OUT / "iphone-live-packets.mov").write_bytes(second_version_movie(mov.read_bytes(), packets=True))
        (OUT / "iphone-live-frames.mov").write_bytes(second_version_movie(mov.read_bytes(), packets=False))
        (OUT / "iphone-still.jpg").write_bytes(jpeg(still((60, 160, 90)), exif=written_exif("2022:11:19 11:14:37")))


if __name__ == "__main__":
    main()
