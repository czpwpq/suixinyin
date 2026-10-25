"""Independent decoder check. lzokay is a host-test dependency and is not shipped in the APK."""
from pathlib import Path
import struct
import sys


def verify(folder: Path, deps: Path):
    sys.path.insert(0, str(deps))
    try:
        import lzokay
    except ImportError as error:
        raise RuntimeError(
            'Install host-only lzokay==2.1.0 into work/reader-test-deps '
            'or set READER_TEST_DEPS before running these tests.') from error
    total = 0
    for name, header in [('lzo-fixtures.bin', '>HH'), ('lzo-compressed-fixtures.bin', '>II')]:
        blob = (folder / name).read_bytes()
        cursor = count = 0
        while cursor < len(blob):
            size, encoded = struct.unpack_from(header, blob, cursor)
            cursor += struct.calcsize(header)
            if not 0 < size <= 2000 or not 0 < encoded <= 4096 or cursor + encoded + size > len(blob):
                raise AssertionError('Damaged exported fixture record')
            packed, expected = blob[cursor:cursor + encoded], blob[cursor + encoded:cursor + encoded + size]
            cursor += encoded + size
            if lzokay.decompress(packed, size) != expected:
                raise AssertionError(f'LZO decode mismatch in {name}, source size {size}')
            count += 1
        assert cursor == len(blob)
        assert count == (2000 if name == 'lzo-fixtures.bin' else 2005), (name, count)
        total += count
        print(f'PASS independent lzokay decode: {name}, {count} records', flush=True)
    print(f'PASS {total} literal/repetition/random/mixed LZO records against independent native decoder', flush=True)


if __name__ == '__main__':
    verify(Path(sys.argv[1]), Path(sys.argv[2]))
