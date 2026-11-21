#!/usr/bin/env python3
"""Strip MethodParameters attributes from .class files inside a jar.
d8 (R8 8.2.2-dev in build-tools 34.0.0) NPEs on MethodParameters entries
whose name index is 0 (null name), e.g. synthetic this$0 params in
androidx/appcompat classes. MethodParameters is debug-only metadata;
removing it is safe for dexing and runtime.
Usage: strip_mp.py in.jar out.jar  (also works on a single .class file)
"""
import struct, sys, zipfile, os

def strip_class(data: bytes) -> bytes:
    if data[:4] != b'\xca\xfe\xba\xbe':
        return data
    p = 8
    cp_count = struct.unpack('>H', data[p:p+2])[0]; p += 2
    cp_utf8 = {}  # index -> string
    i = 1
    while i < cp_count:
        tag = data[p]; p += 1
        if tag == 1:  # Utf8
            ln = struct.unpack('>H', data[p:p+2])[0]; p += 2
            cp_utf8[i] = data[p:p+ln].decode('utf-8', 'replace'); p += ln
        elif tag in (7, 8, 16, 19, 20):
            p += 2
        elif tag in (15,):
            p += 3
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            p += 4
        elif tag in (5, 6):
            p += 8; i += 1
        else:
            raise ValueError(f"bad cp tag {tag}")
        i += 1
    # access_flags(2) this(2) super(2) interfaces_count(2)+2n
    p += 6
    icount = struct.unpack('>H', data[p:p+2])[0]; p += 2 + 2*icount
    # fields
    fcount = struct.unpack('>H', data[p:p+2])[0]; p += 2
    for _ in range(fcount):
        p += 6
        acount = struct.unpack('>H', data[p:p+2])[0]; p += 2
        for _ in range(acount):
            p += 2
            alen = struct.unpack('>I', data[p:p+4])[0]; p += 4 + alen
    # methods: need to rebuild with MethodParameters removed
    mcount_pos = p
    mcount = struct.unpack('>H', data[p:p+2])[0]; p += 2
    out = bytearray(data[:p])
    removed = 0
    for _ in range(mcount):
        mstart = p
        p += 6  # access, name, descriptor
        acount = struct.unpack('>H', data[p:p+2])[0]; p += 2
        attrs = []
        for _ in range(acount):
            aname_idx = struct.unpack('>H', data[p:p+2])[0]
            alen = struct.unpack('>I', data[p+2:p+6])[0]
            attrs.append((aname_idx, data[p:p+6+alen]))
            p += 6 + alen
        kept = [(idx, raw) for idx, raw in attrs if cp_utf8.get(idx) != "MethodParameters"]
        removed += len(attrs) - len(kept)
        out += data[mstart:mstart+6]
        out += struct.pack('>H', len(kept))
        for _, raw in kept:
            out += raw
    out += data[p:]
    return bytes(out) if removed else data

def process_jar(src, dst):
    zin = zipfile.ZipFile(src)
    zout = zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED)
    n = 0
    for item in zin.infolist():
        data = zin.read(item.filename)
        if item.filename.endswith(".class"):
            new = strip_class(data)
            if new != data:
                n += 1
            data = new
        zout.writestr(item, data)
    zout.close()
    return n

if __name__ == "__main__":
    src, dst = sys.argv[1], sys.argv[2]
    if src.endswith(".class"):
        open(dst, "wb").write(strip_class(open(src, "rb").read()))
        print("stripped single class")
    else:
        print(f"stripped {process_jar(src, dst)} classes in {src}")
