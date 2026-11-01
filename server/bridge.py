"""Authenticated, narrow bridge to the installed upstream OpenJarvis CLI."""
import hmac
import os
import subprocess
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

app = FastAPI(title="Arabic Jarvis bridge", docs_url=None, redoc_url=None)
TOKEN = os.environ.get("JARVIS_BRIDGE_TOKEN", "")
CLI = os.environ.get("JARVIS_BIN", "jarvis")

class Ask(BaseModel):
    message: str = Field(min_length=1, max_length=3000)

@app.get('/health')
def health():
    return {"status": "up", "engine": "OpenJarvis CLI adapter"}

@app.post('/v1/ask')
def ask(body: Ask, authorization: str | None = Header(default=None)):
    if not TOKEN or not authorization or not hmac.compare_digest(authorization, 'Bearer ' + TOKEN):
        raise HTTPException(status_code=401, detail="Unauthorized")
    # Explicit argument array, never shell=True; fixed CLI executable from server config.
    try:
        result = subprocess.run([CLI, 'ask', body.message], capture_output=True, text=True,
                                timeout=120, check=False, stdin=subprocess.DEVNULL)
    except subprocess.TimeoutExpired:
        raise HTTPException(status_code=504, detail="OpenJarvis timeout")
    except OSError:
        raise HTTPException(status_code=503, detail="OpenJarvis executable unavailable")
    if result.returncode:
        raise HTTPException(status_code=502, detail="OpenJarvis command failed; inspect server logs")
    return {"reply": result.stdout.strip()[:20000], "source": "openjarvis"}
