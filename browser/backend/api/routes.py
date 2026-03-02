from fastapi import APIRouter, Request, Depends
from rdflib import Graph
from rdflib import Dataset
from api.deps import get_POEM
from poem.query import getTotalInstruments, getTotalLanguages, getInstrumentItemConcepts
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
    return {"count": count, "languages": languages, "itemConcepts": itemConcepts}
@router.get("/gad7")
def gad(POEM: Dataset = Depends(get_POEM)):
    count = getTotalInstruments(POEM,"GAD")
    languages = getTotalLanguages(POEM, "GAD")
    itemConcepts = getInstrumentItemConcepts(POEM, "GAD-7 GAD-7")
    return {"count": count, "languages":languages, "itemConcepts": itemConcepts}