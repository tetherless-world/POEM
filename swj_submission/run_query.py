from rdflib import Graph, Namespace
import os


# CQ1: What conditions does the RCADS-47 measure?
def query1(g):
    query = """
    SELECT ?scale ?conditionLabel WHERE {
        ?condition a poem:Disorder .
        ?condition rdfs:label ?conditionLabel .
        ?questionnaire fhir:code "RCADS-47-Y-EN" .
        ?questionnaire sio:hasMember ?scale .
        ?scale sio:isAbout ?condition .
    }
    """

    qres = g.query(query)
    for row in qres:
        print(f"{row.scale}, {row.conditionLabel}")


# CQ3: What scales does the RCADS-47 have?
def query3(g):
    query = """
    SELECT ?scale ?scaleLabel WHERE {
        ?questionnaire fhir:code "RCADS-47-Y-EN" .
        ?questionnaire sio:hasMember ?scale .
        ?scale rdfs:label ?scaleLabel .
    }
    """

    qres = g.query(query)
    for row in qres:
        print(f"{row.scale}, {row.scaleLabel}")


# CQ4: What languages are available for the RCADS-47?
def query4(g):
    query_ = """
    SELECT ?item ?source ?language WHERE {
        ?questionnaire fhir:code "RCADS-47-Y-EN" .
        ?questionnaire sio:hasMember ?item .
        ?item sio:hasSource ?source .
        ?source dc:language ?language .
    }
    """
    '''qres = g.query(query)
    for row in qres:
        print(f"{row.item}, {row.source}, {row.language}")'''
    
    query = """
    SELECT ?lang WHERE {
    <http://purl.org/twc/poem/individual/itemStem/1>
    }
    """


def main():
    g = Graph()

    #DC = Namespace("http://purl.org/dc/terms/")
    #g.bind("dc", DC)

    files = os.listdir('./individuals')#('./swj_submission/individuals')
    for file in files:
        print(file)
        if file.endswith('.ttl'):
            #print("parsing {}".format(file))
            g.parse(f"./individuals/{file}")
            #print("parsed {}".format(file))

    for namespace in g.namespaces():
        print(namespace)

    query1_nope = """
    SELECT ?person ?attribute? ?attributevalue WHERE {
        {
            ?instrument fhir:code "RCADS-47-Y-EN" .
            ?instrument sio:hasAttribute ?person .
        } FILTER NOT EXISTS { ?person sio:hasAttribute ?attribute . }
    }
    """

    query4(g)


if __name__ == "__main__":
    main()