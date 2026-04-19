from unicodedata import name

from fastapi import APIRouter, Request, Depends
from rdflib import Graph
from rdflib import Dataset
from api.deps import get_POEM, get_buckets
from pydantic import BaseModel
import inspect

from poem.query import (
    get_instrument,
    get_instruments_by_scales,
    get_items,
    get_total_instruments,
    get_total_languages,
    get_instrument_item_concepts,
    get_scales,
    get_all_instruments_from_family,
    get_all_scales,
    get_components,
    ai_summary,
    fetch_html,
    extract_text,
    search_query_small,
    search_query
)

router = APIRouter()

class Request(BaseModel):
    url: str
    content: str
@router.get("/api/debug/graphs")
def debug_graphs(request: Request):
    ds = request.app.state.POEM
    return [str(ctx.identifier) for ctx in ds.contexts()]


@router.get("/rcads")
def rcads(POEM: Dataset = Depends(get_POEM)):
    count = get_total_instruments(POEM, "RCADS")
    languages = get_total_languages(POEM, "RCADS")
    itemConcepts = get_instrument_item_concepts(POEM, "RCADS-47-Y-EN RCADS-47-CG-EN")
    scales = get_scales(POEM, "RCADS-47-CG-EN")
    return {
        "count": count,
        "languages": languages,
        "itemConcepts": itemConcepts,
        "scales": scales,
    }


@router.get("/gad")
def gad(POEM: Dataset = Depends(get_POEM)):
    count = get_total_instruments(POEM, "GAD")
    languages = get_total_languages(POEM, "GAD")
    itemConcepts = get_instrument_item_concepts(POEM, "GAD-7 GAD-7")
    return {"count": count, "languages": languages, "itemConcepts": itemConcepts}


@router.get("/mtt")
def mtt(POEM: Dataset = Depends(get_POEM)):
    count = get_total_instruments(POEM, "MTT")
    languages = get_total_languages(POEM, "MTT")
    itemConcepts = get_instrument_item_concepts(POEM, "MTT-35-Y-EN-1 MTT-35-CG-EN-1")
    scales = get_scales(POEM, "MTT-35-CG-EN-1")
    return {
        "count": count,
        "languages": languages,
        "itemConcepts": itemConcepts,
        "scales": scales,
    }


@router.get("/phq")
def phq(POEM: Dataset = Depends(get_POEM)):
    count = get_total_instruments(POEM, "PHQ")
    languages = get_total_languages(POEM, "PHQ")
    itemConcepts = get_instrument_item_concepts(POEM, "PHQ-9-A-EN PHQ-9-A-EN")

    print(inspect.getsourcefile(get_scales))
    print(inspect.signature(get_scales))   
    scales = get_scales(POEM, "PHQ-9-A-EN")
    return {
        "count": count,
        "languages": languages,
        "itemConcepts": itemConcepts,
        "scales": scales,
    }


@router.get("/instruments/{name}")
def get_instruments(POEM: Dataset = Depends(get_POEM), name: str = "RCADS"):
    instruments = get_all_instruments_from_family(POEM, name)
    return {"instruments": instruments}


@router.get("/scales")
def get_scales_route(POEM: Dataset = Depends(get_POEM)):
    scales = get_all_scales(POEM)
    return {"scales": scales}


@router.get("/instrument/individual/{instrument}")
def get_instrument_details(
    POEM: Dataset = Depends(get_POEM), instrument: str = "RCADS-47-Y-EN RCADS-47-CG-EN"
):
    items = get_items(POEM, instrument)
    components = get_components(POEM, instrument)
    instrumentR = get_instrument(POEM, instrument)
    return {"items": items, "components": components, "instrument": instrumentR}


@router.get("/all_instruments_by_scale")
def get_instruments_by_scale(POEM: Dataset = Depends(get_POEM)):
    scales = get_all_scales(POEM)
    instruments_by_scale = get_instruments_by_scales(POEM, scales)
    result = {"scales": instruments_by_scale}
    return result
@router.post("/ai_summary")
async def get_ai_summary(req: Request):
    text: str = extract_text(req.content)
    summary = ai_summary(text)
    print(summary)
    return {"summary": summary}

@router.post("/search_small/{query}")
async def search_small(query, buckets = Depends(get_buckets)):
    results = search_query_small(query, buckets)
    return results
@router.post("/search/{query}")
async def search(query, buckets = Depends(get_buckets)):
    results = search_query(query, buckets)
    return results