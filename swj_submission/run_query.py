from rdflib import Graph
import os

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