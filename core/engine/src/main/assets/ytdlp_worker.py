# Proceso persistente de yt-dlp para LibreDescarga.
#
# Importa yt-dlp UNA vez al arrancar y luego atiende peticiones, una por línea en stdin:
#   {"id": 1, "args": ["--dump-single-json", ..., "https://..."]}
# y responde una línea en stdout:
#   {"id": 1, "code": 0, "out": "<salida de yt-dlp>", "err": "<errores de yt-dlp>"}
#
# Cada petición ejecuta yt_dlp.main(args): exactamente lo mismo que lanzar "yt-dlp args" por línea de comandos,
# pero sin volver a arrancar Python ni a importar yt-dlp (~2,5 s en un teléfono de gama media).
# argv[1] = carpeta con el código de yt-dlp (allí Python guarda sus .pyc y no recompila en cada arranque).

import contextlib
import io
import json
import sys
import traceback

sys.path.insert(0, sys.argv[1])
real_stdout = sys.stdout

import yt_dlp  # noqa: E402

# Precarga de los extractores más usados: el primer análisis de cada sitio no paga su importación.
for module in ("youtube", "tiktok", "facebook", "instagram", "twitter", "generic"):
    try:
        __import__("yt_dlp.extractor." + module)
    except Exception:
        pass

real_stdout.write(json.dumps({"ready": True}) + "\n")
real_stdout.flush()

for line in sys.stdin:
    line = line.strip()
    if not line:
        continue
    request = json.loads(line)
    out, err, code = io.StringIO(), io.StringIO(), 0
    with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
        try:
            yt_dlp.main(request["args"])
        except SystemExit as exit_:
            code = exit_.code if isinstance(exit_.code, int) else (0 if exit_.code is None else 1)
        except BaseException:
            code = 1
            traceback.print_exc()
    real_stdout.write(json.dumps({"id": request["id"], "code": code, "out": out.getvalue(), "err": err.getvalue()}) + "\n")
    real_stdout.flush()
