"""Create tiny synthetic import fixtures; contains no user media."""
import pathlib, struct, zlib
root=pathlib.Path(__file__).resolve().parents[1]/'.tools'/'art-fixtures'
root.mkdir(parents=True,exist_ok=True)
def chunk(name,data):return struct.pack('>I',len(data))+name+data+struct.pack('>I',zlib.crc32(name+data)&0xffffffff)
rows=b''.join(b'\0'+bytes(c for x in range(64) for c in (40+x*2,110+y,90,255)) for y in range(64))
(root/'Kongye_test_art.png').write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',64,64,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(rows))+chunk(b'IEND',b''))
gif=bytearray(b'GIF89a'+struct.pack('<HHBBB',32,32,0x80,0,0)+bytes([40,122,89,86,122,171])+b'\x21\xff\x0bNETSCAPE2.0\x03\x01\x00\x00\x00')
for color in (0,1):
    codes=[code for _ in range(1024) for code in (4,color)]+[5]
    encoded=bytearray();acc=bits=0
    for code in codes:
        acc|=code<<bits;bits+=3
        while bits>=8:encoded.append(acc&255);acc>>=8;bits-=8
    if bits:encoded.append(acc&255)
    gif+=b'\x21\xf9\x04\x00'+struct.pack('<H',30)+b'\0\0'+b','+struct.pack('<HHHHB',0,0,32,32,0)+b'\x02'
    for start in range(0,len(encoded),255):block=encoded[start:start+255];gif.append(len(block));gif+=block
    gif+=b'\0'
gif+=b';';(root/'Kongye_test_art.gif').write_bytes(gif)
print('Synthetic PNG and two-frame GIF fixtures ready.')
