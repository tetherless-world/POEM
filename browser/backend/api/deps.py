from fastapi import Request
from rdflib import Dataset

def get_POEM(request: Request) -> Dataset:
    return request.app.state.POEM
def get_buckets(request: Request):
    return request.app.state.buckets
