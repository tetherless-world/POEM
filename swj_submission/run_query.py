from rdflib import Graph

def query2():
    query = """
    SELECT ?scale ?disorder ?label WHERE {
        poem-rcads:RCADS47Questionnaire poem:hasScale ?scale .
        ?disorder rdf:type poem:Disorder .
        ?disorder rdfs:label ?label .
        ?scale rdf:type poem:QuestionnaireScale .
        ?scale poem:isAboutDisorder ?disorder .
    }
    """

def main():
    g = Graph()
    g.parse("./activities.ttl")
    g.parse("./codebooks.ttl")
    g.parse("./experiences.ttl")
    g.parse("./informants.ttl")
    g.parse("./instruments.ttl")
    g.parse("./items.ttl")
    g.parse("./itemStemConcepts.ttl")
    g.parse("./itemStems.ttl")
    g.parse("./responseOptions.ttl")
    g.parse("./scaleItemConceptMap.ttl")
    g.parse("./scales.ttl")

    query1_nope = """
    SELECT ?person ?attribute? ?attributevalue WHERE {
        {
            ?instrument fhir:code "RCADS-47-Y-EN" .
            ?instrument sio:hasAttribute ?person .
        } FILTER NOT EXISTS { ?person sio:hasAttribute ?attribute . }
    }
    """

    query2 = """
    SELECT ?informantlabel WHERE {
        ?informant rdf:type vstoi:Informant .
        ?informant rdfs:label ?informantlabel .
        ?questionnaire fhir:code "RCADS-47-Y-EN" .
        ?questionnaire sio:hasAttribute ?informant .
    }
    """

    qres = g.query(query2)
    for row in qres:
        print(f"{row.informantlabel}")

if __name__ == "__main__":
    main()