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

# not working
# CQ4: What languages are available for the RCADS-47?
def query4(g):
    query = """
    SELECT DISTINCT ?language WHERE {
        <http://purl.org/twc/poem/individual/instrumentFamily/1> sio:hasMember ?instrument .
        ?instrument sio:hasMember ?item .
        ?item sio:hasSource ?itemStem .
        ?itemStem dc:language ?language .
    }
    """
    qres = g.query(query)
    for row in qres:
        print(f"{row.language}")


# CQ9: Who can fill out the RCADS on behalf of a subject?
def query9(g):
    query = """
    SELECT DISTINCT ?informantLabel WHERE {
        <http://purl.org/twc/poem/individual/instrumentFamily/1> sio:hasMember ?questionnaire .
        ?questionnaire sio:hasAttribute ?informant .
        ?informant rdfs:label ?informantLabel .
    }
    """

    qres = g.query(query)
    for row in qres:
        print(f"{row.informantLabel}")


def main():
    g = Graph()

    DC = Namespace("http://purl.org/dc/terms/")
    g.bind("dc", DC, replace=True)

    #SIO = Namespace("http://semanticscience.org/ontology/sio.owl")
    #g.bind("sio", SIO, replace=True)

    files = os.listdir('./individuals')#('./swj_submission/individuals')
    for file in files:
        if file.endswith('.ttl'):
            #print("parsing {}".format(file))
            g.parse(f"./individuals/{file}")
            #print("parsed {}".format(file))

    #for namespace in g.namespaces():
    #    print(namespace)
    g.serialize(destination='./individuals_full2.ttl')

    query4(g)


if __name__ == "__main__":
    main()