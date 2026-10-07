package top.ghhccghk.yadea.network

actual fun createTcpTransport(): TcpTransport = throw UnsupportedOperationException("iOS TCP not implemented")
actual fun createUdpTransport(): UdpTransport = throw UnsupportedOperationException("iOS UDP not implemented")