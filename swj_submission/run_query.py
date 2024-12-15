from rdflib import Graph
import os


# What conditions does the RCADS-47 measure?
def query1(g):
    query_ = """
    SELECT ?scale ?conditionLabel WHERE {
        ?condition a poem:Disorder .
        ?condition rdfs:label ?conditionLabel .
        ?questionnaire fhir:code "RCADS-47-Y-EN" .
        ?questionnaire sio:hasMember ?scale .
        ?scale sio:isAbout ?condition .
    }
    """

    query = """
    SELECT ?scale WHERE {
        <http://purl.org/twc/poem/individual/instrument/1> sio:hasMember ?scale .
    }
    """

    qres = g.query(query)
    for row in qres:
        print(f"{row.scale}")


def query4(g):
    query = """
    SELECT ?informantlabel WHERE {
        ?informant rdf:type vstoi:Informant .
        ?informant rdfs:label ?informantlabel .
        ?questionnaire fhir:code "RCADS-47-Y-EN" .
        ?questionnaire sio:hasAttribute ?informant .
    }
    """
    qres = g.query(query)
    for row in qres:
        print(f"{row.informantlabel}")


def main():
    g = Graph()

    files = os.listdir('./')
    for file in files:
        if file.endswith('.ttl') and file != 'individuals_full.ttl':
            print("parsing {}".format(file))
            g.parse(file)
            print("parsed {}".format(file))


    query1_nope = """
    SELECT ?person ?attribute? ?attributevalue WHERE {
        {
            ?instrument fhir:code "RCADS-47-Y-EN" .
            ?instrument sio:hasAttribute ?person .
        } FILTER NOT EXISTS { ?person sio:hasAttribute ?attribute . }
    }
    """

    query1(g)


if __name__ == "__main__":
    main()