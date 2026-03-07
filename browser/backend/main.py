from fastapi import FastAPI, APIRouter, Request
from fastapi.middleware.cors import CORSMiddleware
from poem.loader import load_dataset
from pathlib import Path
from contextlib import asynccontextmanager
from rdflib import URIRef
from typing import Any
from rdflib.namespace import RDFS
from api.routes import router
DATA_DIR: Path = Path(__file__).parent / "data"

@asynccontextmanager
async def lifespan(app: FastAPI):
    POEM  = load_dataset(DATA_DIR)
    app.state.POEM = POEM

    yield

app: FastAPI = FastAPI(lifespan = lifespan)
app.include_router(router)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/api/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
4