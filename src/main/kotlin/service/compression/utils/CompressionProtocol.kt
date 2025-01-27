package main.service.compression.utils

import java.io.InputStream
import java.io.OutputStream

interface CompressionProtocol {
    fun compress(input: InputStream, output: OutputStream)
    fun decompress(input: InputStream, output: OutputStream)
}