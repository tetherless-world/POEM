from rdflib import Dataset, URIRef, Literal

instrumentsCollections: str = "urn:poem:file:instrumentCollections.ttl"
instruments: str = "urn:poem:file:instruments.ttl"
languages: str = "urn:poem:file:languages.ttl"
items: str = "urn:poem:file:items.ttl"
itemStems:  str = "urn:poem:file:itemStems.ttl"
scale_instruments: str = "urn:poem:file:scalesInstrument.ttl"
scales: str = "urn:poem:file:scales.ttl"
def getTotalInstruments(POEM: Dataset, name: str):
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
def getTotalLanguages(POEM: Dataset, name: str):
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
def getInstrumentItemConcepts(POEM: Dataset, name: str):
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

def getScales(POEM: Dataset, name: str):
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