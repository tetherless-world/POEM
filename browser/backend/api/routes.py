from unicodedata import name

from fastapi import APIRouter, Request, Depends
from rdflib import Graph
from rdflib import Dataset
from api.deps import get_POEM
from poem.query import get_instrument, get_instruments_by_scales, get_items, getTotalInstruments, getTotalLanguages, getInstrumentItemConcepts, getScales, getAllInstruments, getAllScales, get_components
router = APIRouter()

@router.get("/api/debug/graphs")
def debug_graphs(request: Request):
    ds = request.app.state.POEM
    return [str(ctx.identifier) for ctx in ds.contexts()]
@router.get("/rcads")
def rcads(POEM: Dataset = Depends(get_POEM) ):
    count = getTotalInstruments(POEM,"RCADS")
    languages = getTotalLanguages(POEM, "RCADS")
    itemConcepts= getInstrumentItemConcepts(POEM, "RCADS-47-Y-EN RCADS-47-CG-EN")
    scales = getScales(POEM, "RCADS-47-CG-EN")
    return {"count": count, "languages": languages, "itemConcepts": itemConcepts, "scales": scales}
@router.get("/gad")
def gad(POEM: Dataset = Depends(get_POEM)):
    count = getTotalInstruments(POEM,"GAD")
    languages = getTotalLanguages(POEM, "GAD")
    itemConcepts = getInstrumentItemConcepts(POEM, "GAD-7 GAD-7")
    return {"count": count, "languages":languages, "itemConcepts": itemConcepts}
@router.get("/mtt")
def mtt(POEM: Dataset = Depends(get_POEM)):
    count = getTotalInstruments(POEM, "MTT")
    languages = getTotalLanguages(POEM, "MTT")
    itemConcepts = getInstrumentItemConcepts(POEM, "MTT-35-Y-EN-1 MTT-35-CG-EN-1")
    scales = getScales(POEM, "MTT-35-CG-EN-1")
    return {"count": count, "languages":languages, "itemConcepts": itemConcepts, "scales": scales}
@router.get("/phq")
def mtt(POEM: Dataset = Depends(get_POEM)):
    count = getTotalInstruments(POEM, "PHQ")
    languages = getTotalLanguages(POEM, "PHQ")
    itemConcepts = getInstrumentItemConcepts(POEM, "PHQ-9-A-EN PHQ-9-A-EN")
    scales = getScales(POEM, "PHQ-9-A-EN")
    return {"count": count, "languages":languages, "itemConcepts": itemConcepts, "scales": scales}

@router.get("/instruments/{name}")
def get_instruments(POEM: Dataset = Depends(get_POEM), name: str = "RCADS"):
    instruments = getAllInstruments(POEM, name)
    return {"instruments": instruments}
@router.get("/scales")
def get_scales(POEM: Dataset = Depends(get_POEM)):
    scales = getAllScales(POEM)
    return {"scales": scales}

@router.get("/instrument/individual/{instrument}")
def get_instrument_details(POEM: Dataset = Depends(get_POEM), instrument: str = "RCADS-47-Y-EN RCADS-47-CG-EN"):
    items = get_items(POEM, instrument)
    components = get_components(POEM, instrument)
    instrumentR = get_instrument(POEM, instrument)
    return {  "items": items, "components": components, "instrument": instrumentR}
@router.get("/all_instruments_by_scale")
def get_instruments_by_scale(POEM: Dataset = Depends(get_POEM)):
    scales = getAllScales(POEM)
    instruments_by_scale= get_instruments_by_scales(POEM, scales)
    result = {"scales":instruments_by_scale}
    return result