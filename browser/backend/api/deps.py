from fastapi import Request
from rdflib import Dataset

def get_POEM(request: Request) -> Dataset:
    return request.app.state.POEM

