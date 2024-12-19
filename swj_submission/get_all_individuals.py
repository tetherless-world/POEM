from rdflib import Graph, Literal, Namespace, BNode, URIRef
from rdflib.namespace import RDF, XSD
import os

def main():
    g = Graph()

    files = os.listdir('./individuals/')
    for file in files:
        if file.endswith('.ttl') and file != 'individuals_full.ttl':
            print("parsing {}".format(file))
            g.parse(f'individuals/{file}')
            print("parsed {}".format(file))

    test = BNode()
    ont = BNode()
    OWL = Namespace("http://www.w3.org/2002/07/owl#")
    uri = URIRef("http://purl.org/twc/poem")
    g.bind("owl", OWL)
    g.add((test, RDF.type, ont))
    g.add((ont, RDF.type, OWL.Ontology))
    g.add((ont, OWL.imports, uri))

    g.serialize(destination='./individuals_full.ttl')

if __name__ == "__main__":
    main()