#!/usr/bin/env python3
"""Decrypt an Inside Invoice backup file"""
import sys
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

ENCRYPTION_KEY = "475cdadfe93e31872f0deddfa46d5b2cd7a04c2600e6c3d6663230f23be0f6b6"

def decrypt(encrypted_file, output_file):
    key = bytes.fromhex(ENCRYPTION_KEY)
    
    with open(encrypted_file, 'rb') as f:
        data = f.read()
    
    version = int.from_bytes(data[0:4], 'big')
    if version != 1:
        raise ValueError(f"Unsupported encryption version: {version}")
    
    iv = data[4:16]
    ciphertext = data[16:]
    
    aesgcm = AESGCM(key)
    plaintext = aesgcm.decrypt(iv, ciphertext, None)
    
    with open(output_file, 'wb') as f:
        f.write(plaintext)
    
    print(f"Decrypted: {output_file} ({len(plaintext)} bytes)")

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: python3 decrypt.py <encrypted_file> <output_file>")
        sys.exit(1)
    decrypt(sys.argv[1], sys.argv[2])
