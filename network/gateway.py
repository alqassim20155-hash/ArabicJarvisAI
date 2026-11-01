import ssl
import hmac
import os
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

TOKEN = Path(__file__).with_name("token.txt").read_text().strip()
UPSTREAM = "http://127.0.0.1:8765"
MAX_BODY = 65536

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path != "/health":
            return self.respond(404, b"Not found")
        if not self.authorized():
            return self.respond(401, b"Unauthorized")
        self.proxy(b"", "GET")

    def do_POST(self):
        if self.path != "/v1/chat/completions":
            return self.respond(404, b"Not found")
        if not self.authorized():
            return self.respond(401, b"Unauthorized")
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            return self.respond(400, b"Bad length")
        if not 0 < length <= MAX_BODY:
            return self.respond(413, b"Invalid body size")
        self.proxy(self.rfile.read(length), "POST")

    def authorized(self):
        return hmac.compare_digest(
            self.headers.get("Authorization", ""),
            "Bearer " + TOKEN
        )

    def respond(self, status, body, content_type="text/plain"):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def proxy(self, body, method):
        try:
            req = urllib.request.Request(
                UPSTREAM + self.path,
                data=body if method == "POST" else None,
                method=method,
                headers={"Content-Type": "application/json"}
            )
            with urllib.request.urlopen(req, timeout=120) as response:
                data = response.read(1048576 + 1)
                if len(data) > 1048576:
                    return self.respond(502, b"Response too large")
                self.respond(
                    response.status,
                    data,
                    response.headers.get("Content-Type", "application/json")
                )
        except Exception:
            self.respond(502, b"Upstream unavailable")

    def log_message(self, format, *args):
        pass

server = ThreadingHTTPServer(("192.168.1.228", 18765), Handler)
print("Jarvis gateway listening on 192.168.1.228:18765", flush=True)

cert_dir = Path(__file__).with_name("certs")

context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
context.minimum_version = ssl.TLSVersion.TLSv1_2
context.load_cert_chain(
    certfile=str(cert_dir / "lan.crt"),
    keyfile=str(cert_dir / "lan.key")
)

server.socket = context.wrap_socket(
    server.socket,
    server_side=True
)

print("HTTPS enabled on 192.168.1.228:18765", flush=True)
server.serve_forever()
