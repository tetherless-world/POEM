import os
from dotenv import load_dotenv
from rdflib import Dataset, URIRef, Literal
import httpx
from bs4 import BeautifulSoup
from poem.generate_search import generate_txt, place_into_buckets, search
import requests
import json
from rapidfuzz import process, fuzz

load_dotenv()


OPEN_ROUTER_API_KEY = os.getenv("OPEN_ROUTER_KEY")
instrumentsCollections: str = "urn:poem:file:instrumentCollections.ttl"
instruments: str = "urn:poem:file:instruments.ttl"
languages: str = "urn:poem:file:languages.ttl"
items: str = "urn:poem:file:items.ttl"
informants: str = "urn:poem:file:informants.ttl"
itemStems:  str = "urn:poem:file:itemStems.ttl"
itemStemConcepts: str = "urn:poem:file:itemStemConcepts.ttl"
scale_instruments: str = "urn:poem:file:scalesInstrument.ttl"
scales: str = "urn:poem:file:scales.ttl"
components: str = "urn:poem:file:components.ttl"
def get_total_instruments(POEM: Dataset, name: str):
    name = Literal(name).n3()
    query = f"""
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX sio: <http://semanticscience.org/resource/> 
    SELECT (COUNT(?o) AS ?count)
    WHERE {{
            GRAPH <{instrumentsCollections}> {{?s rdfs:label {name} ;
             sio:SIO_000059 ?o.
            }}
        }}
    """
    res = POEM.query(query)
    for row in res:
        return int(row['count'])
    return 0
def get_total_languages(POEM: Dataset, name: str):
    name = Literal(name).n3()
    query = f"""
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX sio: <http://semanticscience.org/resource/> 
    SELECT (COUNT(DISTINCT ?l) AS ?count) ?i
    WHERE {{
            GRAPH <{instrumentsCollections}> {{?s rdfs:label {name} ;
             sio:SIO_000059 ?i.
            }}
            GRAPH <{instruments}> {{ ?i sio:SIO_000008 ?l .}}
            GRAPH <{languages}> {{ ?l rdfs:label ?o .}}
        }}
    """
    res = POEM.query(query)
    for row in res:
        return int(row['count'])
    return 0
def get_instrument_item_concepts(POEM: Dataset, name: str):
    group = name.split()
    youth = Literal(group[0]).n3()
    caregiver = Literal(group[1]).n3()
    query1= f"""
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX sio: <http://semanticscience.org/resource/> 
    SELECT ?r
    WHERE {{
        GRAPH <{instruments}> {{?s rdfs:label {youth} ;
          sio:SIO_000059 ?i .}}
        GRAPH <{items}> {{?i sio:SIO_000253 ?c.}}
        GRAPH <{itemStems}>{{?c rdfs:label ?r}}
    }}
    """
    query2 = f"""
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX sio: <http://semanticscience.org/resource/> 
    SELECT ?r
    WHERE {{
        GRAPH <{instruments}> {{?s rdfs:label {caregiver} ;
          sio:SIO_000059 ?i .}}
        GRAPH <{items}> {{?i sio:SIO_000253 ?c.}}
        GRAPH <{itemStems}>{{?c rdfs:label ?r}}
    }}
    """
    return  {"youth": list([row.r for row in POEM.query(query1)]), "caregiver": list([row.r for row in POEM.query(query2)])}

def get_scales(POEM: Dataset, name: str):
    name = Literal(name).n3()
    scales_query = f"""
    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX sio: <http://semanticscience.org/resource/>
    SELECT DISTINCT ?s
    WHERE{{
        GRAPH <{instruments}> {{?su rdfs:label {name}}}
        GRAPH <{scale_instruments}> {{ ?su ?p ?o .}}
        GRAPH <{scales}> {{?o rdfs:label ?s .}}
    }}
    """ 
    return list([row.s for row in POEM.query(scales_query)])
def get_all_instruments_from_family(POEM: Dataset, name: str):
    name = Literal(name.upper()).n3()
    query = f"""
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX sio: <http://semanticscience.org/resource/>
PREFIX owl: <http://www.w3.org/2002/07/owl#>
SELECT DISTINCT ?label ?lang ?i
WHERE {{
    GRAPH <{instrumentsCollections}> {{?s rdfs:label {name} }}
    GRAPH <{instrumentsCollections}> {{?s sio:SIO_000059 ?o.}}
    GRAPH <{instruments}> {{?o rdfs:label ?label .}}
    GRAPH <{instruments}> {{?o sio:SIO_000008 ?l .}}
    GRAPH <{instruments}> {{?o sio:SIO_000008 ?in .}}
    GRAPH <{languages}> {{?l rdfs:label ?lang .}}
    GRAPH <{informants}> {{?in rdfs:label ?i .}}
    GRAPH <{instruments}> {{?o owl:deprecated 0.}}
    }}
"""
    return list([{"name": row.label, "lang": row.lang, "informant": row.i, "count": int(row.label.split('-')[1])} for row in POEM.query(query)])
def get_all_scales(POEM: Dataset):
    query = f"""
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX sio: <http://semanticscience.org/resource/>
SELECT DISTINCT ?label
WHERE {{
    GRAPH <{scales}> {{?s rdfs:label ?label .}}
    }}
"""
    return list([row.label for row in POEM.query(query)])
def get_items(POEM: Dataset, name: str):
    name = Literal(name).n3()
    query = f"""    PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
    PREFIX sio: <http://semanticscience.org/resource/>
    SELECT DISTINCT ?label
    WHERE {{
        GRAPH <{instruments}> {{?su rdfs:label {name}}}
        GRAPH <{instruments}> {{ ?su sio:SIO_000059 ?o .}}
        GRAPH <{items}> {{ ?o sio:SIO_000253 ?c .}}
        GRAPH <{itemStems}> {{?c rdfs:label ?label .}}
    }}
    """
    return list([row.label for row in POEM.query(query)])
def get_instrument(POEM: Dataset, name: str):
    name = Literal(name).n3()
    query = f"""PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX sio: <http://semanticscience.org/resource/>
SELECT DISTINCT ?language ?informant
WHERE {{    
    GRAPH <{instruments}> {{?su rdfs:label {name}}}
    GRAPH <{instruments}> {{ ?su sio:SIO_000008 ?l .}}
    GRAPH <{instruments}> {{ ?su sio:SIO_000008 ?in .}}
    GRAPH <{languages}> {{?l rdfs:label ?language .}}
    GRAPH <{informants}> {{?in rdfs:label ?informant .}}
    }}
"""
    res = POEM.query(query)
    for row in res:
        return {"language": row.language, "informant": row.informant}
    return None
def get_components(POEM: Dataset, name: str):
    name = Literal(name).n3()
    query = f"""PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX sio: <http://semanticscience.org/resource/>
SELECT DISTINCT ?component
WHERE {{
    GRAPH <{instruments}> {{?su rdfs:label {name}}}
    GRAPH <{instruments}> {{ ?su sio:SIO_000059 ?c.}}
    GRAPH <{components}> {{?c rdfs:label ?component .}}
    }} """
    return list([row.component for row in POEM.query(query)])
def get_instruments_by_scales(POEM: Dataset, scales_list: list[str]):
    scale_names = [Literal(name).n3() for name in scales_list]
    res = {}
    for scale in scale_names:
        query = f"""PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                PREFIX sio: <http://semanticscience.org/resource/>
    SELECT DISTINCT ?label
    WHERE {{
        GRAPH <{scales}> {{?s rdfs:label {scale} .}}
        GRAPH <{scale_instruments}> {{ ?o ?p ?s .}}
        GRAPH <{instrumentsCollections}> {{?su sio:SIO_000059 ?o .}}
        GRAPH <{instrumentsCollections}> {{?su rdfs:label ?label .}}
     
    }}"""
        labels = list([str(row.label) for row in POEM.query(query)])
        for label in labels:
            if label not in res:
                res[label] = set()
            res[label].add(str(scale).strip("\""))
    return {label: list(scales) for label, scales in res.items()}

def ai_summary(text: str) -> str:
    response = requests.post(
  url="https://openrouter.ai/api/v1/chat/completions",
  headers={
    "Authorization": "Bearer " + OPEN_ROUTER_API_KEY,
    "Content-Type": "application/json",
  },
  data=json.dumps({
    "model": "stepfun/step-3.5-flash:free",
    "messages": [
        {
          "role": "user",
          "content": f"""You are analyzing content from the Psychometric Ontology of Experiences and Measures (POEM).

POEM models:
- assessment instruments
- constructs (latent variables)
- relationships between instruments, respondents, and measures

Your task:
- Summarize the page
- Identify any instruments or constructs mentioned
- Explain relationships if present
- Keep it clear and structured

Page Content:
{text}"""
        }
      ],
    "reasoning": {"enabled": True}
  })
)
# Extract the assistant message with reasoning_details
    response = response.json()
    print(response)
    if 'choices' not in response:
        return "Summary not available right now try again later"
    response = response['choices'][0]['message']
    return response.get('content')
def extract_text(html: str) -> str:
    soup = BeautifulSoup(html, "html.parser")
    for tag in soup(["script", "style", "nav", "footer"]):
        tag.decompose()
    text: str = soup.get_text(separator=" ", strip=True)
    return text[:8000] 
async def fetch_html(url: str) -> str:
    async with httpx.AsyncClient() as client:
        response = await client.get(url)
        return response.text
    
def get_all_instruments(POEM: Dataset):
    query = f"""PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX sio: <http://semanticscience.org/resource/>
SELECT DISTINCT ?label
WHERE {{    
    GRAPH <{instruments}> {{?i rdfs:label ?label}}
    }}
"""
    res = POEM.query(query)
    labels = set()
    for row in res:
        labels.add(str(row.label))
    return labels
def search_query(query: str,buckets):
    return search(query,buckets)