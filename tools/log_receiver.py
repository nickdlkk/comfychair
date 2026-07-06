#!/usr/bin/env python3
import argparse
import cgi
import json
import os
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


class LogUploadHandler(BaseHTTPRequestHandler):
    upload_dir = Path("uploads")

    def _json(self, code: int, payload: dict):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path in ("/", "/health"):
            self._json(200, {"ok": True, "message": "log receiver running"})
        else:
            self._json(404, {"ok": False, "error": "not found"})

    def do_POST(self):
        if self.path != "/upload":
            self._json(404, {"ok": False, "error": "not found"})
            return

        ctype, pdict = cgi.parse_header(self.headers.get("Content-Type", ""))
        if ctype != "multipart/form-data":
            self._json(400, {"ok": False, "error": "expected multipart/form-data"})
            return

        form = cgi.FieldStorage(  # type: ignore[arg-type]
            fp=self.rfile,
            headers=self.headers,
            environ={
                "REQUEST_METHOD": "POST",
                "CONTENT_TYPE": self.headers.get("Content-Type", ""),
            },
        )

        file_item = form["file"] if "file" in form else None
        if file_item is None or not getattr(file_item, "file", None):
            self._json(400, {"ok": False, "error": "missing file field"})
            return

        original_name = os.path.basename(file_item.filename or "upload.zip")
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        save_name = f"{timestamp}_{original_name}"
        save_path = self.upload_dir / save_name
        self.upload_dir.mkdir(parents=True, exist_ok=True)

        with save_path.open("wb") as f:
            f.write(file_item.file.read())

        metadata = {
            "saved_at": datetime.now().isoformat(),
            "client": self.client_address[0],
            "original_name": original_name,
            "saved_name": save_name,
            "size": save_path.stat().st_size,
            "app": form.getvalue("app"),
            "filename_field": form.getvalue("filename"),
        }
        meta_path = save_path.with_suffix(save_path.suffix + ".json")
        meta_path.write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8")

        self._json(200, {"ok": True, "saved": str(save_path), "size": metadata["size"]})


def main():
    parser = argparse.ArgumentParser(description="Receive ComfyChair log bundles")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=9123)
    parser.add_argument("--upload-dir", default="./uploads")
    args = parser.parse_args()

    LogUploadHandler.upload_dir = Path(args.upload_dir).resolve()
    server = ThreadingHTTPServer((args.host, args.port), LogUploadHandler)
    print(f"Listening on http://{args.host}:{args.port}/upload")
    print(f"Saving uploads to {LogUploadHandler.upload_dir}")
    server.serve_forever()


if __name__ == "__main__":
    main()
